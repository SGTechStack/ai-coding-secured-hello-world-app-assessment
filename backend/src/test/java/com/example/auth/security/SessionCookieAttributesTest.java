package com.example.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.auth.LoginRequest;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

/**
 * Session-security NFR (HttpOnly / SameSite session cookie, 15-minute idle
 * timeout) against a real embedded server rather than MockMvc. Spring Boot
 * only applies {@code server.servlet.session.*} to Spring Session when it
 * runs its own web server; under MockMvc's mock servlet context it treats the
 * app as a WAR deployment and takes the cookie settings from that (empty)
 * mock context instead, so the {@code SESSION} cookie there carries none of
 * these attributes and asserting on it would prove nothing about production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class SessionCookieAttributesTest {

    private static final String USERNAME = "session-cookie-user";
    private static final String PASSWORD = "Password123!";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @Test
    void loginSetsAnHttpOnlySameSiteSessionCookieBackedByA15MinuteSession() throws Exception {
        if (userRepository.findByUsername(USERNAME).isEmpty()) {
            userRepository.save(
                    new User(USERNAME, "session-cookie-user@example.com", passwordEncoder.encode(PASSWORD), "Cookie"));
        }
        HttpClient client = HttpClient.newHttpClient();

        // Mints the XSRF-TOKEN cookie, exactly like a browser's first request.
        HttpResponse<String> me = client.send(
                HttpRequest.newBuilder(uri("/api/auth/me")).GET().build(), HttpResponse.BodyHandlers.ofString());
        String csrfToken = cookieValue(me.headers().allValues("Set-Cookie"), "XSRF-TOKEN");

        HttpResponse<String> login = client.send(
                HttpRequest.newBuilder(uri("/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .header("Cookie", "XSRF-TOKEN=" + csrfToken)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(new LoginRequest(USERNAME, PASSWORD))))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(login.statusCode()).isEqualTo(200);
        List<String> setCookies = login.headers().allValues("Set-Cookie");
        assertThat(setCookies).noneMatch(header -> header.startsWith("JSESSIONID="));
        assertThat(setCookies)
                .filteredOn(header -> header.startsWith("SESSION="))
                .singleElement()
                .satisfies(sessionCookie -> assertThat(sessionCookie)
                        .contains("HttpOnly")
                        .contains("SameSite=Lax")
                        .doesNotContain("Secure"));

        assertThat(sessionRepository.findByPrincipalName(USERNAME).values())
                .isNotEmpty()
                .allSatisfy(session -> assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(15)));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String cookieValue(List<String> setCookieHeaders, String name) {
        String header = setCookieHeaders.stream()
                .filter(candidate -> candidate.startsWith(name + "="))
                .findFirst()
                .orElseThrow();
        return header.substring(name.length() + 1, header.indexOf(';'));
    }
}
