package com.hawolt.ionhttp.proxy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record ProxyAuthentication(String username, String password) {
    boolean verify(String[] credentials) {
        if (credentials.length != 2) return false;
        String username = credentials[0];
        String password = credentials[1];
        byte[] provided = Base64.getEncoder().encode(
                String.join(
                        ":",
                        username,
                        password
                ).getBytes(StandardCharsets.UTF_8)
        );
        byte[] expected = Base64.getEncoder().encode(
                String.join(
                        ":",
                        username(),
                        password()
                ).getBytes(StandardCharsets.UTF_8)
        );
        if (provided.length != expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != provided[i]) return false;
        }
        return true;
    }
}