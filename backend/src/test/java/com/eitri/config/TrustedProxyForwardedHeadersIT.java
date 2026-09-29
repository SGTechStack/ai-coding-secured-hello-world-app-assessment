package com.eitri.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.RealServerLogin;
import com.eitri.testsupport.StructuredLogTestCapture;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * With the default trusted-proxy range (loopback), a request from {@code 127.0.0.1} is treated as coming
 * through the trusted edge: its {@code X-Forwarded-For} becomes the client address that the IP throttle
 * and the audit trail see.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TrustedProxyForwardedHeadersIT {

    private static final String CLIENT_IP = "203.0.113.50";
    private static final String OTHER_CLIENT_IP = "198.51.100.60";

    @LocalServerPort
    private int port;

    @Test
    void theTrustedProxysForwardedForIsTheThrottledAndAuditedAddress() throws Exception {
        RealServerLogin server = new RealServerLogin(port);
        for (int attempt = 0; attempt < 20; attempt++) {
            assertThat(server.login("proxied-user-" + attempt, "WrongPassword!", CLIENT_IP).statusCode())
                    .isEqualTo(401);
        }

        try (StructuredLogTestCapture audit = StructuredLogTestCapture.audit()) {
            HttpResponse<String> throttled = server.login("proxied-user", "WrongPassword!", CLIENT_IP);

            assertThat(throttled.statusCode()).isEqualTo(429);
            assertThat(JsonPath.<String>read(audit.line("Login rate limit exceeded"), "$.source.ip"))
                    .isEqualTo(CLIENT_IP);
        }
        // Another client behind the same proxy has its own budget.
        assertThat(server.login("proxied-user", "WrongPassword!", OTHER_CLIENT_IP).statusCode())
                .isEqualTo(401);
    }
}
