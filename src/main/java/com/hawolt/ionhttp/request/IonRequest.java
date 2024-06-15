package com.hawolt.ionhttp.request;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class IonRequest {
    private final Builder builder;

    private IonRequest(Builder builder) {
        this.builder = builder;
    }

    public Builder getBuilder() {
        return builder;
    }

    public static SimpleBuilder on(String url) {
        return new SimpleBuilder(url);
    }

    public static class Builder {
        public final Map<String, String> headers = new LinkedHashMap<>();
        public final Map<String, String> parameters = new HashMap<>();
        public String method, protocol, hostname, path;

        public IonReadState state;
        public byte[] payload;
        public int port;

        @Override
        public String toString() {
            String base = String.format(
                    "%s://%s%s/%s",
                    protocol,
                    hostname,
                    port > 0 ? ":" + port : "",
                    path
            );
            StringBuilder builder = new StringBuilder(base);
            if (!parameters.isEmpty()) builder.append("?");
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                builder.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
            }
            if (!parameters.isEmpty()) builder.setLength(builder.length() - 1);
            return builder.toString();
        }
    }

    public static class SimpleBuilder extends Builder {

        public SimpleBuilder(String url) {
            this.state = IonReadState.HEADER;
            String[] arr;
            arr = url.split(":", 2);
            this.protocol = arr[0];
            if ("http".equals(protocol)) {
                this.port = 80;
            } else if ("https".equals(protocol)) {
                this.port = 443;
            }
            arr = arr[1].substring(2).split("/", 2);
            this.hostname = arr[0];
            if (arr.length == 2) {
                arr = arr[1].split("\\?", 2);
                this.path = arr[0];
            } else {
                this.path = "/";
            }
            if (arr.length < 2) return;
            arr = arr[1].split("&");
            for (String parameter : arr) {
                String[] pair = parameter.split("=", 2);
                String value = pair.length == 2 ? pair[1] : "";
                this.parameters.put(pair[0], value);
            }
        }

        public SimpleBuilder addHeader(String k, Object v) {
            this.headers.put(k, v.toString());
            return this;
        }

        public SimpleBuilder payload(byte[] payload) {
            this.payload = payload;
            return this;
        }

        public IonRequest get() {
            this.method = "GET";
            return new IonRequest(this);
        }


        public IonRequest head() {
            this.method = "HEAD";
            return new IonRequest(this);
        }

        public IonRequest put() {
            this.method = "PUT";
            return new IonRequest(this);
        }

        public IonRequest delete() {
            this.method = "DELETE";
            return new IonRequest(this);
        }

        public IonRequest options() {
            this.method = "OPTIONS";
            return new IonRequest(this);
        }

        public IonRequest trace() {
            this.method = "TRACE";
            return new IonRequest(this);
        }

        public IonRequest patch() {
            this.method = "PATCH";
            return new IonRequest(this);
        }
    }

    public static class AdvancedBuilder extends Builder {

        public AdvancedBuilder state(IonReadState state) {
            this.state = state;
            return this;
        }

        public AdvancedBuilder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public AdvancedBuilder hostname(String hostname) {
            this.hostname = hostname;
            return this;
        }

        public AdvancedBuilder path(String path) {
            this.path = path;
            return this;
        }

        public AdvancedBuilder method(String method) {
            this.method = method;
            return this;
        }

        public AdvancedBuilder addQueryParameter(String k, Object v) {
            this.parameters.put(k, v.toString());
            return this;
        }

        public AdvancedBuilder addHeader(String k, Object v) {
            this.headers.put(k, v.toString());
            return this;
        }

        public AdvancedBuilder port(int port) {
            this.port = port;
            return this;
        }

        public AdvancedBuilder payload(byte[] payload) {
            this.payload = payload;
            return this;
        }

        public IonRequest build() {
            return new IonRequest(this);
        }
    }
}
