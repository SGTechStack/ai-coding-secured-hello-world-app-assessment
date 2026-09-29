package sg.securedhello.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.json.JsonMapper;

/**
 * Drives two-step self-registration through the real API, as the SPA does (ADR-032): each request bootstraps its own
 * CSRF token on a fresh anonymous session. Tokens come from {@link CapturedEmails}, never from a log.
 */
public final class Registrations {

    /** A policy-passing password for activations that are not about the password. */
    public static final String PASSWORD = "velvet harbour quietly hums";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mockMvc;
    private final String source;

    /** Registrations from the loopback source, for the shared contexts' raised budgets. */
    public Registrations(MockMvc mockMvc) {
        this(mockMvc, "127.0.0.1");
    }

    /** Registrations from {@code source}, for tests that isolate budgets by source address. */
    public Registrations(MockMvc mockMvc, String source) {
        this.mockMvc = mockMvc;
        this.source = source;
    }

    /** A username no account has: canonical and well-formed. */
    public static String freshUsername() {
        return "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
    }

    /** The canonical email address tests pair with {@code username}. */
    public static String emailFor(String username) {
        return username + "@example.test";
    }

    /** {@code POST /api/register} with {@code {username, email}}. */
    public ResultActions register(String username, String email) throws Exception {
        return send("/api/register", JSON.writeValueAsString(Map.of("username", username, "email", email)));
    }

    /** {@code POST /api/register/activate} with {@code {token, password}}. */
    public ResultActions activate(String token, String password) throws Exception {
        return send("/api/register/activate", JSON.writeValueAsString(Map.of("token", token, "password", password)));
    }

    /** {@code POST} of a raw JSON body to {@code path}, on a fresh token. */
    public ResultActions send(String path, String json) throws Exception {
        CsrfSession session = CsrfSession.bootstrap(mockMvc, source);
        return mockMvc.perform(post(path).with(session.inHeader()).with(request -> {
            request.setRemoteAddr(source);
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
