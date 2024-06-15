package com.hawolt.ionhttp.misc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class SocketWriter extends Writer {
    private final OutputStream outputStream;

    public SocketWriter(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public void write(String line) throws IOException {
        String actual = line + "\r\n";
        write(actual.toCharArray(), 0, actual.length());
    }

    public void write(byte[] b) throws IOException {
        this.outputStream.write(b, 0, b.length);
    }

    @Override
    public void write(char[] buffer, int off, int len) throws IOException {
        String reference = new String(buffer, off, len);
        byte[] b = reference.getBytes(StandardCharsets.UTF_8);
        this.outputStream.write(b);
    }

    @Override
    public void flush() throws IOException {
        this.outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        this.outputStream.close();
    }
}
