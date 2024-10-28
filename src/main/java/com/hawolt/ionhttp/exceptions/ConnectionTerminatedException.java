package com.hawolt.ionhttp.exceptions;

import java.io.IOException;

public class ConnectionTerminatedException extends IOException {

    public ConnectionTerminatedException() {
        super("Remote host closed the connection");
    }
}
