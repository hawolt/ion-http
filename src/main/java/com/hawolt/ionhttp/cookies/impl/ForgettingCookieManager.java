package com.hawolt.ionhttp.cookies.impl;

import com.hawolt.ionhttp.cookies.Cookie;
import com.hawolt.ionhttp.cookies.CookieManager;

public class ForgettingCookieManager implements CookieManager {
    @Override
    public String getCookie(String hostname) {
        return null;
    }

    @Override
    public void add(Cookie... cookies) {

    }

    @Override
    public boolean has(String cookie) {
        return false;
    }

    @Override
    public Cookie[] getAllCookies() {
        return new Cookie[0];
    }
}
