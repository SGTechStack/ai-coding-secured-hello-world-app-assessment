package sg.securedhello.security.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.ObjectStreamClass;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MvcResult;

import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;

/**
 * The sign-in's {@link SourceKeyAuthenticationDetails}: set by the JSON login converter from the key the early filter
 * already derived, stored with the signed-in session, and naming no address (ADR-015; ADR-020).
 */
class SourceKeyDetailsTest extends CtxDefaultTest {

    private static final String SOURCE = "203.0.113.7";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SessionRepository<? extends Session> sessions;

    @Test
    @Proves({"T-RL-005", "T-AUD-040"})
    void theSignedInAuthenticationCarriesTheConvertersSourceKeyDetailsThroughTheJdbcSession() throws Exception {
        Account account = new Accounts(jdbc, passwordEncoder).user();
        MvcResult result = SignedIn.loginFrom(mockMvc, SOURCE, account.username(), account.password()).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).isNotNull();

        // Read back through the session store's own deserialiser, allowlist included.
        Session stored = sessions.findById(SessionRows.idOf(cookie.getValue()));
        SecurityContext context = stored.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getDetails()).isInstanceOfSatisfying(
                SourceKeyAuthenticationDetails.class, details -> assertThat(details.sourceKey())
                        .isEqualTo(SourceKeyResolver.derive(SOURCE, 64)));
    }

    @Test
    @Proves("T-RL-005")
    void failedLoginRowsFromTwoSourcesCarryDistinctSourceHashes() throws Exception {
        String username = Accounts.unknownUsername();
        try (AuditCapture audit = AuditCapture.start()) {
            SignedIn.loginFrom(mockMvc, "203.0.113.8", username, "not-the-password");
            SignedIn.loginFrom(mockMvc, "2001:db8:1::1", username, "not-the-password");
            List<Map<String, Object>> rows = audit.withMessage("Login failed.");
            assertThat(rows).hasSize(2).allSatisfy(row -> assertThat(row.get("source.ip_hash")).isNotNull());
            assertThat(rows.get(0).get("source.ip_hash")).isNotEqualTo(rows.get(1).get("source.ip_hash"));
        }
    }

    @Test
    @Proves("T-RL-029")
    void theDetailsSourceUsesTheEarlyFiltersKeyAndNeverCallsTheResolver() {
        SourceKeyResolver resolver = mock(SourceKeyResolver.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        SourceKey derived = new SourceKey("4:cb007107");
        request.setAttribute(SourceKeyResolver.REQUEST_ATTRIBUTE, derived);

        SourceKeyAuthenticationDetails details = new SourceKeyAuthenticationDetailsSource(resolver)
                .buildDetails(request);

        assertThat(details.sourceKey()).isSameAs(derived);
        verifyNoInteractions(resolver);
    }

    @Test
    @Proves("T-AUD-040")
    void theDetailsDeclareASerialVersionUidAndTheirTextNamesNoSource() {
        assertThat(ObjectStreamClass.lookup(SourceKeyAuthenticationDetails.class).getSerialVersionUID())
                .isEqualTo(1L);
        SourceKey key = SourceKeyResolver.derive(SOURCE, 64);
        String text = new SourceKeyAuthenticationDetails(key).toString();
        assertThat(text).doesNotContain(SOURCE).doesNotContain(key.value()).doesNotContain("cb007107");
    }
}
