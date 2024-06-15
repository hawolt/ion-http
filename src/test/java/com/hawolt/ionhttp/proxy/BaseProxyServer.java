package com.hawolt.ionhttp.proxy;

import com.hawolt.ionhttp.misc.Threading;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BaseProxyServer implements Runnable {
    private final ExecutorService service = Executors.newSingleThreadExecutor();
    private final ProxyAuthentication authentication;
    private final int port;
    private ServerSocket socket;

    public BaseProxyServer(int port) {
        this(port, null);
    }

    public BaseProxyServer(int port, ProxyAuthentication authentication) {
        this.port = port;
        this.authentication = authentication;
        this.service.execute(this);
    }

    public void shutdown() throws IOException {
        Threading.waitUntil(() -> socket != null, 200L);
        this.service.shutdown();
        this.socket.close();
    }

    public boolean isRunning() {
        return socket != null && !socket.isClosed();
    }

    @Override
    public void run() {
        try {
            this.socket = new ServerSocket(port);
            do {
                Socket connection = socket.accept();
                ProxyConnection.create(authentication, connection);
            } while (!socket.isClosed());
        } catch (IOException e) {
            if ("Socket closed".equals(e.getMessage())) return;
            throw new RuntimeException(e);
        }
    }
}
