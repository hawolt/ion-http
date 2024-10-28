package com.hawolt.ionhttp.request.tunnel;

import com.hawolt.ionhttp.data.HttpWriter;
import com.hawolt.ionhttp.proxy.ProxyAuthenticator;
import com.hawolt.ionhttp.proxy.ProxyServer;
import com.hawolt.ionhttp.request.IonRequest;

public class ProxyConnector extends HttpWriter {
    private ProxyConnector(ProxyServer proxy, IonRequest request) {
        IonRequest.Builder builder = request.builder();
        ProxyAuthenticator authenticator = proxy.getProxyAuthenticator();
        write("CONNECT " + String.join(":", builder.hostname, String.valueOf(builder.port)) + " HTTP/1.1");
        write("Host: " + builder.hostname);
        if (authenticator != null) {
            write("Proxy-Authorization: Basic " + authenticator.asBasic());
        }
        write("");
    }

    public static ProxyConnector build(ProxyServer proxy, IonRequest request) {
        return new ProxyConnector(proxy, request);
    }
}
