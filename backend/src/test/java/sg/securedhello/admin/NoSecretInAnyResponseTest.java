package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.LogOutputGuard;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;

/**
 * No response of any endpoint, success or error envelope, carries a credential, a factor secret or a tombstone HMAC,
 * by field name or by value (PRD Story 8; ADR-050). The sweep is driven by the handler mappings, so a route added later
 * is swept too, as an anonymous caller, a user and a verified administrator.
 */
class NoSecretInAnyResponseTest extends CtxDefaultTest {

    private static final Set<String> SECRET_FIELD_NAMES = Set.of("passwordHash", "totpSecret", "emailHmac");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private final List<String> forbiddenValues = new ArrayList<>();

    private Accounts accounts;

    /** A fresh {@code USER} whose stored hash joins the forbidden values; the target of any {@code {id}} route. */
    private Account target() {
        Account target = accounts.user();
        forbiddenValues.add(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class,
                target.id()));
        return target;
    }

    @Test
    @Proves("T-ADM-008")
    void noEndpointReturnsAHashAFactorSecretOrATombstoneHmacToAnyCaller() throws Exception {
        accounts = new Accounts(jdbc, passwordEncoder);
        TotpFactors factors = new TotpFactors(jdbc, cipher, clock);
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        forbiddenValues.add(new Base32().encodeAsString(secret).replace("=", ""));
        forbiddenValues.add(Base64.getEncoder().encodeToString(secret));
        forbiddenValues.add(HexFormat.of().formatHex(secret));
        forbiddenValues.add(Base64.getEncoder().encodeToString(jdbc.queryForObject(
                "SELECT totp_key FROM totp_user_details WHERE user_id = ?", byte[].class, admin.id())));
        forbiddenValues.add(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class,
                admin.id()));
        CsrfSession adminSession = factors.verified(mockMvc, SignedIn.as(mockMvc, admin), secret);
        Account user = target();
        CsrfSession userSession = SignedIn.as(mockMvc, user);

        // A tombstone whose HMAC must never surface, made through the real delete.
        Account deleted = target();
        sweepOne(adminSession, HttpMethod.DELETE, "/api/admin/users/" + deleted.id(), "");
        forbiddenValues.add(jdbc.queryForObject("SELECT email_hmac FROM deleted_users WHERE user_id = ?",
                String.class, deleted.id()));

        List<String> bodies = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, ?> handler : handlerMapping.getHandlerMethods().entrySet()) {
            for (String pattern : handler.getKey().getPatternValues()) {
                if (!pattern.startsWith("/api/") || pattern.equals("/api/logout")) {
                    continue;
                }
                for (RequestMethod method : handler.getKey().getMethodsCondition().getMethods()) {
                    for (CsrfSession caller : List.of(CsrfSession.bootstrap(mockMvc), userSession, adminSession)) {
                        String path = pattern.replaceAll("\\{[^}]+}", target().id().toString());
                        bodies.add(sweepOne(caller, method.asHttpMethod(), path, "{}"));
                    }
                }
            }
        }
        // The successful reads, whose bodies carry the most account data.
        bodies.add(sweepOne(adminSession, HttpMethod.GET, "/api/admin/users", ""));
        bodies.add(sweepOne(adminSession, HttpMethod.GET, "/api/admin/users/" + user.id(), ""));
        bodies.add(sweepOne(userSession, HttpMethod.GET, "/api/profile", ""));
        bodies.add(sweepOne(adminSession, HttpMethod.GET, "/api/profile", ""));
        bodies.add(sweepOne(adminSession, HttpMethod.POST, "/api/logout", ""));

        assertThat(bodies).hasSizeGreaterThan(60);
        assertThat(forbiddenValues).doesNotContainNull();
        for (String body : bodies) {
            assertThat(body).doesNotContain(SECRET_FIELD_NAMES).doesNotContain(forbiddenValues);
        }
    }

    private String sweepOne(CsrfSession caller, HttpMethod method, String path, String json) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, path).with(caller.inHeader());
        if (!json.isEmpty()) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        String body = mockMvc.perform(builder).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        // An issued token (not the CSRF bootstrap) is a secret too: the suite's canary scan keeps it out of every log.
        if (body.contains("\"token\"") && !body.contains("\"headerName\"")) {
            LogOutputGuard.register(body.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1"));
        }
        return body;
    }

}
