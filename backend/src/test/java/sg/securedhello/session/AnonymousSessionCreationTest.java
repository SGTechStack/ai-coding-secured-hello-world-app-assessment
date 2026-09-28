package sg.securedhello.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.servlet.http.Cookie;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionRows;

/**
 * Only {@code GET /api/csrf} creates an anonymous session (ADR-040). Every other method and route, including the
 * paths the framework would otherwise save a request or a CSRF token for, creates none.
 */
class AnonymousSessionCreationTest extends CtxDefaultTest {

    private static final String CSRF_ROUTE = "/api/csrf";
    private static final List<HttpMethod> METHODS = List.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST,
            HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.OPTIONS);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private SessionRows sessions;

    @BeforeEach
    void setUp() {
        sessions = new SessionRows(jdbc);
    }

    @Test
    @Proves("T-SES-026")
    void noRequestButTheTokenFetchCreatesASession() throws Exception {
        List<Cookie> cookies = new ArrayList<>();
        cookies.add(null);
        cookies.add(new Cookie("SESSION", Base64.getEncoder()
                .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8))));
        cookies.add(expiredSessionCookie());

        Set<String> before = sessions.ids();
        for (Cookie cookie : cookies) {
            for (String path : paths()) {
                for (HttpMethod method : METHODS) {
                    if (method == HttpMethod.GET && path.equals(CSRF_ROUTE)) {
                        continue;
                    }
                    send(method, path, cookie);
                    assertThat(sessions.ids()).as("%s %s with cookie %s", method, path, describe(cookie))
                            .isSubsetOf(before);
                }
            }
        }

        Set<String> beforeFetch = sessions.ids();
        mockMvc.perform(get(CSRF_ROUTE)).andExpect(status().isOk());
        Set<String> created = sessions.ids();
        created.removeAll(beforeFetch);
        assertThat(created).as("rows GET %s creates", CSRF_ROUTE).hasSize(1);
    }

    /** Every mapped route, one unmapped path, the admin surface and the actuator. */
    private Set<String> paths() {
        Set<String> paths = new TreeSet<>();
        handlerMapping.getHandlerMethods().keySet().forEach(info -> info.getPatternValues()
                .forEach(pattern -> paths.add(pattern.replaceAll("\\{[^}]*}", UUID.randomUUID().toString()))));
        assertThat(paths).as("mapped routes").contains(CSRF_ROUTE);
        Stream.of("/api/no-such-route", "/api/admin/users", "/actuator/health", "/actuator/env").forEach(paths::add);
        return paths;
    }

    private void send(HttpMethod method, String path, @Nullable Cookie cookie) throws Exception {
        var builder = request(method, path);
        if (cookie != null) {
            builder.cookie(cookie);
        }
        mockMvc.perform(builder);
    }

    /** A cookie for a session that existed and has expired: fetched, then aged in the store. */
    private Cookie expiredSessionCookie() throws Exception {
        Cookie cookie = CsrfSession.bootstrap(mockMvc).cookie();
        sessions.expire(SessionRows.idOf(cookie.getValue()));
        return cookie;
    }

    private static String describe(@Nullable Cookie cookie) {
        return cookie == null ? "none" : cookie.getValue();
    }
}
