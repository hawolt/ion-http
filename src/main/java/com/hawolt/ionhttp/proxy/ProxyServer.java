package com.hawolt.ionhttp.proxy;

public class ProxyServer {
    private final ProxyAuthenticator proxyAuthenticator;
    private final String host;
    private final int port;

    private ProxyServer(String host, int port) {
        this(host, port, null);
    }

    private ProxyServer(String host, int port, ProxyAuthenticator proxyAuthenticator) {
        this.proxyAuthenticator = proxyAuthenticator;
        this.port = port;
        this.host = host;
    }

    public ProxyAuthenticator getProxyAuthenticator() {
        return proxyAuthenticator;
    }

    public String getHost() {
        return host;
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

    @Override
    public String toString() {
        return "ProxyServer{" +
                "proxyAuthenticator=" + proxyAuthenticator +
                ", host='" + host + '\'' +
                ", port=" + port +
                '}';
    }
}
