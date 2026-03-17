package com.hawolt.ionhttp.request;

import com.hawolt.ionhttp.IonClient;
import com.hawolt.ionhttp.data.ByteSink;
import com.hawolt.ionhttp.data.HttpWriter;
import com.hawolt.ionhttp.proxy.ProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IonRequest implements ByteSink {

    private static final Logger logger = LoggerFactory.getLogger(IonRequest.class);

    private static final Pattern ENCODED_PATTERN = Pattern.compile("%[0-9A-Fa-f]{2}");

    private final Builder builder;

    private IonRequest(Builder builder) {
        this.builder = builder;
    }

    public Builder builder() {
        return builder;
    }

    public static SimpleBuilder on(String url) {
        return new SimpleBuilder(url);
    }

    private HttpWriter buildHttpWriter() {
        HttpWriter writer = new HttpWriter();
        StringBuilder buffer = new StringBuilder(builder.path);

        Map<String, String> parameters = builder.parameters;
        if (!parameters.isEmpty()) buffer.append("?");
        List<String> keys = new ArrayList<>(parameters.keySet());
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String value = parameters.get(key);
            if (i > 0) buffer.append("&");
            buffer.append(getSafeURLEncoded(key));
            buffer.append("=");
            buffer.append(getSafeURLEncoded(value));
        }

        String requestTarget;
        if (requiresAbsoluteTarget()) {
            requestTarget = builder.protocol + "://" + builder.hostname + buffer;
        } else {
            requestTarget = buffer.toString();
        }

        writer.write(String.join(" ", builder.method, requestTarget, "HTTP/1.1"));
        for (Map.Entry<String, List<String>> entry : builder.headers.entrySet()) {
            for (String value : entry.getValue()) {
                writer.write(entry.getKey() + ": " + value);
            }
        }
        writer.write("");

        byte[] payload = builder.payload;
        if (payload != null && payload.length > 0) {
            writer.write(payload);
        }
        return writer;
    }

    private boolean requiresAbsoluteTarget() {
        return builder.proxy != null && "http".equals(builder.protocol);
    }

    @Override
    public String toString() {
        return new String(buildHttpWriter().check(), StandardCharsets.UTF_8);
    }

    private String getSafeURLEncoded(String input) {
        if (input == null || input.isEmpty()) return input;

        Matcher matcher = ENCODED_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                result.append(
                        URLEncoder.encode(
                                input.substring(lastEnd, matcher.start()),
                                StandardCharsets.UTF_8
                        )
                );
            }
            result.append(matcher.group());
            lastEnd = matcher.end();
        }

        if (lastEnd < input.length()) {
            result.append(
                    URLEncoder.encode(input.substring(lastEnd), StandardCharsets.UTF_8)
            );
        }

        return result.toString();
    }

    @Override
    public void drainTo(OutputStream stream) throws IOException {
        HttpWriter writer = buildHttpWriter();
        if (IonClient.debug) {
            logger.debug("- {}\n{}", System.currentTimeMillis(), new String(writer.check(), StandardCharsets.UTF_8));
        }
        stream.write(writer.check());
        stream.flush();
    }

    @Override
    public byte[] check() {
        return buildHttpWriter().check();
    }

    public static class Builder {
        public final Map<String, List<String>> headers = new LinkedHashMap<>();
        public final Map<String, String> parameters = new HashMap<>();
        public String method, protocol, hostname, path;
        public IonReadState state;
        public ProxyServer proxy;
        public byte[] payload;
        public int port;

        @Override
        public String toString() {
            String base = String.format(
                    "%s://%s%s%s",
                    protocol,
                    hostname,
                    port > 0 ? ":" + port : "",
                    path
            );
            StringBuilder builder = new StringBuilder(base);
            if (!parameters.isEmpty()) builder.append("?");
            List<String> parameters = new ArrayList<>(this.parameters.keySet());
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) builder.append("&");
                builder.append(parameters.get(i)).append("=").append(this.parameters.get(parameters.get(i)));
            }
            return builder.toString();
        }
    }

    public static class SimpleBuilder extends Builder {

        public SimpleBuilder(String url) {
            this.state = IonReadState.HEADER;
            String[] arr;
            arr = url.split(":", 2);
            this.protocol = arr[0];
            arr = arr[1].substring(2).split("/", 2);
            String[] port = arr[0].split(":", 2);
            this.hostname = port[0];
            if ("http".equals(protocol)) {
                this.port = port.length == 2 ? Integer.parseInt(port[1]) : 80;
            } else if ("https".equals(protocol)) {
                this.port = port.length == 2 ? Integer.parseInt(port[1]) : 443;
            }

            this.headers.put("Host", new LinkedList<>(Collections.singletonList(this.hostname)));

            if (arr.length == 2) {
                arr = arr[1].split("\\?", 2);
                this.path = "/" + arr[0];
            } else {
                this.path = "/";
            }
            if (arr.length < 2) return;
            for (String parameter : arr[1].split("&")) {
                String[] pair = parameter.split("=", 2);
                String value = pair.length == 2 ? pair[1] : "";
                this.parameters.put(pair[0], value);
            }
        }

        public SimpleBuilder setProxyServer(ProxyServer proxy) {
            this.proxy = proxy;
            return this;
        }

        public SimpleBuilder addQueryParameter(String k, Object v) {
            this.parameters.put(k, v.toString());
            return this;
        }

        public SimpleBuilder addHeader(String k, Object v) {
            if ("Host".equalsIgnoreCase(k)) {
                this.headers.put(k, new LinkedList<>(Collections.singletonList(v.toString())));
            } else {
                this.headers.computeIfAbsent(k, ignored -> new LinkedList<>()).add(v.toString());
            }
            return this;
        }

        public SimpleBuilder payload(byte[] payload) {
            this.payload = payload;
            return this;
        }

        public SimpleBuilder payload(ByteSink sink) {
            this.payload = sink.check();
            return this;
        }

        public SimpleBuilder method(String method) {
            this.method = method;
            return this;
        }

        public IonRequest build() {
            if (method == null) throw new IllegalArgumentException("Method must not be null");
            return new IonRequest(this);
        }

        public IonRequest get() {
            this.method = "GET";
            return new IonRequest(this);
        }

        public IonRequest post() {
            this.method = "POST";
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

        public AdvancedBuilder setProxyServer(ProxyServer proxy) {
            this.proxy = proxy;
            return this;
        }

        public AdvancedBuilder addQueryParameter(String k, Object v) {
            this.parameters.put(k, v.toString());
            return this;
        }

        public AdvancedBuilder addHeader(String k, Object v) {
            this.headers.computeIfAbsent(k, ignored -> new LinkedList<>()).add(v.toString());
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

        public AdvancedBuilder payload(ByteSink sink) {
            this.payload = sink.check();
            return this;
        }

        public IonRequest build() {
            return new IonRequest(this);
        }
    }
}