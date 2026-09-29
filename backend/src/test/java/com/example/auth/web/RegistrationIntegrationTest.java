package com.example.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.auth.user.UserResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

/**
 * HTTP-boundary integration tests for POST /api/auth/register.
 * Exercises the real Spring Security filter chain, CSRF, H2, and the full
 * controller→service→repository stack. Security internals are not mocked.
 * @DirtiesContext resets the in-memory H2 between test classes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.test.context.TestPropertySource(properties =
    "spring.datasource.url=jdbc:h2:mem:regtest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class RegistrationIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    private String csrfToken;

    @BeforeEach
    void fetchCsrfToken() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/auth/csrf", HttpMethod.GET, null, Void.class);
        csrfToken = extractCsrfCookie(response.getHeaders());
    }

    private String extractCsrfCookie(HttpHeaders headers) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("XSRF-TOKEN=")) {
                    return cookie.substring("XSRF-TOKEN=".length()).split(";")[0];
                }
            }
        }
        return null;
    }

    private HttpEntity<String> jsonEntityWithCsrf(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (csrfToken != null) {
            headers.set("X-XSRF-TOKEN", csrfToken);
            headers.set(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        }
        return new HttpEntity<>(body, headers);
    }

    @Test
    void successfulRegistrationReturns201WithUserResponse() {
        String body = """
                {"username":"alice","email":"alice@example.com","password":"secure-pass-12"}
                """;

        ResponseEntity<UserResponse> response = restTemplate.exchange(
                "/api/auth/register", HttpMethod.POST,
                jsonEntityWithCsrf(body), UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UserResponse user = response.getBody();
        assertThat(user).isNotNull();
        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.email()).isEqualTo("alice@example.com");
        assertThat(user.role().name()).isEqualTo("USER");
        assertThat(user.enabled()).isTrue();
        assertThat(user.id()).isNotNull();
        assertThat(user.createdAt()).isNotNull();
    }

    @Test
    void passwordHashNotExposedInResponse() {
        // UserResponse has no passwordHash field — verify by checking the raw JSON string
        String body = """
                {"username":"bob","email":"bob@example.com","password":"secure-pass-12"}
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/register", HttpMethod.POST,
                jsonEntityWithCsrf(body), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).doesNotContain("passwordHash", "password_hash", "$2a$");
    }

    @Test
    void passwordTooShortReturns400() {
        String body = """
                {"username":"carol","email":"carol@example.com","password":"short"}
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/register", HttpMethod.POST,
                jsonEntityWithCsrf(body), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void passwordTooLongReturns400() {
        String tooLong = "a".repeat(73);
        String body = String.format(
                "{\"username\":\"dave\",\"email\":\"dave@example.com\",\"password\":\"%s\"}", tooLong);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/register", HttpMethod.POST,
                jsonEntityWithCsrf(body), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void duplicateUsernameReturns409() {
        String body = """
                {"username":"eve","email":"eve@example.com","password":"secure-pass-12"}
                """;
        restTemplate.exchange("/api/auth/register", HttpMethod.POST, jsonEntityWithCsrf(body), Void.class);

        // second registration same username, different email
        String duplicate = """
                {"username":"eve","email":"eve2@example.com","password":"secure-pass-12"}
                """;
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/register", HttpMethod.POST,
                jsonEntityWithCsrf(duplicate), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void duplicateEmailReturns409() {
        String body = """
                {"username":"frank","email":"shared@example.com","password":"secure-pass-12"}
                """;
        restTemplate.exchange("/api/auth/register", HttpMethod.POST, jsonEntityWithCsrf(body), Void.class);

        // second registration same email, different username
        String duplicate = """
                {"username":"frank2","email":"shared@example.com","password":"secure-pass-12"}
                """;
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/register", HttpMethod.POST,
                jsonEntityWithCsrf(duplicate), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void errorResponseIsApplicationProblemJson() {
        String body = """
                {"username":"grace","email":"grace@example.com","password":"short"}
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/register", HttpMethod.POST,
                jsonEntityWithCsrf(body), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        assertThat(contentType).contains("application/problem+json");
    }
}
