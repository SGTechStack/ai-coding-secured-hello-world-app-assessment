package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;

/**
 * {@code AdminActionGuard}'s first check, actor ≠ subject, across the four mutations it guards on the account itself
 * (Std §2 Failure Paths 12–16; ADR-048). The factor reset's own self-refusal is {@code AdminFactorResetTest}'s.
 */
class SelfActionRefusalTest extends CtxDefaultTest {

    /** A guarded mutation an administrator might aim at their own account. */
    enum SelfAction {
        DEMOTE(id -> put("/api/admin/users/" + id + "/role").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"USER\"}")),
        DISABLE(id -> put("/api/admin/users/" + id + "/enabled").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}")),
        DELETE(id -> delete("/api/admin/users/" + id)),
        UNLOCK(id -> post("/api/admin/users/" + id + "/unlock").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"USER_REQUEST\"}"));

        private final Function<UUID, MockHttpServletRequestBuilder> request;

        SelfAction(Function<UUID, MockHttpServletRequestBuilder> request) {
            this.request = request;
        }
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    private Accounts accounts;
    private TotpFactors factors;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        factors = new TotpFactors(jdbc, cipher, clock);
    }

    private Map<String, Object> accountRow(UUID id) {
        return jdbc.queryForMap("SELECT username, role, enabled, password_hash, failed_login_attempts, locked_until,"
                + " force_password_change, credential_issued_at FROM users WHERE id = ?", id);
    }

    @ParameterizedTest
    @EnumSource(SelfAction.class)
    @Proves("T-ADM-003")
    void anAdministratorActingOnTheirOwnAccountIsRefusedAndTheAccountIsUnchanged(SelfAction action)
            throws Exception {
        Account admin = accounts.withRole("ADMIN");
        CsrfSession session = factors.verified(mockMvc, SignedIn.as(mockMvc, admin), factors.enrol(admin));
        if (action == SelfAction.UNLOCK) {
            // Something to unlock, so an unlock that went through would show.
            jdbc.update("UPDATE users SET failed_login_attempts = 5, locked_until = ? WHERE id = ?",
                    Timestamp.from(clock.instant().plus(Duration.ofMinutes(20))), admin.id());
        }
        Map<String, Object> before = accountRow(admin.id());

        try (AuditCapture audit = AuditCapture.start()) {
            mockMvc.perform(action.request.apply(admin.id()).with(session.inHeader()))
                    .andExpect(problem(ErrorCode.ACCESS_DENIED));
            assertThat(audit.withMessage("Administrative action refused.")).singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("event.reason", "SELF_ACTION")
                            .containsEntry("user.id", admin.id().toString())
                            .containsEntry("user.target.id", admin.id().toString()));
        }

        assertThat(accountRow(admin.id())).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deleted_users WHERE user_id = ?", Integer.class,
                admin.id())).isZero();
    }
}
