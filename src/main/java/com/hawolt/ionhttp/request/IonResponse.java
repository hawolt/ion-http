package com.hawolt.ionhttp.request;

import com.hawolt.ionhttp.cookies.Cookie;
import com.hawolt.ionhttp.cookies.CookieManager;
import com.hawolt.ionhttp.misc.SocketReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class IonResponse implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(IonResponse.class);

    private final Map<String, List<String>> headers = new HashMap<>();
    private final String version, reason;
    private final CookieManager manager;
    private final SocketReader reader;
    private final IonRequest origin;
    private final Socket socket;

    private final int code;
    private byte[] body;
    private IonResponse predecessor;

    public static IonResponse create(
            IonRequest request, Socket socket, CookieManager manager
    ) throws IOException {
        SocketReader reader = new SocketReader(socket.getInputStream());
        IonReadState state = request.builder().state;
        IonResponse response = new IonResponse(request, socket, manager, reader, reader.readContentLine());
        if (state == IonReadState.STATUS) return response;
        response.readHeader();
        return response;
    }

    private IonResponse(
            IonRequest request,
            Socket socket,
            CookieManager manager,
            SocketReader reader,
            String status
    ) {
        String[] data = status.split(" ", 3);
        this.code = Integer.parseInt(data[1]);
        this.manager = manager;
        this.version = data[0];
        this.reason = data.length > 2 ? data[2] : "";
        this.origin = request;
        this.socket = socket;
        this.reader = reader;
    }

    public void addHeader(String k, String v) {
        if (!headers.containsKey(k)) headers.put(k, new LinkedList<>());
        this.headers.get(k).add(v);
    }

    private void readHeader() throws IOException {
        String line;
        while (!(line = reader.readContentLine()).isEmpty()) {
            String[] data = line.split(":", 2);
            if (data.length < 2) continue; // skip malformed header lines
            addHeader(data[0].toLowerCase(), data[1].trim());
        }
        List<String> setCookieHeaders = headers.get("set-cookie");
        if (setCookieHeaders == null) return;
        IonRequest.Builder builder = origin.builder();
        String url = String.format("%s://%s/", builder.protocol, builder.hostname);
        manager.add(
                setCookieHeaders.stream()
                        .map(content -> new Cookie(url, content))
                        .toArray(Cookie[]::new)
        );
    }

    public byte[] body() throws IOException {
        if (body != null) return body;
        if (origin.builder().state == IonReadState.STATUS) readHeader();

        boolean close = headers.getOrDefault("connection", Collections.emptyList())
                .stream()
                .anyMatch("close"::equalsIgnoreCase);

        boolean encoded = headers.containsKey("transfer-encoding");
        boolean hasLength = headers.containsKey("content-length");

        if (encoded && hasLength) {
            throw new IOException(
                    "Response contains both Transfer-Encoding and Content-Length headers, " +
                            "which is not permitted by RFC 7230."
            );
        }

        if (hasLength) {
            int length = Integer.parseInt(
                    headers.get("content-length").get(0).trim()
            );
            this.body = reader.readContentLengthBody(length);
        } else if (encoded) {
            List<String> encodings = headers.get("transfer-encoding");
            String outermost = encodings.get(encodings.size() - 1).trim().toLowerCase();
            switch (outermost) {
                case "chunked":
                    this.body = reader.readChunkedBody();
                    break;
                default:
                    throw new IOException(
                            "Unsupported Transfer-Encoding: " + outermost
                    );
            }
        } else if (close) {
            this.body = reader.readBasicBody();
        } else if (code == 204 || code == 304) {
            this.body = new byte[0];
        } else {
            if ("HEAD".equalsIgnoreCase(origin.builder().method)) {
                this.body = new byte[0];
            } else {
                throw new IOException(
                        "Cannot determine how to read response body " +
                                "(no Content-Length, no Transfer-Encoding, connection not closing, " +
                                "and status code " + code + " is not 204/304)."
                );
            }
        }

        if (headers.containsKey("content-encoding")) {
            String encoding = headers.get("content-encoding").get(0).trim().toLowerCase();
            switch (encoding) {
                case "gzip":
                    this.body = decompressGzip(this.body);
                    break;
                case "deflate":
                    this.body = decompressDeflate(this.body);
                    break;
                default:
                    logger.warn("[ion-http] Unsupported Content-Encoding: {}", encoding);
                    break;
            }
        }

        return body;
    }

    private byte[] decompressGzip(byte[] b) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(b);
             GZIPInputStream gis = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = gis.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return bos.toByteArray();
        }
    }

    private byte[] decompressDeflate(byte[] b) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(b);
             InflaterInputStream iis = new InflaterInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = iis.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
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
        return Collections.unmodifiableMap(headers);
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
        builder.append(version).append(' ').append(code).append(' ').append(reason).append(System.lineSeparator());
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            for (String value : entry.getValue()) {
                builder.append(entry.getKey()).append(": ").append(value).append(System.lineSeparator());
            }
        }
        if (body != null) {
            builder.append(new String(body, StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    @Override
    public void close() throws IOException {
        this.socket.close();
    }
}