package com.example.helloworldauth.auth;

import com.example.helloworldauth.user.Role;
import com.example.helloworldauth.user.User;
import com.example.helloworldauth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LogoutControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    @BeforeEach
    void seed() {
        users.deleteAll();
        users.save(new User("alice", "alice@example.com", encoder.encode("correcthorsebattery"), Role.USER));
    }

    /**
     * Builds a server-side session carrying a persisted authenticated
     * SecurityContext, exactly as HttpSessionSecurityContextRepository stores it
     * after a successful login. Requests replaying this session are authenticated
     * without a per-request post-processor, so we can prove logout invalidates it.
     */
    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        UserDetails principal = org.springframework.security.core.userdetails.User
            .withUsername("alice").password("n/a").roles("USER").build();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = new SecurityContextImpl(authentication);
        session.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    @Test
    void logoutInvalidatesSessionAndReplayedCookieIsRejected() throws Exception {
        MockHttpSession session = authenticatedSession();

        // The live session can reach the protected endpoint.
        mockMvc.perform(get("/api/hello").session(session))
            .andExpect(status().isOk());

        // Logout ends the session.
        mockMvc.perform(post("/api/logout").with(csrf()).session(session))
            .andExpect(status().isNoContent());

        // The server-side session is now invalidated.
        assertThat(session.isInvalid()).isTrue();

        // Replaying the SAME (now old) session id with no security context is
        // rejected as unauthenticated.
        MockHttpSession replayed = new MockHttpSession(null, session.getId());
        mockMvc.perform(get("/api/hello").session(replayed))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void logoutWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/logout"))
            .andExpect(status().isForbidden());
    }

    @Test
    void logoutWhenUnauthenticatedIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/logout").with(csrf()))
            .andExpect(status().isUnauthorized());
    }
}
