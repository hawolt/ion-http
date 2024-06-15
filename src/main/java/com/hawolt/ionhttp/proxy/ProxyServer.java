package com.hawolt.ionhttp.proxy;

public class ProxyServer {
    private final ProxyAuthenticator proxyAuthenticator;
    private final String ip;
    private final int port;

    private ProxyServer(String ip, int port) {
        this(ip, port, null);
    }

    private ProxyServer(String ip, int port, ProxyAuthenticator proxyAuthenticator) {
        this.proxyAuthenticator = proxyAuthenticator;
        this.port = port;
        this.ip = ip;
    }

    public ProxyAuthenticator getProxyAuthenticator() {
        return proxyAuthenticator;
    }

    public String getIP() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public static ProxyServer create(String ip, int port) {
        return new ProxyServer(ip, port);
    }

    public static ProxyServer create(String ip, int port, String username, String password) {
        return create(ip, port, ProxyAuthenticator.with(username, password));
    }

    public static ProxyServer create(String ip, int port, ProxyAuthenticator proxyAuthenticator) {
        return new ProxyServer(ip, port, proxyAuthenticator);
    }
}
