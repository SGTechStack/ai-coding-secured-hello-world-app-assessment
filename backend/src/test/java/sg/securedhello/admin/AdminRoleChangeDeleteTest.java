package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.h2.api.Trigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;
import sg.securedhello.user.Tombstones;

/**
 * {@code PUT /api/admin/users/{uuid}/role} and {@code DELETE /api/admin/users/{uuid}} on the shared context (PRD
 * Stories 10 and 11; ADR-044; ADR-048). Every target is a {@code USER}, an unenrolled admin or the actor, so the
 * two-admin count, which this shared database cannot pin, never decides a result; {@link TwoAdminInvariantTest} owns a
 * database for that.
 */
class AdminRoleChangeDeleteTest extends CtxDefaultTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private Tombstones tombstones;

    private Accounts accounts;
    private TotpFactors factors;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        factors = new TotpFactors(jdbc, cipher, clock);
    }

    private CsrfSession verifiedAdmin(Account admin) throws Exception {
        return factors.verified(mockMvc, SignedIn.as(mockMvc, admin), factors.enrol(admin));
    }

    private ResultActions setRole(CsrfSession session, UUID id, String body) throws Exception {
        return mockMvc.perform(put("/api/admin/users/" + id + "/role").with(session.inHeader())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions promote(CsrfSession session, UUID id) throws Exception {
        return setRole(session, id, "{\"role\":\"ADMIN\"}");
    }

    private ResultActions demote(CsrfSession session, UUID id) throws Exception {
        return setRole(session, id, "{\"role\":\"USER\"}");
    }

    private ResultActions deleteUser(CsrfSession session, UUID id) throws Exception {
        return mockMvc.perform(delete("/api/admin/users/" + id).with(session.inHeader()));
    }

    private String role(UUID id) {
        return jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, id);
    }

    private int count(String table, String column, Object value) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
    }

    private void history(UUID userId) {
        jdbc.update("INSERT INTO password_history (id, user_id, password_hash, created_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), userId, "{bcrypt}old", Timestamp.from(clock.instant()));
    }

    private static String email(Account account) {
        return account.username() + "@example.test";
    }

    private boolean live(CsrfSession session) {
        return new SessionRows(jdbc).exists(SessionRows.idOf(session.cookie().getValue()));
    }

    @Test
    void promotingAUserEndsItsSessionAndTheAuditRowNamesActorAndSubject() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        Account target = accounts.user();
        CsrfSession targetSession = SignedIn.as(mockMvc, target);
        CsrfSession session = verifiedAdmin(admin);

        try (AuditCapture audit = AuditCapture.start()) {
            String body = promote(session, target.id()).andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(target.id().toString()))
                    .andExpect(jsonPath("$.role").value("ADMIN"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("password", "emailHmac", "totp");
            assertThat(audit.withMessage("Account role changed to administrator.")).singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("event.action", "user-administration")
                            .containsEntry("event.type", List.of("change"))
                            .containsEntry("user.id", admin.id().toString())
                            .containsEntry("user.target.id", target.id().toString()));
        }

        assertThat(role(target.id())).isEqualTo("ADMIN");
        assertThat(live(targetSession)).as("the old session, with USER authorities, is gone").isFalse();
        mockMvc.perform(get("/api/hello").cookie(targetSession.cookie()))
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }

    @Test
    void demotingAnAdminEndsItsSessionSoNoSessionKeepsTheAdminRole() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        // Not enrolled, so not counted: the demotion lowers no count and the guard allows it whatever the database.
        Account target = accounts.withRole("ADMIN");
        CsrfSession targetSession = SignedIn.as(mockMvc, target);
        CsrfSession session = verifiedAdmin(admin);

        try (AuditCapture audit = AuditCapture.start()) {
            demote(session, target.id()).andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("USER"));
            assertThat(audit.withMessage("Account role changed to user.")).singleElement().satisfies(row ->
                    assertThat(row).containsEntry("user.id", admin.id().toString())
                            .containsEntry("user.target.id", target.id().toString()));
        }

        assertThat(role(target.id())).isEqualTo("USER");
        assertThat(live(targetSession)).isFalse();
        CsrfSession again = SignedIn.as(mockMvc, target);
        mockMvc.perform(get("/api/hello").cookie(again.cookie())).andExpect(status().isOk());
    }

    @Test
    void settingTheRoleAnAccountAlreadyHasChangesNothingAndKeepsItsSession() throws Exception {
        Account target = accounts.user();
        CsrfSession targetSession = SignedIn.as(mockMvc, target);
        CsrfSession session = verifiedAdmin(accounts.withRole("ADMIN"));

        demote(session, target.id()).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"));

        assertThat(role(target.id())).isEqualTo("USER");
        assertThat(live(targetSession)).isTrue();
    }

    /** T-ADM-003's role-change and delete cases; disable is ticket 20's, and unlock is still to come. */
    @Test
    void anAdminChangingTheirOwnRoleOrDeletingThemselvesGetsAccessDeniedAndNothingChanges() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        CsrfSession session = verifiedAdmin(admin);

        try (AuditCapture audit = AuditCapture.start()) {
            demote(session, admin.id()).andExpect(problem(ErrorCode.ACCESS_DENIED));
            promote(session, admin.id()).andExpect(problem(ErrorCode.ACCESS_DENIED));
            deleteUser(session, admin.id()).andExpect(problem(ErrorCode.ACCESS_DENIED));
            assertThat(audit.withMessage("Administrative action refused.")).hasSize(3).allSatisfy(row ->
                    assertThat(row).containsEntry("event.reason", "SELF_ACTION")
                            .containsEntry("user.id", admin.id().toString())
                            .containsEntry("user.target.id", admin.id().toString()));
        }

        assertThat(role(admin.id())).isEqualTo("ADMIN");
        assertThat(count("deleted_users", "user_id", admin.id())).isZero();
        mockMvc.perform(get("/api/admin/users").cookie(session.cookie())).andExpect(status().isOk());
    }

    @Test
    void deletingAUserRemovesItsRowAndHistoryLeavesATombstoneAndEndsItsSession() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        Account target = accounts.user();
        history(target.id());
        CsrfSession targetSession = SignedIn.as(mockMvc, target);
        CsrfSession session = verifiedAdmin(admin);

        try (AuditCapture audit = AuditCapture.start()) {
            deleteUser(session, target.id()).andExpect(status().isNoContent());
            assertThat(audit.withMessage("Account deleted.")).singleElement().satisfies(row ->
                    assertThat(row).containsEntry("event.action", "user-administration")
                            .containsEntry("event.type", List.of("deletion"))
                            .containsEntry("user.id", admin.id().toString())
                            .containsEntry("user.target.id", target.id().toString()));
        }

        assertThat(count("users", "id", target.id())).isZero();
        assertThat(count("password_history", "user_id", target.id())).isZero();
        assertThat(live(targetSession)).isFalse();
        mockMvc.perform(get("/api/hello").cookie(targetSession.cookie()))
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        mockMvc.perform(get("/api/admin/users/" + target.id()).cookie(session.cookie()))
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @Proves("T-ADM-004")
    void theTombstoneHoldsTheDeletedIdentityAndTheDeleterAndIsWrittenInTheDeletesTransaction() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        Account target = accounts.user();
        CsrfSession session = verifiedAdmin(admin);

        deleteUser(session, target.id()).andExpect(status().isNoContent());

        Map<String, Object> tombstone = jdbc.queryForMap("SELECT * FROM deleted_users WHERE user_id = ?", target.id());
        assertThat(tombstone).containsEntry("USERNAME", target.username())
                .containsEntry("EMAIL_HMAC", tombstones.emailHmac(email(target)))
                .containsEntry("DELETED_BY_ID", admin.id());
        assertThat(((OffsetDateTime) tombstone.get("DELETED_AT")).toInstant())
                .isCloseTo(clock.instant(), within(1, ChronoUnit.MICROS));
        assertThat(tombstone.get("EMAIL_HMAC").toString()).doesNotContain(email(target)).hasSize(64);
    }

    /**
     * T-ADM-004's failure half: the {@code users} delete fails after the tombstone was written, in the same
     * transaction, and neither commits. The failure is a real one, raised by a database trigger on the delete, which
     * also records that the tombstone row was already there when the delete ran.
     */
    @Test
    @Proves("T-ADM-004")
    void aFailureAfterTheTombstoneWriteCommitsNeitherTheTombstoneNorTheDelete() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        Account target = accounts.user();
        CsrfSession targetSession = SignedIn.as(mockMvc, target);
        CsrfSession session = verifiedAdmin(admin);
        RefuseDelete.arm(target.id());
        jdbc.execute("CREATE TRIGGER refuse_user_delete BEFORE DELETE ON users FOR EACH ROW CALL '"
                + RefuseDelete.class.getName() + "'");
        try (AuditCapture audit = AuditCapture.start()) {
            deleteUser(session, target.id()).andExpect(problem(ErrorCode.INTERNAL_ERROR));
            assertThat(audit.withMessage("Account deleted.")).isEmpty();
        } finally {
            jdbc.execute("DROP TRIGGER refuse_user_delete");
            RefuseDelete.disarm();
        }

        assertThat(RefuseDelete.tombstonesSeen).as("the tombstone was written before the delete ran").isOne();
        assertThat(count("users", "id", target.id())).isOne();
        assertThat(count("deleted_users", "user_id", target.id())).isZero();
        assertThat(live(targetSession)).as("a rolled-back delete ends no session").isTrue();
    }

    /** Fails the delete of the armed account's {@code users} row, once it has seen whether its tombstone exists. */
    public static final class RefuseDelete implements Trigger {

        private static volatile UUID armed;
        static volatile int tombstonesSeen = -1;

        static void arm(UUID id) {
            armed = id;
            tombstonesSeen = -1;
        }

        static void disarm() {
            armed = null;
        }

        @Override
        public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
            if (!Arrays.asList(oldRow).contains(armed)) {
                return;
            }
            try (var query = connection.prepareStatement("SELECT COUNT(*) FROM deleted_users WHERE user_id = ?")) {
                query.setObject(1, armed);
                try (var rows = query.executeQuery()) {
                    rows.next();
                    tombstonesSeen = rows.getInt(1);
                }
            }
            throw new SQLException("injected failure after the tombstone write");
        }
    }

    /** The ticket's reuse check: the deleted username is taken, and the deleted address creates nothing. */
    @Test
    void aDeletedAccountsUsernameAndEmailCannotBeRegisteredAgain() throws Exception {
        Account target = accounts.user();
        CsrfSession session = verifiedAdmin(accounts.withRole("ADMIN"));
        deleteUser(session, target.id()).andExpect(status().isNoContent());
        Registrations registrations = new Registrations(mockMvc);

        registrations.register(target.username(), Registrations.emailFor(Registrations.freshUsername()))
                .andExpect(problem(ErrorCode.VALIDATION_FAILED))
                .andExpect(jsonPath("$.rule").value("USERNAME_UNAVAILABLE"));
        String username = Registrations.freshUsername();
        registrations.register(username, email(target).toUpperCase(Locale.ROOT)).andExpect(status().isAccepted());

        assertThat(count("users", "username", username)).isZero();
        assertThat(count("users", "email", email(target))).isZero();
        assertThat(emails.to(email(target))).isEmpty();
    }

    @Test
    void anUnknownAccountIsDeniedAndARoleOutsideTheTwoIsInvalid() throws Exception {
        Account target = accounts.user();
        CsrfSession session = verifiedAdmin(accounts.withRole("ADMIN"));

        promote(session, UUID.randomUUID()).andExpect(problem(ErrorCode.ACCESS_DENIED));
        deleteUser(session, UUID.randomUUID()).andExpect(problem(ErrorCode.ACCESS_DENIED));
        setRole(session, target.id(), "{}").andExpect(problem(ErrorCode.VALIDATION_FAILED));
        setRole(session, target.id(), "{\"role\":\"ROOT\"}").andExpect(problem(ErrorCode.VALIDATION_FAILED));
        setRole(session, target.id(), "{\"role\":\"admin\"}").andExpect(problem(ErrorCode.VALIDATION_FAILED));
        assertThat(role(target.id())).isEqualTo("USER");
    }

    @Test
    void aFactorOlderThanTenMinutesIsExpiredForBothChanges() throws Exception {
        Account target = accounts.user();
        CsrfSession session = verifiedAdmin(accounts.withRole("ADMIN"));
        // Keep the session inside its idle window while the factor ages past 10 minutes.
        for (int step = 0; step < 2; step++) {
            clock.advance(Duration.ofMinutes(5).plusSeconds(30));
            mockMvc.perform(get("/api/admin/users/" + target.id()).cookie(session.cookie()))
                    .andExpect(status().isOk());
        }

        promote(session, target.id()).andExpect(problem(ErrorCode.MISSING_FACTOR))
                .andExpect(jsonPath("$.reason").value("EXPIRED"));
        deleteUser(session, target.id()).andExpect(problem(ErrorCode.MISSING_FACTOR))
                .andExpect(jsonPath("$.reason").value("EXPIRED"));
        assertThat(role(target.id())).isEqualTo("USER");
        assertThat(count("deleted_users", "user_id", target.id())).isZero();
    }
}
