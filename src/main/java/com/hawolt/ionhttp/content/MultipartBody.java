package com.hawolt.ionhttp.content;

import com.hawolt.ionhttp.data.ByteSink;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MultipartBody implements ByteSink {
    private final String boundary;
    private final byte[] b;

    public MultipartBody(String boundary, byte[] b) {
        this.boundary = boundary;
        this.b = b;
    }

    public String getBoundary() {
        return boundary;
    }

    @Override
    public void drainTo(OutputStream stream) throws IOException {
        stream.write(b);
    }

    @Override
    public byte[] check() {
        return b;
    }

    public static class Builder {
        private static final String CRLF = "\r\n";
        private final List<byte[]> list;
        private final String boundary;

        public Builder() {
            this.boundary = generateBoundary();
            this.list = new ArrayList<>();
        }

        public Builder addFormField(String name, String value) {
            StringBuilder builder = new StringBuilder();
            builder.append("--").append(boundary).append(CRLF)
                    .append("Content-Disposition: form-data; name=\"").append(name).append("\"").append(CRLF)
                    .append(CRLF)
                    .append(value).append(CRLF);
            list.add(builder.toString().getBytes(StandardCharsets.UTF_8));
            return this;
        }

        public Builder addFormFile(String field, String filename, String type, byte[] b) {
            StringBuilder builder = new StringBuilder();
            builder.append("--").append(boundary).append(CRLF)
                    .append("Content-Disposition: form-data; name=\"").append(field).append("\"; filename=\"").append(filename).append("\"").append(CRLF)
                    .append("Content-Type: ").append(type).append(CRLF)
                    .append(CRLF);
            list.add(builder.toString().getBytes(StandardCharsets.UTF_8));
            list.add(b);
            list.add(CRLF.getBytes(StandardCharsets.UTF_8));
            return this;
        }

        public MultipartBody build() throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (byte[] part : list) {
                outputStream.write(part);
            }
            String end = "--" + boundary + "--" + CRLF;
            outputStream.write(end.getBytes(StandardCharsets.UTF_8));
            return new MultipartBody(boundary, outputStream.toByteArray());
        }

        private String generateBoundary() {
            return "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
    }
}
