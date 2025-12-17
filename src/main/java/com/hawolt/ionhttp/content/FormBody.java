package com.hawolt.ionhttp.content;

import com.hawolt.ionhttp.data.ByteSink;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FormBody implements ByteSink {
    private final byte[] b;

    public FormBody(byte[] b) {
        this.b = b;
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
        private final LinkedList<String> key, value;

        public Builder() {
            this.value = new LinkedList<>();
            this.key = new LinkedList<>();
        }

        public Builder add(String key, String value) {
            Objects.requireNonNull(value);
            Objects.requireNonNull(key);

            this.value.add(value);
            this.key.add(key);

            return this;
        }

        private String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }

        public FormBody build() {
            StringBuilder buffer = new StringBuilder();

            for (int i = 0; i < key.size(); i++) {
                if (i != 0) buffer.append('&');
                buffer.append(encode(key.get(i))).append('=').append(encode(value.get(i)));
            }

            byte[] bytes = buffer.toString().getBytes(StandardCharsets.UTF_8);
            return new FormBody(bytes);
        }
    }
}
