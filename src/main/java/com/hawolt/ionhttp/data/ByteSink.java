package com.hawolt.ionhttp.data;

import java.io.IOException;
import java.io.OutputStream;

public interface ByteSink {
    void drainTo(OutputStream stream) throws IOException;

    byte[] check();
}
