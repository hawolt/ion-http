package com.hawolt.ionhttp;

import com.hawolt.ionhttp.misc.SocketReader;
import com.hawolt.ionhttp.misc.SocketWriter;
import com.hawolt.ionhttp.misc.Threading;
import com.hawolt.ionhttp.proxy.BaseProxyServer;
import com.hawolt.ionhttp.proxy.ProxyAuthentication;
import org.junit.Test;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertEquals;

public class TestProxyFunctionality {
    @Test
    public void test() {
        String username = "unit";
        String password = "test";

        BaseProxyServer server = new BaseProxyServer(45678, new ProxyAuthentication(
                username,
                password
        ));

        Threading.waitUntil(server::isRunning, 200L);

        try {
            Socket socket = new Socket("127.0.0.1", 45678);

            SocketWriter writer = new SocketWriter(socket.getOutputStream());

            String basic = Base64.getEncoder().encodeToString(
                    String.join(":", username, password).getBytes(StandardCharsets.UTF_8)
            );

            writer.write("CONNECT example.com HTTP/1.1");
            writer.write("Proxy-Authorization: Basic " + basic);
            writer.write("Host: example.com");
            writer.write("");
            writer.write("GET / HTTP/1.1");
            writer.write("Host: example.com");
            writer.write("");

            SocketReader reader = new SocketReader(socket.getInputStream());
            StringBuilder builder = new StringBuilder();
            builder.append(reader.readContentLine());
            String line;
            while (!(line = reader.readContentLine()).isEmpty()) {
                String[] data = line.split(":", 2);
                builder.append(data[0].toLowerCase()).append(": ").append(data[1].trim());
            }
            builder.append(
                    new String(
                            reader.readBasicBody(),
                            StandardCharsets.UTF_8
                    )
            );

            socket.close();
            server.shutdown();

            String response = builder.toString();

            int lastIndex = response.lastIndexOf("<");
            String closing = response.substring(lastIndex, lastIndex + 7);

            assertEquals("Response should end with </html>", "</html>", closing);
        } catch (IOException e) {
            assert false;
        }
    }
}
