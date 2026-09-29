package sg.securedhello.testsupport;

import java.util.Map;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.json.JsonMapper;

/**
 * Drives password reset through the real API, as the SPA does: each request bootstraps its own CSRF token on a fresh
 * anonymous session. Tokens come from {@link CapturedEmails}, never from a log.
 */
public final class PasswordResets {

    /** A policy-passing password no fixture account has, for redemptions that are not about the password. */
    public static final String NEW_PASSWORD = "copper lantern drifts westward";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Registrations requests;

    /** Resets from the loopback source, for the shared contexts' raised budgets. */
    public PasswordResets(MockMvc mockMvc) {
        this.requests = new Registrations(mockMvc);
    }

    /** Resets from {@code source}, for tests that isolate budgets by source address. */
    public PasswordResets(MockMvc mockMvc, String source) {
        this.requests = new Registrations(mockMvc, source);
    }

    /** {@code POST /api/password-reset/request} with {@code {email}}. */
    public ResultActions request(String email) throws Exception {
        return requests.send("/api/password-reset/request", JSON.writeValueAsString(Map.of("email", email)));
    }

    /** {@code POST /api/password-reset/confirm} with {@code {token, password}}. */
    public ResultActions confirm(String token, String password) throws Exception {
        return requests.send("/api/password-reset/confirm",
                JSON.writeValueAsString(Map.of("token", token, "password", password)));
    }

    /** The canonical email address a fixture account from {@link Accounts} has. */
    public static String emailOf(Accounts.Account account) {
        return account.username() + "@example.test";
    }
}
