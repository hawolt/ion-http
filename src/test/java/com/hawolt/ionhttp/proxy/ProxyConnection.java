package com.hawolt.ionhttp.proxy;

import com.hawolt.ionhttp.misc.SocketReader;
import com.hawolt.ionhttp.misc.SocketWriter;
import com.hawolt.ionhttp.request.IonRequest;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyConnection implements Runnable, AutoCloseable {
    private final ExecutorService service = Executors.newFixedThreadPool(1);
    private final ProxyConnectionContext context;
    private final Socket socket;

    private ProxyConnection(ProxyConnectionContext context, Socket socket) {
        this.socket = socket;
        this.context = context;
        this.service.execute(this);
    }

    public static ProxyConnection create(ProxyAuthentication authentication, Socket socket) {
        ProxyConnectionContext connectionContext = new ProxyConnectionContext(authentication);
        return new ProxyConnection(connectionContext, socket);
    }

    @Override
    public void close() throws Exception {
        this.socket.close();
    }

    @Override
    public void run() {
        try (
                SocketReader reader = new SocketReader(socket.getInputStream());
                SocketWriter writer = new SocketWriter(socket.getOutputStream())
        ) {
            socket.setSoTimeout(5000);

            String line;

            try {
                line = reader.readContentLine();
            } catch (SocketTimeoutException e) {
                respond(writer, 408, "Request Timeout");
                return;
            }

            if (line == null || line.isEmpty()) {
                respond(writer, 400, "Bad Request");
                return;
            }

            Map<String, String> headers;
            headers = getHeaderMap(reader);

            if (context.isAuthenticationRequired()) {
                boolean available = headers.containsKey("Proxy-Authorization");
                line = headers.get("Proxy-Authorization");
                String[] values = (!available || line == null) ? null : line.split(" ", 2);
                if (!available || line == null || !"Basic".equals(values[0])) {
                    respond(
                            writer,
                            407,
                            "Proxy Authentication Required",
                            "Proxy-Authorization: Basic realm=\"test\""
                    );
                    return;
                } else {
                    String base64 = values[1];
                    byte[] decoded = Base64.getDecoder().decode(base64.getBytes(StandardCharsets.UTF_8));
                    String[] credentials = new String(decoded, StandardCharsets.UTF_8).split(":");
                    ProxyAuthentication authentication = context.getAuthentication();
                    if (!authentication.verify(credentials)) {
                        respond(
                                writer,
                                407,
                                "Proxy Authentication Required",
                                "Proxy-Authorization: Basic realm=\"test\""
                        );
                        return;
                    }
                }
            }

            line = reader.readContentLine();

            String[] status = line.split(" ", 3);
            if (status.length != 3) {
                respond(writer, 400, "Bad Request");
                return;
            }

            String method = status[0];
            String path = status[1];
            String version = status[2];

            headers = getHeaderMap(reader);

            String host = headers.get("Host");

            IonRequest.AdvancedBuilder builder = new IonRequest.AdvancedBuilder()
                    .method(method)
                    .protocol("http")
                    .hostname(host)
                    .port(80)
                    .path(path);
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }

            InetAddress address = InetAddress.getByName(host);
            String ip = address.getHostAddress();

            try (Socket proxied = new Socket(ip, 80)) {
                SocketWriter w1 = new SocketWriter(proxied.getOutputStream());
                w1.write(String.join(" ", builder.method, builder.path, "HTTP/1.1"));
                for (Map.Entry<String, List<String>> entry : builder.headers.entrySet()) {
                    for (String value : entry.getValue()) {
                        w1.write(String.join(": ", entry.getKey(), value));
                    }
                }
                w1.write("");
                w1.flush();

                SocketReader r1 = new SocketReader(proxied.getInputStream());
                String s1 = r1.readContentLine();
                headers = getHeaderMap(r1);

                String transferEncoding = headers.get("Transfer-Encoding");
                String contentLength = headers.get("Content-Length");
                byte[] body;
                if ("chunked".equalsIgnoreCase(transferEncoding)) {
                    body = r1.readChunkedBody();
                } else {
                    if (contentLength != null) {
                        int length = Integer.parseInt(contentLength);
                        body = r1.readContentLengthBody(length);
                    } else {
                        body = r1.readBasicBody();
                    }
                }

                writer.write(s1);
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    writer.write(String.join(": ", entry.getKey(), entry.getValue()));
                }
                writer.write("");
                writer.write(body);
                writer.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.service.shutdown();
    }

    private Map<String, String> getHeaderMap(SocketReader reader) throws IOException {
        String line;
        Map<String, String> headers = new HashMap<>();
        while (!(line = reader.readContentLine()).isEmpty()) {
            String[] data = line.split(": ", 2);
            String value = data.length == 1 ? "" : data[1];
            headers.put(data[0], value);
        }
        return headers;
    }

    private void respond(SocketWriter writer, int code, String reason, String... headers) throws IOException {
        writer.write(String.format("HTTP/1.1 %s %s", code, reason));
        for (String header : headers) {
            writer.write(header);
        }
        writer.write("Content-Length: 0");
        writer.write("");
        writer.flush();
    }
}
