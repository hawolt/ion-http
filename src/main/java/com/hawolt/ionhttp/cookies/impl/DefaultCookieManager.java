package com.hawolt.ionhttp.cookies.impl;

import com.hawolt.ionhttp.cookies.Cookie;
import com.hawolt.ionhttp.cookies.CookieManager;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultCookieManager implements CookieManager {
    private final Map<String, Cookie> map = new HashMap<>();
    private final Object lock = new Object();

    @Override
    public String getCookie(String hostname) {
        synchronized (lock) {
            return map.values().stream()
                    .filter(cookie -> cookie.isValidFor(hostname))
                    .filter(Cookie::isNotExpired)
                    .filter(Cookie::hasValue)
                    .map(Cookie::get)
                    .collect(Collectors.joining("; "));
        }
    }

    @Override
    public void add(Cookie... cookies) {
        synchronized (lock) {
            for (Cookie cookie : cookies) {
                map.put(cookie.getName(), cookie);
            }
        }
    }

    @Override
    public Cookie[] getAllCookies() {
        synchronized (lock) {
            return map.values().toArray(new Cookie[0]);
        }
    }

    @Override
    public boolean has(String cookie) {
        synchronized (lock) {
            return map.values().stream()
                    .filter(Cookie::hasValue)
                    .filter(Cookie::isNotExpired)
                    .anyMatch(o -> o.getName().equalsIgnoreCase(cookie));
        }
    }
}
