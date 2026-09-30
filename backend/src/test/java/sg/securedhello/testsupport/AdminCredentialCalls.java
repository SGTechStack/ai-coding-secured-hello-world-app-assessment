package sg.securedhello.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.json.JsonMapper;

/**
 * Drives the admin credential routes (invite, admin-issued reset, unlock) as a signed-in administrator holding a
 * freshly verified factor, as the SPA does. Every token a test reads back is registered with {@link LogOutputGuard},
 * so the suite's canary scan fails the test if that token reaches any log (R-FE-007).
 */
public final class AdminCredentialCalls {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mockMvc;
    private final CsrfSession session;

    public AdminCredentialCalls(MockMvc mockMvc, CsrfSession session) {
        this.mockMvc = mockMvc;
        this.session = session;
    }

    /** Signs {@code admin} in, enrols and verifies a factor, and returns the calls on that session. */
    public static AdminCredentialCalls signedIn(MockMvc mockMvc, TotpFactors factors, Accounts.Account admin)
            throws Exception {
        return new AdminCredentialCalls(mockMvc,
                factors.verified(mockMvc, SignedIn.as(mockMvc, admin), factors.enrol(admin)));
    }

    /** The administrator's session. */
    public CsrfSession session() {
        return session;
    }

    /** {@code POST /api/admin/users} with {@code {username, email, role}}. */
    public ResultActions invite(String username, String email, String role) throws Exception {
        return send("/api/admin/users", JSON.writeValueAsString(Map.of("username", username, "email", email,
                "role", role)));
    }

    /** {@code POST /api/admin/users/{id}/password-reset}. */
    public ResultActions issueReset(UUID id) throws Exception {
        return mockMvc.perform(post("/api/admin/users/" + id + "/password-reset").with(session.inHeader()));
    }

    /** {@code POST /api/admin/users/{id}/unlock} with {@code {reason}}. */
    public ResultActions unlock(UUID id, String reason) throws Exception {
        return send("/api/admin/users/" + id + "/unlock", JSON.writeValueAsString(Map.of("reason", reason)));
    }

    /** {@code POST} of a raw JSON body to {@code path} on the administrator's session. */
    public ResultActions send(String path, String json) throws Exception {
        return mockMvc.perform(post(path).with(session.inHeader()).contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** The {@code token} member of a token response, registered with the canary scan. */
    public static String token(ResultActions issued) throws Exception {
        String token = JSON.readTree(issued.andReturn().getResponse().getContentAsString()).get("token").asString();
        LogOutputGuard.register(token);
        return token;
    }

    /** The {@code userId} member of a token response. */
    public static UUID userId(ResultActions issued) throws Exception {
        return UUID.fromString(JSON.readTree(issued.andReturn().getResponse().getContentAsString()).get("userId")
                .asString());
    }
}
