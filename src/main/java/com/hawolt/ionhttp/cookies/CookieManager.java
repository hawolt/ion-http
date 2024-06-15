package com.hawolt.ionhttp.cookies;

public interface CookieManager {
    String getCookie(String hostname);

    void add(Cookie... cookies);

    Cookie[] getAllCookies();
}
