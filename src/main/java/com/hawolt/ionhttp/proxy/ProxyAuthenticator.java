package com.hawolt.ionhttp.proxy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ProxyAuthenticator {
    private final String username, password;

    private ProxyAuthenticator(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public static ProxyAuthenticator with(String username, String password) {
        return new ProxyAuthenticator(username, password);
    }

    public String asBasic() {
        return Base64.getEncoder().encodeToString(
                String.join(":", username, password).getBytes(StandardCharsets.UTF_8)
        );
    }

    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        return "ProxyAuthenticator{" +
                "username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", base64='" + asBasic() + '\'' +
                '}';
    }
}
