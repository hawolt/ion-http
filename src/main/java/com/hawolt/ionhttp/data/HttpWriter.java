package com.hawolt.ionhttp.data;

import com.hawolt.logger.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class HttpWriter extends ByteWriter implements ByteSink {

    @Override
    public void write(Object o) {
        super.write(o);
        super.write("\r\n");
    }

    @Override
    public void drainTo(OutputStream stream) throws IOException {
        byte[] b = check();
        Logger.debug("- {}\n{}", System.currentTimeMillis(), new String(b, StandardCharsets.UTF_8));
        stream.write(b);
        stream.flush();
    }

    @Override
    public byte[] check() {
        return b;
    }
}
