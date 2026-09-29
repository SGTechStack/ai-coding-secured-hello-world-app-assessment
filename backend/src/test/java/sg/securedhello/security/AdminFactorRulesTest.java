package sg.securedhello.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.security.AuthorizationMatrix.Route;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;

/**
 * The two admin factor rules (ADR-021; ADR-026), driven through the real chain with real sign-ins and verification on
 * the shared clock. The application has no admin mutation yet, so this context adds a probe route under
 * {@code /api/admin/**} with a {@code PUT}, next to the real read routes, and enumerates the admin rows of its bound
 * matrix. It starts its own context for that reason.
 */
@Import(AdminFactorRulesTest.AdminProbe.class)
@TestPropertySource(properties = {
        "app.security.authorization.roles.ADMIN[0].method=GET",
        "app.security.authorization.roles.ADMIN[0].path=/api/profile",
        "app.security.authorization.roles.ADMIN[1].method=POST",
        "app.security.authorization.roles.ADMIN[1].path=/api/mfa/totp/enrolment",
        "app.security.authorization.roles.ADMIN[2].method=POST",
        "app.security.authorization.roles.ADMIN[2].path=/api/mfa/totp/verification",
        "app.security.authorization.roles.ADMIN[3].method=GET",
        "app.security.authorization.roles.ADMIN[3].path=/api/admin/users",
        "app.security.authorization.roles.ADMIN[4].method=GET",
        "app.security.authorization.roles.ADMIN[4].path=/api/admin/users/{id}",
        "app.security.authorization.roles.ADMIN[5].method=PUT",
        "app.security.authorization.roles.ADMIN[5].path=/api/admin/probe/{id}"})
class AdminFactorRulesTest extends CtxDefaultTest {

    /** A mutation on the admin surface, standing in for the admin mutations that come later. */
    @RestController
    static class AdminProbe {

        @PutMapping("/api/admin/probe/{id}")
        String mutate() {
            return "mutated";
        }

        @GetMapping("/api/admin/probe/{id}")
        String unmatched() {
            return "unreachable";
        }
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private AuthorizationMatrix matrix;

    @Autowired
    private SessionRepository<? extends Session> sessions;

    private Accounts accounts;
    private TotpFactors factors;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        factors = new TotpFactors(jdbc, cipher, clock);
    }

    private ResultActions call(Route route, UUID id, CsrfSession session) throws Exception {
        return mockMvc.perform(request(route.method(), route.path().replace("{id}", id.toString()))
                .with(session.inHeader()).contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private List<Route> adminRows() {
        List<Route> rows = new ArrayList<>(matrix.rolesByRoute().keySet().stream()
                .filter(route -> route.path().startsWith("/api/admin/")).toList());
        assertThat(rows).extracting(Route::method).contains(HttpMethod.GET, HttpMethod.PUT);
        return rows;
    }

    /** Keeps {@code session} inside its idle window while the clock moves {@code total} forward. */
    private void age(CsrfSession session, Duration total) throws Exception {
        Duration step = Duration.ofMinutes(6);
        for (Duration moved = Duration.ZERO; moved.compareTo(total) < 0; moved = moved.plus(step)) {
            clock.advance(step.compareTo(total.minus(moved)) < 0 ? step : total.minus(moved));
            mockMvc.perform(get("/api/profile").cookie(session.cookie())).andExpect(status().isOk());
        }
    }

    @Test
    @Proves("T-MFA-002")
    void everyAdminRowRefusesAMissingFactorReadsAcceptAnyAgeAndMutationsOnlyTenMinutes() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        UUID target = accounts.user().id();
        byte[] secret = factors.enrol(admin);
        CsrfSession unverified = SignedIn.as(mockMvc, admin);
        for (Route route : adminRows()) {
            call(route, target, unverified).andExpect(problem(ErrorCode.MISSING_FACTOR))
                    .andExpect(jsonPath("$.reason").value("MISSING"));
        }

        CsrfSession verified = factors.verified(mockMvc, unverified, secret);
        age(verified, Duration.ofMinutes(9));
        for (Route route : adminRows()) {
            call(route, target, verified).andExpect(status().isOk());
        }

        age(verified, Duration.ofMinutes(12).minus(Duration.ofMinutes(9)));
        for (Route route : adminRows()) {
            if (HttpMethod.GET.equals(route.method())) {
                call(route, target, verified).andExpect(status().isOk());
            } else {
                call(route, target, verified).andExpect(problem(ErrorCode.MISSING_FACTOR))
                        .andExpect(jsonPath("$.reason").value("EXPIRED"));
            }
        }

        // Renewing the factor re-opens the mutations, on the same session.
        CsrfSession renewed = factors.verified(mockMvc, verified, secret);
        for (Route route : adminRows()) {
            call(route, target, renewed).andExpect(status().isOk());
        }
    }

    @Test
    @Proves("T-MFA-019")
    void eachFactorFailureMapsToItsStatusCodeAndMembersInTheSharedEnvelope() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        UUID target = accounts.user().id();
        CsrfSession session = SignedIn.as(mockMvc, admin);

        // No TOTP factor on an admin route.
        mockMvc.perform(get("/api/admin/users").cookie(session.cookie()))
                .andExpect(problem(ErrorCode.MISSING_FACTOR))
                .andExpect(jsonPath("$.factor").value("TOTP"))
                .andExpect(jsonPath("$.reason").value("MISSING"));
        // A wrong code at verification.
        String right = factors.code(secret);
        TotpFactors.verify(mockMvc, session, right.equals("000000") ? "000001" : "000000")
                .andExpect(problem(ErrorCode.INVALID_FACTOR));
        // Provisioning while enrolled.
        mockMvc.perform(post("/api/mfa/totp/enrolment").with(session.inHeader()))
                .andExpect(problem(ErrorCode.FACTOR_ALREADY_ENROLLED));
        // A mutation with a factor older than 10 minutes.
        CsrfSession verified = factors.verified(mockMvc, session, secret);
        age(verified, Duration.ofMinutes(10));
        mockMvc.perform(put("/api/admin/probe/" + target).with(verified.inHeader()))
                .andExpect(problem(ErrorCode.MISSING_FACTOR))
                .andExpect(jsonPath("$.factor").value("TOTP"))
                .andExpect(jsonPath("$.reason").value("EXPIRED"));
        // An unenrolled admin.
        CsrfSession unenrolled = SignedIn.as(mockMvc, accounts.withRole("ADMIN"));
        mockMvc.perform(get("/api/admin/users").cookie(unenrolled.cookie()))
                .andExpect(problem(ErrorCode.FACTOR_ENROLMENT_REQUIRED));
        mockMvc.perform(put("/api/admin/probe/" + target).with(unenrolled.inHeader()))
                .andExpect(problem(ErrorCode.FACTOR_ENROLMENT_REQUIRED));
    }

    @Test
    void aUserGetsAccessDeniedOnTheMutationRuleToo() throws Exception {
        Account user = accounts.user();
        mockMvc.perform(put("/api/admin/probe/" + user.id()).with(SignedIn.as(mockMvc, user).inHeader()))
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
        mockMvc.perform(put("/api/admin/probe/" + user.id()).with(CsrfSession.bootstrap(mockMvc).inHeader()))
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }

    @Test
    void aMethodWithoutAMatrixRowOnTheAdminSurfaceIsDenied() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        CsrfSession verified = factors.verified(mockMvc, SignedIn.as(mockMvc, admin), factors.enrol(admin));

        // GET /api/admin/probe/{id} has a handler but no matrix row: the terminal denyAll refuses it.
        mockMvc.perform(get("/api/admin/probe/" + admin.id()).cookie(verified.cookie()))
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @Proves("T-MFA-005")
    void aPlainAuthorityCarryingTheFactorsNameIsDeniedOnTheMutationRule() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        CsrfSession verified = factors.verified(mockMvc, SignedIn.as(mockMvc, admin), factors.enrol(admin));
        mockMvc.perform(put("/api/admin/probe/" + admin.id()).with(verified.inHeader())).andExpect(status().isOk());

        TotpFactors.degrade(sessions, SessionRows.idOf(verified.cookie().getValue()));

        mockMvc.perform(put("/api/admin/probe/" + admin.id()).with(verified.inHeader()))
                .andExpect(problem(ErrorCode.MISSING_FACTOR))
                .andExpect(jsonPath("$.reason").value("EXPIRED"));
    }
}
