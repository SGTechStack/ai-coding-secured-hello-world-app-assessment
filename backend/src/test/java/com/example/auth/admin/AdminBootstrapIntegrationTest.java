package com.example.auth.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.auth.user.Role;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifies the admin bootstrap actually seeds a usable ADMIN at startup and is
 * idempotent. Uses a dedicated admin config + H2 so it is deterministic
 * regardless of other test contexts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:bootstraptest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "app.admin.username=bootadmin",
    "app.admin.email=bootadmin@admin.test",
    "app.admin.password=boot-admin-pass-99"
})
class AdminBootstrapIntegrationTest {

    private static final String ADMIN_USERNAME = "bootadmin";
    private static final String ADMIN_PASSWORD = "boot-admin-pass-99";

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository users;

    @Autowired
    AdminBootstrapRunner runner;

    @Test
    void seedsAdminAtStartup_withAdminRoleAndBcryptHash() {
        User admin = users.findByUsername(ADMIN_USERNAME).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getPasswordHash()).startsWith("$2a$");
        assertThat(admin.getPasswordHash()).isNotEqualTo(ADMIN_PASSWORD);
    }

    @Test
    void seededAdminCanLogInAndAccessAdminEndpoints() {
        String csrf = valueOfCookie(
                restTemplate.exchange("/api/auth/csrf", HttpMethod.GET, null, Void.class).getHeaders(), "XSRF-TOKEN");
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.set("X-XSRF-TOKEN", csrf);
        loginHeaders.set(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);

        ResponseEntity<Void> login = restTemplate.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(String.format("{\"username\":\"%s\",\"password\":\"%s\"}",
                        ADMIN_USERNAME, ADMIN_PASSWORD), loginHeaders),
                Void.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        String session = "SESSION=" + valueOfCookie(login.getHeaders(), "SESSION");
        HttpHeaders authed = new HttpHeaders();
        authed.set(HttpHeaders.COOKIE, session);

        ResponseEntity<String> adminList = restTemplate.exchange("/api/admin/users", HttpMethod.GET,
                new HttpEntity<>(authed), String.class);
        assertThat(adminList.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void bootstrapIsIdempotent_noDuplicateAdminOnRerun() {
        long before = adminCount();
        runner.run(new DefaultApplicationArguments());
        runner.run(new DefaultApplicationArguments());
        assertThat(adminCount()).isEqualTo(before);
        assertThat(users.findAll().stream()
                .filter(u -> u.getUsername().equals(ADMIN_USERNAME)).count()).isEqualTo(1);
    }

    private long adminCount() {
        return users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).count();
    }

    private String valueOfCookie(HttpHeaders headers, String name) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String c : cookies) {
                if (c.startsWith(name + "=")) {
                    return c.substring((name + "=").length()).split(";")[0];
                }
            }
        }
        return null;
    }
}
