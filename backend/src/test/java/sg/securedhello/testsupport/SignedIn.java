package sg.securedhello.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.json.JsonMapper;

/**
 * Signs in through the real API, as the SPA does: bootstrap a token, {@code POST /api/login}, then fetch the new token
 * the rotation issued (ADR-040). The result is a {@link CsrfSession} on the signed-in session.
 */
public final class SignedIn {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SignedIn() {
    }

    /** The JSON login body. */
    public static String credentials(String username, String password) {
        return JSON.writeValueAsString(java.util.Map.of("username", username, "password", password));
    }

    /** Posts a login on {@code session} with its token. */
    public static ResultActions login(MockMvc mockMvc, CsrfSession session, String username, String password)
            throws Exception {
        return mockMvc.perform(post("/api/login").with(session.inHeader())
                .contentType(MediaType.APPLICATION_JSON).content(credentials(username, password)));
    }

    /** Signs {@code account} in and returns the signed-in session with its current token. */
    public static CsrfSession as(MockMvc mockMvc, Accounts.Account account) throws Exception {
        MvcResult result = login(mockMvc, CsrfSession.bootstrap(mockMvc), account.username(), account.password())
                .andReturn();
        assertThat(result.getResponse().getStatus()).as("login of a fixture account").isEqualTo(200);
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).as("rotated session cookie").isNotNull();
        return refreshed(mockMvc, cookie);
    }

    /** The token {@code GET /api/csrf} now returns for the session {@code cookie} names. */
    public static CsrfSession refreshed(MockMvc mockMvc, Cookie cookie) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/csrf").cookie(cookie)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return new CsrfSession(cookie, JSON.readTree(result.getResponse().getContentAsString()).get("token")
                .asString());
    }
}
