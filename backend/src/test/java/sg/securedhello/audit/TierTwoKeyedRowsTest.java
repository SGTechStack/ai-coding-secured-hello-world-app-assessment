package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;

/**
 * The tier-2 keyed rows (ADR-019): row 12 (a signed-in caller refused), row 14 (the admin surface asking for the
 * factor) and row 35 (the self-read) are keyed on {@code user.id}, one per user, row, reason and window, and past the
 * distinct-user cap the window ends with one truncation row.
 */
class TierTwoKeyedRowsTest extends CtxDefaultTest {

    private static final String ACCESS_DENIED = "Access denied.";
    private static final String FACTOR_REQUIRED = "Second factor required.";
    private static final String PROFILE_READ = "Profile read.";
    private static final Set<String> TIER_TWO = Set.of(ACCESS_DENIED, FACTOR_REQUIRED, PROFILE_READ);

    @Autowired
    private AuditEmitter emitter;

    @Autowired
    private AuditProperties properties;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Test
    void eachTierTwoRowIsWrittenOncePerUserAndWindowWithItsCount() throws Exception {
        Accounts accounts = new Accounts(jdbc, passwordEncoder);
        Account admin = accounts.withRole("ADMIN");
        new TotpFactors(jdbc, cipher, clock).enrol(admin);
        Account alice = accounts.user();
        Cookie adminSession = SignedIn.as(mockMvc, admin).cookie();
        Cookie aliceSession = SignedIn.as(mockMvc, alice).cookie();
        emitter.closeKeyingWindow();

        try (AuditCapture audit = AuditCapture.start()) {
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(get("/api/admin/users").cookie(adminSession))
                        .andExpect(problem(ErrorCode.MISSING_FACTOR));
                mockMvc.perform(get("/api/admin/users").cookie(aliceSession))
                        .andExpect(problem(ErrorCode.ACCESS_DENIED));
                mockMvc.perform(get("/api/profile").cookie(aliceSession)).andExpect(status().isOk());
            }
            assertThat(audit.rows()).as("nothing is written while the window is open").isEmpty();
            emitter.closeKeyingWindow();

            assertThat(audit.withMessage(FACTOR_REQUIRED)).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("user.id", admin.id().toString())
                    .containsEntry("event.reason", "FACTOR_MISSING")
                    .containsEntry("event.action", "access-control")
                    .containsEntry("log.level", "WARN")
                    .containsEntry("event.count", 3));
            assertThat(audit.withMessage(ACCESS_DENIED)).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("user.id", alice.id().toString())
                    .containsEntry("event.reason", "INSUFFICIENT_ROLE")
                    .containsEntry("event.count", 3));
            assertThat(audit.withMessage(PROFILE_READ)).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("user.id", alice.id().toString())
                    .containsEntry("event.action", "profile-read")
                    .containsEntry("event.type", List.of("allowed"))
                    .containsEntry("event.count", 3)
                    .doesNotContainKey("event.reason"));
            assertThat(audit.rows()).hasSize(3);
        }
    }

    @Test
    @Proves("T-AUD-035")
    void pastTheDistinctUserCapTheWindowEndsWithOneTruncationRowAndNothingFurther() throws Exception {
        int cap = properties.truncation().distinctUsers();
        Accounts accounts = new Accounts(jdbc, passwordEncoder);
        Account admin = accounts.withRole("ADMIN");
        new TotpFactors(jdbc, cipher, clock).enrol(admin);
        List<Cookie> users = new ArrayList<>();
        for (int i = 0; i < cap; i++) {
            users.add(SignedIn.as(mockMvc, accounts.user()).cookie());
        }
        Cookie adminSession = SignedIn.as(mockMvc, admin).cookie();
        emitter.closeKeyingWindow();

        try (AuditCapture audit = AuditCapture.start()) {
            // cap + 1 distinct users: the admin's row 14, then rows 12 and 35 in turn; the last user is untracked.
            mockMvc.perform(get("/api/admin/users").cookie(adminSession)).andExpect(problem(ErrorCode.MISSING_FACTOR));
            for (int i = 0; i < users.size(); i++) {
                if (i % 2 == 0) {
                    mockMvc.perform(get("/api/admin/users").cookie(users.get(i)))
                            .andExpect(problem(ErrorCode.ACCESS_DENIED));
                } else {
                    mockMvc.perform(get("/api/profile").cookie(users.get(i))).andExpect(status().isOk());
                }
            }
            emitter.closeKeyingWindow();

            List<Map<String, Object>> rows = audit.rows();
            assertThat(rows).filteredOn(row -> TIER_TWO.contains(row.get("message"))).hasSize(cap)
                    .extracting(row -> row.get("user.id")).doesNotHaveDuplicates();
            String lastRow = (cap - 1) % 2 == 0 ? "ACCESS_DENIED" : "PROFILE_READ";
            assertThat(audit.withMessage("Keyed audit rows truncated.")).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("event.reason", "USER_CAP_REACHED")
                    .containsEntry("user.distinct_count", cap)
                    .containsEntry("events.untracked_count", 1)
                    .containsEntry("labels.truncated_rows", List.of(lastRow))
                    .doesNotContainKeys("user.id", "source.distinct_count"));
            assertThat(rows).hasSize(cap + 1);
        }
    }
}
