package com.hawolt.ionhttp.data;

import java.nio.charset.StandardCharsets;

public abstract class ByteWriter {
    protected byte[] b = new byte[0];

    public void write(Object o) {
        byte[] content = o instanceof byte[] ?
                (byte[]) o : o.toString().getBytes(StandardCharsets.UTF_8);
        byte[] tmp = new byte[b.length + content.length];
        System.arraycopy(b, 0, tmp, 0, b.length);
        System.arraycopy(content, 0, tmp, b.length, content.length);
        b = tmp;
    }
}
