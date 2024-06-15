package com.hawolt.ionhttp.request;

import com.hawolt.ionhttp.misc.SocketReader;

import java.io.IOException;
import java.net.Socket;
import java.util.*;

public class IonResponse implements AutoCloseable {
    private final Map<String, List<String>> headers = new HashMap<>();
    private final String version, reason;
    private final IonReadState state;
    private final Socket socket;
    private final int code;

    private IonResponse predecessor;

    public static IonResponse create(Socket socket, IonReadState state) throws IOException {
        SocketReader reader = new SocketReader(socket.getInputStream());
        IonResponse response = new IonResponse(socket, state, reader.readContentLine());
        if (state == IonReadState.STATUS) return response;
        response.headers();
        return response;
    }

    private IonResponse(Socket socket, IonReadState state, String status) {
        String[] data = status.split(" ");
        this.code = Integer.parseInt(data[1]);
        this.version = data[0];
        this.reason = data[2];
        this.socket = socket;
        this.state = state;
    }

    public void addHeader(String k, String v) {
        if (!headers.containsKey(k)) headers.put(k, new LinkedList<>());
        this.headers.get(k).add(v);
    }

    private void headers() throws IOException {
        SocketReader reader = new SocketReader(socket.getInputStream());
        String line;
        while (!(line = reader.readContentLine()).isEmpty()) {
            String[] data = line.split(":", 2);
            addHeader(data[0], data[1].trim());
        }
    }

    public byte[] body() throws IOException {
        if (state == IonReadState.STATUS) headers();
        boolean close = headers.getOrDefault("Connection", new ArrayList<>()).stream().anyMatch("close"::equals);
        boolean encoded = headers.containsKey("Transfer-Encoding");
        boolean length = headers.containsKey("Content-Length");
        if (encoded && length) throw new IOException("Encountered a pair of headers that is not permitted");
        SocketReader reader = new SocketReader(socket.getInputStream());
        if (length) {
            return reader.readContentLengthBody(Integer.parseInt(headers.get("Content-Length").get(0)));
        } else if (encoded) {
            String encoding = headers.get("Transfer-Encoding").get(0);
            switch (encoding) {
                case "chunked":
                    return reader.readChunkedBody();
                default:
                    throw new IOException("Encountered unknown Transfer-Encoding: " + encoding);
            }
        } else if (close) {
            return reader.readBasicBody();
        } else {
            throw new IOException("Encountered unknown state for the body");
        }
    }

    public IonResponse getPredecessor() {
        return predecessor;
    }

    public void setPredecessor(IonResponse predecessor) {
        this.predecessor = predecessor;
    }

    public Map<String, List<String>> getHeaderMap() {
        return headers;
    }

    public String getVersion() {
        return version;
    }

    public String getReason() {
        return reason;
    }

    public int getCode() {
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
        return builder.toString();
    }

    @Override
    public void close() throws Exception {
        this.socket.close();
    }
}
