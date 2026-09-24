package com.sgtechstack.helloworldauthapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots with {@code app.security.require-https=true} — the default everywhere
 * except dev — to pin that plaintext is actually refused.
 *
 * <p>Previously nothing enforced transport security at all: the PRD accepts
 * HTTP for local development, but no code or configuration prevented the same
 * build from serving plaintext elsewhere, in which case session cookies,
 * submitted passwords and reset tokens all crossed the network in the clear.
 *
 * <p>Supplies its own datasource because dev is the only profile in
 * {@code application.yml} that configures one, and its own admin credentials
 * because the base profile deliberately has no defaults.
 */
@SpringBootTest(properties = {
        "app.security.require-https=true",
        "app.cors.allowed-origins=https://app.example.com",
        "spring.datasource.url=jdbc:h2:mem:https-enforcement-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // Flyway builds the schema and Hibernate validates against it, as a
        // deployment does. The previous ddl-auto=create-drop plus flyway=false
        // existed only because the migrations were PostgreSQL-only; with one
        // engine everywhere, the non-dev path exercises the real migration too.
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "app.admin.username=https-test-admin",
        "app.admin.password=https-test-admin-password-1234",
})
@AutoConfigureMockMvc
class HttpsEnforcementTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void redirectsPlaintextGetsToHttps() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("https://**/api/health"));
    }

    @Test
    void refusesPlaintextEvenForUnauthenticatedWrites() throws Exception {
        // Registration and login carry credentials in the body, so they are the
        // requests that most need to never travel in the clear.
        mockMvc.perform(post("/api/auth/login")
                        .param("username", "someone")
                        .param("password", "some-password-1234"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void servesRequestsThatAlreadyArrivedOverHttps() throws Exception {
        mockMvc.perform(get("/api/health").secure(true))
                .andExpect(status().isOk());
    }
}
