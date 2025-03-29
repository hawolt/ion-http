package com.hawolt.ionhttp;

import com.hawolt.ionhttp.misc.TLS;
import com.hawolt.ionhttp.proxy.ProxyServer;
import com.hawolt.ionhttp.request.IonRequest;
import com.hawolt.ionhttp.request.IonResponse;

import java.io.IOException;

public class Test {
    public static void main(String[] args) throws IOException {
        //IonRequest request = IonRequest.on("https://tls.peet.ws/api/all").get();
        IonRequest request = IonRequest.on("http://188.245.253.191:49851/request").get();
        IonClient client = new IonClient.Builder()
                /*.setProxyServer(ProxyServer.create(
                        "brd.superproxy.io",
                        33335,
                        "brd-customer-hl_e88279f2-zone-static-ip-37.61.226.247",
                        args[0]
                ))*/
                .setVersionTLS(TLS.TLSv1_2)
                .setGracefulTrustManager()
                .setAllowedCipherSuites(
                        "TLS_RSA_WITH_AES_128_CBC_SHA"
                )
                .build();
        try (IonResponse response = client.execute(request)) {
            System.out.println(response);
            System.out.println(new String(response.body()));
        }
    }
}
