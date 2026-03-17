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
            if (!builder.isEmpty()) {
                char last = builder.charAt(builder.length() - 1);
                if (last == '\n') {
                    return builder.substring(0, builder.length()).trim();
                }
            }
        }
    }

    public byte[] readBasicBody() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int code;
        while ((code = inputStream.read()) != -1) {
            out.write(code);
        }
        return out.toByteArray();
    }

    public byte[] readContentLengthBody(int length) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(length);
        int count = 0;
        byte[] buffer = new byte[4096];
        while (count < length) {
            int amount = Math.min(length - count, buffer.length);
            int read = inputStream.read(buffer, 0, amount);
            if (read == -1) {
                throw new IOException(
                        "Unexpected end of stream: expected " + length + " bytes, received " + count
                );
            }
            out.write(buffer, 0, read);
            count += read;
        }
        return out.toByteArray();
    }

    public byte[] readChunkedBody() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            String chunkSizeLine = readContentLine();
            if (chunkSizeLine == null) {
                throw new IOException("Unexpected end of stream in chunked body");
            }

            int semicolon = chunkSizeLine.indexOf(';');
            String hexSize = (semicolon >= 0
                    ? chunkSizeLine.substring(0, semicolon)
                    : chunkSizeLine).trim();

            int chunkSize;
            try {
                chunkSize = Integer.parseInt(hexSize, 16);
            } catch (NumberFormatException e) {
                throw new IOException(
                        "Invalid chunk size line: '" + chunkSizeLine + "'", e
                );
            }

            if (chunkSize == 0) {
                readContentLine();
                break;
            }

            byte[] chunk = new byte[chunkSize];
            int bytesRead = 0;
            while (bytesRead < chunkSize) {
                int read = inputStream.read(chunk, bytesRead, chunkSize - bytesRead);
                if (read == -1) {
                    throw new IOException(
                            "Unexpected end of stream inside chunk " +
                                    "(expected " + chunkSize + " bytes, got " + bytesRead + ")"
                    );
                }
                bytesRead += read;
            }
            out.write(chunk, 0, chunkSize);

            readContentLine();
        }
        return out.toByteArray();
    }

    @Override
    public void close() throws IOException {
        this.inputStream.close();
    }
}