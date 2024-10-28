package com.hawolt.ionhttp.misc;

public class TLS {
    public static final TLS TLSv1_3 = new TLS("TLSv1.3");
    public static final TLS TLSv1_2 = new TLS("TLSv1.2");
    public static final TLS TLSv1_1 = new TLS("TLSv1.1");
    public static final TLS TLSv1 = new TLS("TLSv1");
    private final String value;

    private TLS(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
