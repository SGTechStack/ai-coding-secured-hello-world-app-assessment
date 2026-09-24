package com.sgtechstack.helloworldauthapp.auth;

import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetTokenRepository;
import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The container's idle timeout only measures the gap between requests, so a
 * session exercised periodically is renewed forever — meaning a stolen cookie
 * stays valid for as long as an attacker keeps using it. There was no point at
 * which a session simply ended; this covers the cap that adds one.
 *
 * <p>Sets the absolute lifetime to zero so any session is immediately past it.
 * Testing the real 8-hour value would require either waiting or a clock
 * abstraction that would exist only for the test; zero exercises the same
 * branch.
 */
@SpringBootTest(properties = "app.security.session-absolute-timeout=0s")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AbsoluteSessionTimeoutTest {

    private static final String USERNAME = "absolutetimeoutuser";
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User(
                USERNAME, "absolutetimeoutuser@example.com", passwordEncoder.encode(PASSWORD), Role.USER, true));
    }

    @Test
    void rejectsASessionThatHasExceededItsAbsoluteLifetime() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", USERNAME)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);

        assertThat(session).isNotNull();

        // The login response itself succeeded; it is the next use of the
        // session that must be refused, once the cap is evaluated.
        mockMvc.perform(get("/api/hello").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stillServesWhitelistedRoutesAfterExpiry() throws Exception {
        // Expiry clears the context and lets the request continue as anonymous
        // rather than short-circuiting, so public routes keep working.
        mockMvc.perform(get("/api/health").session(new MockHttpSession()))
                .andExpect(status().isOk());
    }
}
