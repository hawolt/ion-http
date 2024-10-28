package com.hawolt.ionhttp;

import com.hawolt.ionhttp.certificates.AllTrustManager;
import com.hawolt.ionhttp.certificates.BasicTrustManager;
import com.hawolt.ionhttp.cookies.CookieManager;
import com.hawolt.ionhttp.cookies.impl.ForgettingCookieManager;
import com.hawolt.ionhttp.misc.TLS;
import com.hawolt.ionhttp.proxy.ProxyServer;
import com.hawolt.ionhttp.request.IonRequest;
import com.hawolt.ionhttp.request.IonResponse;
import com.hawolt.ionhttp.request.tunnel.ProxyConnector;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

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


    private Socket create(String protocol, String host, int port) throws IOException {
        if ("https".equals(protocol)) {
            SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
            socket.setEnabledProtocols(new String[]{tls.getValue()});
            socket.setEnabledCipherSuites(suites);
            socket.startHandshake();
            return socket;
        } else {
            return new Socket(host, port);
        }
    }

    private Socket tunnel(IonRequest request, ProxyServer proxy) throws IOException {
        Socket socket = null;
        try {
            ProxyConnector connector = ProxyConnector.build(proxy, request);
            socket = create("http", proxy.getHost(), proxy.getPort());
            connector.drainTo(socket.getOutputStream());
        } catch (IOException e) {
            if (socket != null) {
                socket.close();
            }
            throw e;
        }
        return socket;
    }

    private Socket plain(IonRequest request) throws IOException {
        IonRequest.Builder builder = request.builder();
        return create(builder.protocol, builder.hostname, builder.port);
    }

    private Socket openConnection(IonRequest request) throws IOException {
        ProxyServer overwrite = request.builder().proxy;
        ProxyServer proxy = overwrite != null ? overwrite : this.proxy;
        return proxy == null ? plain(request) : tunnel(request, proxy);
    }

    private Socket upgrade(IonRequest.Builder builder, boolean isProxyRequest, Socket socket) throws IOException {
        if (!isProxyRequest) return socket;
        if (!"https".equals(builder.protocol)) return socket;
        return factory.createSocket(socket, builder.hostname, builder.port, true);
    }

    public IonResponse execute(IonRequest request) throws IOException {
        IonRequest.Builder builder = request.builder();
        Socket socket = openConnection(request);
        ProxyServer overwrite = request.builder().proxy;
        ProxyServer proxy = overwrite != null ? overwrite : this.proxy;
        boolean isProxyRequest = proxy != null;
        IonResponse proxied = isProxyRequest ?
                IonResponse.create(request, socket, manager) :
                null;
        socket = upgrade(builder, isProxyRequest, socket);
        request.drainTo(socket.getOutputStream());
        IonResponse response = IonResponse.create(request, socket, manager);
        response.setPredecessor(proxied);
        return response;
    }

    public CookieManager getCookieManager() {
        return manager;
    }
}
