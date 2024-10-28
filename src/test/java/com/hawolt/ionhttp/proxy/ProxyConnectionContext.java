package com.hawolt.ionhttp.proxy;

public class ProxyConnectionContext {
    private final ProxyAuthentication authentication;

    public ProxyConnectionContext() {
        this(null);
    }

    public ProxyConnectionContext(ProxyAuthentication authentication) {
        this.authentication = authentication;
    }

    public boolean isAuthenticationRequired() {
        return authentication != null;
    }

    public ProxyAuthentication getAuthentication() {
        return authentication;
    }
}
