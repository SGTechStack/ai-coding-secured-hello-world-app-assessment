package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authorization.AuthorizationManagerFactory;
import org.springframework.security.authorization.DefaultAuthorizationManagerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.security.AuthorizationMatrix;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The admin read surface and the self-read's factor state (PRD Story 8; ADR-021; ADR-026): a verified admin reads the
 * user list and one user, a user is refused every admin route with 403 and never a factor answer, and
 * {@code factors} reports each state.
 */
class AdminReadSurfaceTest extends CtxDefaultTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private AuthorizationMatrix matrix;

    @Autowired
    private ApplicationContext context;

    private Accounts accounts;
    private TotpFactors factors;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        factors = new TotpFactors(jdbc, cipher, clock);
    }

    /** A signed-in admin holding a freshly verified factor. */
    private CsrfSession verifiedAdmin(Account admin) throws Exception {
        return factors.verified(mockMvc, SignedIn.as(mockMvc, admin), factors.enrol(admin));
    }

    /** Every admin-surface row of the bound matrix, with {@code {id}} filled by {@code id}. */
    private List<Map.Entry<String, String>> adminRoutes(UUID id) {
        return matrix.rolesByRoute().keySet().stream()
                .filter(route -> route.path().startsWith("/api/admin/"))
                .map(route -> Map.entry(route.method().name(), route.path().replace("{id}", id.toString())))
                .toList();
    }

    @Test
    @Proves("T-ADM-032")
    void aVerifiedAdminReadsEveryUserWithThePrdFieldsAndNoSecret() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        Account user = accounts.user();
        Account disabled = accounts.disabled();
        CsrfSession session = verifiedAdmin(admin);

        String body;
        try (AuditCapture audit = AuditCapture.start()) {
            body = mockMvc.perform(get("/api/admin/users").cookie(session.cookie()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

            int listed = JSON.readTree(body).size();
            assertThat(audit.withMessage("Administrator listed users.")).singleElement().satisfies(row ->
                    assertThat(row).containsEntry("user.id", admin.id().toString())
                            .containsEntry("user.target.count", listed));
        }
        JsonNode users = JSON.readTree(body);
        for (Account account : List.of(admin, user, disabled)) {
            Map<String, Object> stored = jdbc.queryForMap(
                    "SELECT username, email, role, enabled, created_at FROM users WHERE id = ?", account.id());
            JsonNode entry = entryFor(users, account.id());
            assertThat(entry.get("username").asString()).isEqualTo(stored.get("USERNAME"));
            assertThat(entry.get("email").asString()).isEqualTo(stored.get("EMAIL"));
            assertThat(entry.get("role").asString()).isEqualTo(stored.get("ROLE"));
            assertThat(entry.get("enabled").asBoolean()).isEqualTo(stored.get("ENABLED"));
            assertThat(Instant.parse(entry.get("createdAt").asString()))
                    .isEqualTo(((OffsetDateTime) stored.get("CREATED_AT")).toInstant());
            assertThat(entry.propertyNames()).containsExactlyInAnyOrder("id", "username", "email", "role",
                    "enabled", "createdAt");
        }
        assertThat(entryFor(users, disabled.id()).get("enabled").asBoolean()).isFalse();
        assertNoSecret(body, admin);
    }

    private void assertNoSecret(String body, Account account) {
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, account.id());
        assertThat(body).doesNotContain("passwordHash", "password", "totpSecret", "totpKey", "emailHmac", hash);
    }

    private static JsonNode entryFor(JsonNode users, UUID id) {
        for (JsonNode entry : users) {
            if (entry.get("id").asString().equals(id.toString())) {
                return entry;
            }
        }
        throw new AssertionError("no entry for " + id);
    }

    @Test
    void aVerifiedAdminReadsOneUserAndAnUnknownIdIsDenied() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        Account target = accounts.user();
        CsrfSession session = verifiedAdmin(admin);

        try (AuditCapture audit = AuditCapture.start()) {
            String body = mockMvc.perform(get("/api/admin/users/" + target.id()).cookie(session.cookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(target.username()))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andReturn().getResponse().getContentAsString();
            assertNoSecret(body, target);
            assertThat(audit.withMessage("Administrator read a user.")).singleElement().satisfies(row ->
                    assertThat(row).containsEntry("user.id", admin.id().toString())
                            .containsEntry("user.target.id", target.id().toString()));
        }
        mockMvc.perform(get("/api/admin/users/" + UUID.randomUUID()).cookie(session.cookie()))
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @Proves({"T-ADM-013", "T-ADM-009"})
    void aUserIsRefusedEveryAdminRouteWithAccessDeniedAndStillReachesItsOwnRoutes() throws Exception {
        Account user = accounts.user();
        Account other = accounts.user();
        CsrfSession session = SignedIn.as(mockMvc, user);
        List<Map.Entry<String, String>> routes = adminRoutes(user.id());
        assertThat(routes).as("the bound matrix has admin routes").isNotEmpty();

        for (UUID id : List.of(user.id(), other.id())) {
            for (Map.Entry<String, String> route : adminRoutes(id)) {
                mockMvc.perform(request(HttpMethod.valueOf(route.getKey()), route.getValue())
                                .with(session.inHeader()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(problem(ErrorCode.ACCESS_DENIED));
            }
        }
        mockMvc.perform(get("/api/hello").cookie(session.cookie())).andExpect(status().isOk())
                .andExpect(content().json("{\"message\":\"Hello, " + user.username() + "\"}", true));
        mockMvc.perform(get("/api/profile").cookie(session.cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.id().toString()))
                .andExpect(jsonPath("$.username").value(user.username()));
    }

    @Test
    void anAdminWithoutTheFactorGetsMissingFactorAndAnUnenrolledOneGetsEnrolmentRequired() throws Exception {
        Account enrolled = accounts.withRole("ADMIN");
        factors.enrol(enrolled);
        Account unenrolled = accounts.withRole("ADMIN");

        mockMvc.perform(get("/api/admin/users").cookie(SignedIn.as(mockMvc, enrolled).cookie()))
                .andExpect(problem(ErrorCode.MISSING_FACTOR))
                .andExpect(jsonPath("$.factor").value("TOTP"))
                .andExpect(jsonPath("$.reason").value("MISSING"));
        mockMvc.perform(get("/api/admin/users").cookie(SignedIn.as(mockMvc, unenrolled).cookie()))
                .andExpect(problem(ErrorCode.FACTOR_ENROLMENT_REQUIRED));
        mockMvc.perform(get("/api/admin/users")).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }

    @Test
    void theFactorsObjectReflectsEachState() throws Exception {
        CsrfSession user = SignedIn.as(mockMvc, accounts.user());
        assertThat(factorsOf(user)).isEqualTo(Map.of("held", false, "required", false, "enrolled", false,
                "rebindRequired", false));

        Account admin = accounts.withRole("ADMIN");
        MvcResult login = SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), admin.username(), admin.password())
                .andExpect(jsonPath("$.factors.required").value(true))
                .andExpect(jsonPath("$.factors.enrolled").value(false)).andReturn();
        CsrfSession unenrolled = SignedIn.refreshed(mockMvc, login.getResponse().getCookie("SESSION"));
        assertThat(factorsOf(unenrolled)).isEqualTo(Map.of("held", false, "required", true, "enrolled", false,
                "rebindRequired", false));

        byte[] secret = factors.enrol(admin);
        assertThat(factorsOf(unenrolled)).isEqualTo(Map.of("held", false, "required", true, "enrolled", true,
                "rebindRequired", false));

        CsrfSession verified = factors.verified(mockMvc, unenrolled, secret);
        assertThat(factorsOf(verified)).isEqualTo(Map.of("held", true, "required", true, "enrolled", true,
                "rebindRequired", false));

        jdbc.update("UPDATE totp_user_details SET factor_disabled_at = ? WHERE user_id = ?",
                Timestamp.from(clock.instant()), admin.id());
        assertThat(factorsOf(verified)).containsEntry("rebindRequired", true);
    }

    private Map<String, Object> factorsOf(CsrfSession session) throws Exception {
        String body = mockMvc.perform(get("/api/profile").cookie(session.cookie())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode factorsNode = JSON.readTree(body).get("factors");
        assertThat(factorsNode.propertyNames()).containsExactlyInAnyOrder("held", "required", "enrolled",
                "rebindRequired");
        return Map.of("held", factorsNode.get("held").asBoolean(), "required", factorsNode.get("required").asBoolean(),
                "enrolled", factorsNode.get("enrolled").asBoolean(),
                "rebindRequired", factorsNode.get("rebindRequired").asBoolean());
    }

    @Test
    @Proves("T-ADM-017")
    void thereIsNoRoleHierarchyBean() {
        assertThat(context.getBeanNamesForType(RoleHierarchy.class)).isEmpty();
    }

    @Test
    @Proves("T-MFA-018")
    void noAuthorizationFactoryCarriesAdditionalAuthorizationAndNoRuleIsFactorFirst() throws Exception {
        for (AuthorizationManagerFactory<?> factory : context.getBeansOfType(AuthorizationManagerFactory.class)
                .values()) {
            if (factory instanceof DefaultAuthorizationManagerFactory<?> defaults) {
                assertThat(ReflectionTestUtils.getField(defaults, "additionalAuthorization"))
                        .as("additional authorization on %s", factory).isNull();
            }
        }
        // Behaviourally: the role rules run before the factor rule, so no USER or anonymous caller, and no USER
        // route, ever sees a factor answer (ADR-026).
        CsrfSession user = SignedIn.as(mockMvc, accounts.user());
        mockMvc.perform(get("/api/admin/users").cookie(user.cookie())).andExpect(problem(ErrorCode.ACCESS_DENIED));
        mockMvc.perform(get("/api/hello").cookie(user.cookie())).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users")).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        mockMvc.perform(get("/api/no-such-route")).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }
}
