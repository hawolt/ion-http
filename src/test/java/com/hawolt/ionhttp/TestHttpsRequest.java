package com.hawolt.ionhttp;

import com.hawolt.ionhttp.request.IonRequest;
import com.hawolt.ionhttp.request.IonResponse;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestHttpsRequest {
    @Test
    public void test() {

        IonRequest request = IonRequest.on("https://example.com")
                .addHeader("Host", "example.com")
                .get();

        IonClient client = IonClient.getDefault();

        try (IonResponse response = client.execute(request)) {

            int code = response.code();
            byte[] body = response.body();
            String string = new String(body, StandardCharsets.UTF_8);

            int lastIndex = string.lastIndexOf("<");
            String closing = string.substring(lastIndex, lastIndex + 7);

            assertEquals("Status code should be 200", 200, code);
            assertTrue("Body length should be greater than 0", body.length > 0);
            assertEquals("Response should end with </html> ", "</html>", closing);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
