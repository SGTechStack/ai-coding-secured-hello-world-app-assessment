package com.eitri.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.RealServerLogin;
import com.eitri.testsupport.StructuredLogTestCapture;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

/**
 * When the TCP peer is not a trusted proxy, forwarded headers are ignored: a client talking to the app
 * directly can neither choose the address the IP throttle and audit trail see, nor claim HTTPS.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        // Trust only an address the test client never connects from, so 127.0.0.1 is an untrusted peer.
        properties = "server.tomcat.remoteip.internal-proxies=192\\.0\\.2\\.1")
@DirtiesContext
class UntrustedPeerForwardedHeadersIT {

    private static final String PEER_IP = "127.0.0.1";

    @LocalServerPort
    private int port;

    @Test
    void aForgedForwardedForDoesNotEscapeTheIpThrottle() throws Exception {
        RealServerLogin server = new RealServerLogin(port);
        // Each failure claims a different client address; all of them come from the same peer.
        for (int attempt = 0; attempt < 20; attempt++) {
            assertThat(server.login("sprayed-user-" + attempt, "WrongPassword!", "198.51.100." + attempt)
                            .statusCode())
                    .isEqualTo(401);
        }

        try (StructuredLogTestCapture audit = StructuredLogTestCapture.audit()) {
            HttpResponse<String> throttled = server.login("sprayed-user", "WrongPassword!", "198.51.100.200");

            assertThat(throttled.statusCode()).isEqualTo(429);
            assertThat(JsonPath.<String>read(audit.line("Login rate limit exceeded"), "$.source.ip"))
                    .isEqualTo(PEER_IP);
        }
    }

    @Test
    void aForgedForwardedProtoDoesNotEarnHsts() throws Exception {
        RealServerLogin server = new RealServerLogin(port);

        HttpResponse<String> response = server.send(HttpRequest.newBuilder(server.uri("/actuator/health"))
                .header("X-Forwarded-Proto", "https")
                .GET()
                .build());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
    }
}
