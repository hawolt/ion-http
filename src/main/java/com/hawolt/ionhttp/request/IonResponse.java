package com.hawolt.ionhttp.request;

import com.hawolt.ionhttp.cookies.Cookie;
import com.hawolt.ionhttp.cookies.CookieManager;
import com.hawolt.ionhttp.misc.SocketReader;
import com.hawolt.logger.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class IonResponse implements AutoCloseable {
    private final Map<String, List<String>> headers = new HashMap<>();
    private final String version, reason;
    private final CookieManager manager;
    private final IonRequest origin;
    private final Socket socket;
    private final int code;
    private byte[] body;
    private IonResponse predecessor;

    public static IonResponse create(IonRequest request, Socket socket, CookieManager manager) throws IOException {
        SocketReader reader = new SocketReader(socket.getInputStream());
        IonReadState state = request.builder().state;
        IonResponse response = new IonResponse(request, socket, manager, reader.readContentLine());
        if (state == IonReadState.STATUS) return response;
        response.readHeader();
        return response;
    }

    private IonResponse(IonRequest request, Socket socket, CookieManager manager, String status) {
        String[] data = status.split(" ");
        this.code = Integer.parseInt(data[1]);
        this.manager = manager;
        this.version = data[0];
        this.reason = data[2];
        this.origin = request;
        this.socket = socket;
    }

    public void addHeader(String k, String v) {
        if (!headers.containsKey(k)) headers.put(k, new LinkedList<>());
        this.headers.get(k).add(v);
    }

    private void readHeader() throws IOException {
        SocketReader reader = new SocketReader(socket.getInputStream());
        String line;
        while (!(line = reader.readContentLine()).isEmpty()) {
            String[] data = line.split(":", 2);
            addHeader(data[0].toLowerCase(), data[1].trim());
        }
        List<String> base = headers.get("set-cookie");
        if (base == null) return;
        IonRequest.Builder builder = origin.builder();
        String url = String.format("%s://%s/", builder.protocol, builder.hostname);
        manager.add(
                base.stream()
                        .map(content -> new Cookie(url, content))
                        .toArray(Cookie[]::new)
        );
    }

    public byte[] body() throws IOException {
        if (body != null) return body;
        if (origin.builder().state == IonReadState.STATUS) readHeader();
        boolean close = headers.getOrDefault("connection", new ArrayList<>()).stream().anyMatch("close"::equals);
        boolean encoded = headers.containsKey("transfer-encoding");
        boolean length = headers.containsKey("content-length");
        if (encoded && length) throw new IOException("Encountered a pair of headers that is not permitted");
        SocketReader reader = new SocketReader(socket.getInputStream());
        if (length) {
            this.body = reader.readContentLengthBody(Integer.parseInt(headers.get("content-length").get(0)));
        } else if (encoded) {
            String encoding = headers.get("transfer-encoding").get(0);
            switch (encoding) {
                case "chunked":
                    this.body = reader.readChunkedBody();
                    break;
                default:
                    throw new IOException("Encountered unknown Transfer-Encoding: " + encoding);
            }
        } else if (close) {
            this.body = reader.readBasicBody();
        } else if (code == 204 || code == 304) {
            this.body = new byte[0];
        } else {
            throw new IOException("Encountered unknown state for the body");
        }

        if (headers.containsKey("content-encoding")) {
            String encoding = headers.get("content-encoding").get(0);
            if ("gzip".equals(encoding)) {
                this.body = unzip(this.body);
            } else {
                Logger.warn("Unknown Content-Encoding:{}", encoding);
            }
        }

        return body;
    }

    private byte[] unzip(byte[] b) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(b);
             GZIPInputStream gis = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = gis.read(buffer)) != -1) {
                bos.write(buffer, 0, length);
            }
            return bos.toByteArray();
        }
    }

    public IonRequest origin() {
        return origin;
    }

    public IonResponse predecessor() {
        return predecessor;
    }

    public void setPredecessor(IonResponse predecessor) {
        this.predecessor = predecessor;
    }

    public Map<String, List<String>> headers() {
        return headers;
    }

    public String version() {
        return version;
    }

    public String reason() {
        return reason;
    }

    public int code() {
        return code;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(version).append(" ").append(code).append(" ").append(reason).append(System.lineSeparator());
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String key = entry.getKey();
            for (String value : entry.getValue()) {
                builder.append(key).append(": ").append(value).append(System.lineSeparator());
            }
        }
        if (body != null) builder.append(new String(body, StandardCharsets.UTF_8));
        return builder.toString();
    }

    @Override
    public void close() throws IOException {
        this.socket.close();
    }
}
