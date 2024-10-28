package com.hawolt.ionhttp.misc;

import com.hawolt.ionhttp.exceptions.ConnectionTerminatedException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class SocketReader implements AutoCloseable {
    private final InputStream inputStream;

    public SocketReader(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public String readContentLine() throws IOException {
        StringBuilder builder = new StringBuilder();
        while (true) {
            int code = inputStream.read();
            if (code == -1) {
                if (builder.isEmpty()) {
                    throw new ConnectionTerminatedException();
                } else {
                    String line = builder.toString();
                    builder.setLength(0);
                    return line;
                }
            }
            builder.append((char) code);
            if (builder.isEmpty()) continue;
            char eof = builder.charAt(builder.length() - 1);
            if (eof == '\n') {
                String line = builder.substring(0, builder.length()).trim();
                builder.setLength(0);
                return line;
            }
        }
    }

    public byte[] readBasicBody() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int code;
        while ((code = inputStream.read()) != -1) {
            byteArrayOutputStream.write(code);
        }
        return byteArrayOutputStream.toByteArray();
    }

    public byte[] readContentLengthBody(int length) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        int count = 0;
        byte[] buffer = new byte[1024];
        while (count < length) {
            int amount = Math.min(length - count, buffer.length);
            int read = inputStream.read(buffer, 0, amount);
            if (read == -1) {
                throw new IOException("Unexpected end of stream");
            }
            stream.write(buffer, 0, read);
            count += read;
        }
        return stream.toByteArray();
    }

    public byte[] readChunkedBody() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        while (true) {
            String chunkSizeLine = readContentLine();
            if (chunkSizeLine == null) {
                throw new IOException("Unexpected end of stream");
            }
            int chunkSize = Integer.parseInt(chunkSizeLine, 16);
            if (chunkSize == 0) {
                readContentLine(); // read the trailing \r\n after the 0-sized chunk
                break;
            }
            byte[] chunk = new byte[chunkSize];
            int bytesRead = 0;
            while (bytesRead < chunkSize) {
                int read = inputStream.read(chunk, bytesRead, chunkSize - bytesRead);
                if (read == -1) {
                    throw new IOException("Unexpected end of stream");
                }
                bytesRead += read;
            }
            byteArrayOutputStream.write(chunk, 0, chunkSize);
            readContentLine(); // read the trailing \r\n after each chunk
        }
        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public void close() throws IOException {
        this.inputStream.close();
    }
}
