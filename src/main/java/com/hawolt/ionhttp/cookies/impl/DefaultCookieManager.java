package com.hawolt.ionhttp.cookies.impl;

import com.hawolt.ionhttp.cookies.Cookie;
import com.hawolt.ionhttp.cookies.CookieManager;

public class DefaultCookieManager implements CookieManager {

    private DefaultCookieManager() {
        throw new RuntimeException("not implemented");
    }

    @Override
    public String getCookie(String hostname) {
        return null;
    }

    @Override
    public void add(Cookie... cookies) {

    }

    @Override
    public Cookie[] getAllCookies() {
        return new Cookie[0];
    }
}
