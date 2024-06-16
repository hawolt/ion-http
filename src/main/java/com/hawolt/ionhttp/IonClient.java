package com.hawolt.ionhttp;

import com.hawolt.ionhttp.certificates.AllTrustManager;
import com.hawolt.ionhttp.certificates.BasicTrustManager;
import com.hawolt.ionhttp.cookies.CookieManager;
import com.hawolt.ionhttp.cookies.impl.ForgettingCookieManager;
import com.hawolt.ionhttp.misc.SocketWriter;
import com.hawolt.ionhttp.misc.TLS;
import com.hawolt.ionhttp.proxy.ProxyAuthenticator;
import com.hawolt.ionhttp.proxy.ProxyServer;
import com.hawolt.ionhttp.request.IonRequest;
import com.hawolt.ionhttp.request.IonResponse;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;

public class IonClient {
    private final static SSLSocketFactory DEFAULT_SOCKET_FACTORY = (SSLSocketFactory) SSLSocketFactory.getDefault();

    public static String[] getAvailableCipherSuites() {
        return DEFAULT_SOCKET_FACTORY.getSupportedCipherSuites();
    }

    private final SSLSocketFactory factory;
    private final CookieManager manager;
    private final ProxyServer proxy;
    private final String[] suites;
    private final TLS tls;

    private IonClient(Builder builder) {
        this.factory = construct(builder);
        this.proxy = builder.proxyServer;
        this.manager = builder.manager;
        this.suites = builder.suites;
        this.tls = builder.tls;
    }

    private SSLSocketFactory construct(Builder builder) {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{builder.trustManager}, new SecureRandom());
            return context.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    public static IonClient getDefault() {
        return new IonClient.Builder()
                .setAllowedCipherSuites(getAvailableCipherSuites())
                .setVersionTLS(TLS.TLSv1_2)
                .setGracefulTrustManager()
                .build();
    }

    public static class Builder {
        private X509TrustManager trustManager = new BasicTrustManager();
        private CookieManager manager = new ForgettingCookieManager();
        private ProxyServer proxyServer;
        private String[] suites;
        private TLS tls;

        public Builder setGracefulTrustManager() {
            this.trustManager = new AllTrustManager();
            return this;
        }

        public Builder setTrustManager(X509TrustManager trustManager) {
            this.trustManager = trustManager;
            return this;
        }

        public Builder setVersionTLS(TLS tls) {
            this.tls = tls;
            return this;
        }

        public Builder setAllowedCipherSuites(String... suites) {
            this.suites = suites;
            return this;
        }

        public Builder setCookieManager(CookieManager cookieManager) {
            this.manager = cookieManager;
            return this;
        }

        public Builder setProxyServer(ProxyServer proxyServer) {
            this.proxyServer = proxyServer;
            return this;
        }

        public IonClient build() {
            return new IonClient(this);
        }
    }


    private Socket create(String protocol, String ip, int port) throws IOException {
        if ("https".equals(protocol)) {
            SSLSocket socket = (SSLSocket) factory.createSocket(ip, port);
            socket.setEnabledProtocols(new String[]{tls.getValue()});
            socket.setEnabledCipherSuites(suites);
            socket.startHandshake();
            return socket;
        } else {
            return new Socket(ip, port);
        }
    }

    private Socket tunnel(IonRequest request) throws IOException {
        IonRequest.Builder builder = request.getBuilder();
        Socket socket = null;
        try {
            ProxyAuthenticator authenticator = proxy.getProxyAuthenticator();
            socket = create("http", proxy.getIP(), proxy.getPort());
            SocketWriter writer = new SocketWriter(socket.getOutputStream());
            writer.write("CONNECT " + String.join(":", builder.hostname, String.valueOf(builder.port)) + " HTTP/1.1");
            writer.write("Host: " + builder.hostname);
            if (authenticator != null) {
                writer.write("Proxy-Authorization: Basic " + authenticator.asBasic());
            }
            writer.write("");
            writer.flush();
        } catch (IOException e) {
            if (socket != null) {
                socket.close();
            }
            throw e;
        }
        return socket;
    }

    private Socket plain(IonRequest request) throws IOException {
        IonRequest.Builder builder = request.getBuilder();
        InetAddress address = InetAddress.getByName(builder.hostname);
        String ip = address.getHostAddress();
        return create(builder.protocol, ip, builder.port);
    }

    private Socket openConnection(IonRequest request) throws IOException {
        return proxy == null ? plain(request) : tunnel(request);
    }

    private Socket upgrade(IonRequest.Builder builder, boolean isProxyRequest, Socket socket) throws IOException {
        if (!isProxyRequest) return socket;
        if (!"https".equals(builder.protocol)) return socket;
        return factory.createSocket(socket, builder.hostname, builder.port, true);
    }

    public IonResponse execute(IonRequest request) throws IOException {
        IonRequest.Builder builder = request.getBuilder();
        Socket socket = openConnection(request);
        boolean isProxyRequest = proxy != null;
        IonResponse proxied = isProxyRequest ?
                IonResponse.create(request, socket, manager) :
                null;
        socket = upgrade(builder, isProxyRequest, socket);
        SocketWriter writer = new SocketWriter(socket.getOutputStream());
        writer.write(String.join(" ", builder.method, builder.path, "HTTP/1.1"));
        for (Map.Entry<String, String> entry : request.getBuilder().headers.entrySet()) {
            writer.write(String.join(": ", entry.getKey(), entry.getValue()));
        }
        writer.write("");
        writer.flush();
        byte[] payload = builder.payload;
        if (payload != null && payload.length > 0) {
            writer.write(payload);
            writer.flush();
        }
        IonResponse response = IonResponse.create(request, socket, manager);
        response.setPredecessor(proxied);
        return response;
    }

    public CookieManager getCookieManager() {
        return manager;
    }
}
