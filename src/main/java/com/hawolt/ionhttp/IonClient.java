package com.hawolt.ionhttp;

import com.hawolt.ionhttp.certificates.AllTrustManager;
import com.hawolt.ionhttp.certificates.BasicTrustManager;
import com.hawolt.ionhttp.cookies.CookieManager;
import com.hawolt.ionhttp.cookies.impl.ForgettingCookieManager;
import com.hawolt.ionhttp.misc.TLS;
import com.hawolt.ionhttp.proxy.ProxyServer;
import com.hawolt.ionhttp.request.IonReadState;
import com.hawolt.ionhttp.request.IonRequest;
import com.hawolt.ionhttp.request.IonResponse;
import com.hawolt.ionhttp.request.tunnel.ProxyConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class IonClient {

    private static final Logger logger = LoggerFactory.getLogger(IonClient.class);

    public static boolean debug = false;

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
        Socket socket = new Socket(proxy.getHost(), proxy.getPort());
        try {
            ProxyConnector connector = ProxyConnector.build(proxy, request);
            connector.drainTo(socket.getOutputStream());

            IonResponse connectResponse = IonResponse.create(
                    buildStatusOnlyRequest(request), socket, manager
            );
            int code = connectResponse.code();
            if (code < 200 || code >= 300) {
                socket.close();
                throw new IOException(
                        "Proxy CONNECT rejected with status " + code
                );
            }
        } catch (IOException e) {
            socket.close();
            throw e;
        }
        return socket;
    }

    private Socket upgrade(IonRequest.Builder builder, boolean isProxyRequest, Socket socket) throws IOException {
        if (!isProxyRequest || !"https".equals(builder.protocol)) return socket;
        SSLSocket ssl = (SSLSocket) factory.createSocket(
                socket, builder.hostname, builder.port, true
        );
        ssl.setEnabledProtocols(new String[]{tls.getValue()});
        ssl.setEnabledCipherSuites(suites);
        ssl.startHandshake();
        return ssl;
    }

    private Socket openConnection(IonRequest request) throws IOException {
        ProxyServer effective = bind(request);
        if (effective == null) {
            IonRequest.Builder b = request.builder();
            return create(b.protocol, b.hostname, b.port);
        }
        return tunnel(request, effective);
    }

    public IonResponse execute(IonRequest request) throws IOException {
        IonRequest.Builder builder = request.builder();
        ProxyServer effective = bind(request);
        boolean isProxyRequest = effective != null;

        Socket socket = openConnection(request);
        socket = upgrade(builder, isProxyRequest, socket);

        if (debug) logger.debug("- {}\n{}", System.currentTimeMillis(), request);

        request.drainTo(socket.getOutputStream());
        IonResponse response = IonResponse.create(request, socket, manager);

        if (debug) logger.debug("- {}\n{}", System.currentTimeMillis(), response);

        return response;
    }

    public CookieManager getCookieManager() {
        return manager;
    }

    private ProxyServer bind(IonRequest request) {
        ProxyServer overwrite = request.builder().proxy;
        return overwrite != null ? overwrite : this.proxy;
    }

    private static IonRequest buildStatusOnlyRequest(IonRequest original) {
        IonRequest.Builder b = original.builder();
        return new IonRequest.AdvancedBuilder()
                .state(IonReadState.STATUS)
                .protocol(b.protocol)
                .hostname(b.hostname)
                .port(b.port)
                .method("CONNECT")
                .path(b.hostname + ":" + b.port)
                .build();
    }
}