package com.hawolt.ionhttp.data;

import java.io.IOException;
import java.io.OutputStream;

public class HttpWriter extends ByteWriter implements ByteSink {

    @Override
    public void write(Object o) {
        super.write(o);
        super.write("\r\n");
    }

    @Override
    public void drainTo(OutputStream stream) throws IOException {
        byte[] b = check();
        stream.write(b);
        stream.flush();
    }

    @Override
    public byte[] check() {
        return b;
    }
}
