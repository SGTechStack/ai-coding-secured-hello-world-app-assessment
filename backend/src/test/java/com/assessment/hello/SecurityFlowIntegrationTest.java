package com.assessment.hello;

import com.assessment.hello.domain.Role;
import com.assessment.hello.domain.User;
import com.assessment.hello.dto.LoginRequest;
import com.assessment.hello.dto.PasswordResetConfirmDto;
import com.assessment.hello.dto.PasswordResetRequestDto;
import com.assessment.hello.dto.RegisterRequest;
import com.assessment.hello.repository.UserRepository;
import com.assessment.hello.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcConfigurer;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SecurityFlowIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @SpyBean
    private EmailService emailService;

    private MockMvc mvc;

    private static final String USER_PW = "SuperSecret123!";

    @BeforeEach
    void setup() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void registerUser(String username, String email) throws Exception {
        RegisterRequest req = new RegisterRequest(username, email, USER_PW);
        mvc.perform(post("/api/auth/register")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    // ---- Story 2: Login ----

    @Test
    void login_success_setsSession() throws Exception {
        registerUser("alice", "alice@example.com");
        mvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("alice", USER_PW))))
                .andExpect(status().isOk());
    }

    @Test
    void login_wrongPassword_and_unknownUser_giveIdenticalGenericError() throws Exception {
        registerUser("bob", "bob@example.com");

        MvcResult wrongPw = mvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("bob", "WrongPassword1!"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult unknown = mvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("nobody", "WrongPassword1!"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(wrongPw.getResponse().getContentAsString())
                .isEqualTo(unknown.getResponse().getContentAsString());
    }

    @Test
    void login_lockedAccount_isRejectedEvenWithCorrectPassword() throws Exception {
        registerUser("carol", "carol@example.com");
        // Manually lock the account.
        User carol = userRepository.findByUsername("carol").orElseThrow();
        carol.setLockedUntil(java.time.Instant.now().plusSeconds(600));
        userRepository.save(carol);

        mvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("carol", USER_PW))))
                .andExpect(status().isUnauthorized());
    }

    // ---- Story 3: Lockout ----

    @Test
    void lockout_triggersAfterMaxAttempts() throws Exception {
        registerUser("dave", "dave@example.com");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login")
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new LoginRequest("dave", "Wrong" + i + "aaaaaaa!"))))
                    .andExpect(status().isUnauthorized());
        }
        User dave = userRepository.findByUsername("dave").orElseThrow();
        assertThat(dave.isLocked()).isTrue();

        // Even the correct password is now rejected.
        mvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("dave", USER_PW))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lockout_resetsCounterAfterSuccessfulLogin() throws Exception {
        registerUser("erin", "erin@example.com");
        // A few failures, but below the threshold.
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/login")
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new LoginRequest("erin", "Wrong" + i + "aaaaaaa!"))))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(userRepository.findByUsername("erin").orElseThrow().getFailedLoginAttempts()).isEqualTo(3);

        // Correct login resets the counter.
        mvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("erin", USER_PW))))
                .andExpect(status().isOk());
        assertThat(userRepository.findByUsername("erin").orElseThrow().getFailedLoginAttempts()).isZero();
    }

    // ---- Story 4: Logout ----

    @Test
    void logout_invalidatesSession_reusedCookieRejected() throws Exception {
        registerUser("frank", "frank@example.com");

        MvcResult loginResult = mvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("frank", USER_PW))))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // Authenticated call works with the session.
        mvc.perform(get("/api/hello").session(session))
                .andExpect(status().isOk());

        // Logout.
        mvc.perform(post("/api/auth/logout")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .session(session))
                .andExpect(status().isOk());

        // Reusing the same (now invalidated) session is unauthorized.
        mvc.perform(get("/api/hello").session(session))
                .andExpect(status().isUnauthorized());
    }

    // ---- Story 5: Protected content ----

    @Test
    void hello_withoutSession_isUnauthorized() throws Exception {
        mvc.perform(get("/api/hello"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hello_withSession_greetsByUsername() throws Exception {
        registerUser("grace", "grace@example.com");
        MvcResult loginResult = mvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("grace", USER_PW))))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mvc.perform(get("/api/hello").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hello, grace")));
    }

    // ---- Stories 6-7: Password reset ----

    @Test
    void passwordReset_singleUse_expiry_and_sessionInvalidation() throws Exception {
        registerUser("heidi", "heidi@example.com");

        // Log in first so we have a session to invalidate.
        MvcResult loginResult = mvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("heidi", USER_PW))))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession oldSession = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Request a reset; capture the raw token passed to the (stubbed) email service.
        doNothing().when(emailService).sendPasswordResetEmail(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        mvc.perform(post("/api/auth/password-reset/request")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new PasswordResetRequestDto("heidi@example.com"))))
                .andExpect(status().isOk());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(org.mockito.ArgumentMatchers.eq("heidi@example.com"),
                tokenCaptor.capture());
        String rawToken = tokenCaptor.getValue();

        String newPw = "BrandNewPass456!";
        // Confirm reset with the valid token.
        mvc.perform(post("/api/auth/password-reset/confirm")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new PasswordResetConfirmDto(rawToken, newPw))))
                .andExpect(status().isOk());

        // Single-use: reusing the same token fails.
        mvc.perform(post("/api/auth/password-reset/confirm")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new PasswordResetConfirmDto(rawToken, "AnotherPass789!"))))
                .andExpect(status().isBadRequest());

        // Existing session invalidated: the old session can no longer hit protected content.
        mvc.perform(get("/api/hello").session(oldSession))
                .andExpect(status().isUnauthorized());

        // New password works.
        mvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("heidi", newPw))))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetRequest_unknownEmail_stillGenericSuccess() throws Exception {
        mvc.perform(post("/api/auth/password-reset/request")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new PasswordResetRequestDto("ghost@example.com"))))
                .andExpect(status().isOk());
    }

    // ---- Story 8: Role enforcement ----

    @Test
    void adminEndpoint_asUser_isForbidden() throws Exception {
        registerUser("ivan", "ivan@example.com");
        MvcResult loginResult = mvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("ivan", USER_PW))))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoint_asAdmin_listsUsers() throws Exception {
        MockHttpSession adminSession = loginAsSeededAdmin();
        mvc.perform(get("/api/admin/users").session(adminSession))
                .andExpect(status().isOk());
    }

    // ---- Stories 9-11: Admin self-action guards ----

    @Test
    void admin_cannotDisableThemselves() throws Exception {
        MockHttpSession adminSession = loginAsSeededAdmin();
        UUID adminId = userRepository.findByUsername("admin").orElseThrow().getId();

        mvc.perform(put("/api/admin/users/" + adminId + "/status")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("enabled", "false")
                        .session(adminSession))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_cannotDeleteThemselves() throws Exception {
        MockHttpSession adminSession = loginAsSeededAdmin();
        UUID adminId = userRepository.findByUsername("admin").orElseThrow().getId();

        mvc.perform(delete("/api/admin/users/" + adminId)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .session(adminSession))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_cannotDemoteThemselves() throws Exception {
        MockHttpSession adminSession = loginAsSeededAdmin();
        UUID adminId = userRepository.findByUsername("admin").orElseThrow().getId();

        mvc.perform(put("/api/admin/users/" + adminId + "/role")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content("{\"role\":\"USER\"}")
                        .session(adminSession))
                .andExpect(status().isBadRequest());
    }

    private MockHttpSession loginAsSeededAdmin() throws Exception {
        MvcResult loginResult = mvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "AdminPassword123!"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }
}
