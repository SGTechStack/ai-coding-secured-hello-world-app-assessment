package sg.securedhello.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.config.OriginsProperties;
import sg.securedhello.credential.CredentialTokenHash;
import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.email.LinkEmail;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;
import sg.securedhello.user.Identifiers;
import sg.securedhello.user.Tombstones;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * {@code POST /api/register} through the full context (ADR-032; ADR-045): the username axis answers specifically, the
 * email axis never does, and only an activation link sent to the address can make the account usable.
 */
class RegistrationTest extends CtxDefaultTest {

    private static final String LAPSED = "Lapsed pending registration deleted.";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Tombstones tombstones;

    @Autowired
    private OriginsProperties origins;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Registrations registrations;

    @BeforeEach
    void setUp() {
        registrations = new Registrations(mockMvc);
    }

    private int usersNamed(String username) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username);
    }

    private int usersWithEmail(String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
    }

    private void tombstone(String username, String canonicalEmail) {
        jdbc.update("INSERT INTO deleted_users (user_id, username, email_hmac, deleted_at, deleted_by_id)"
                        + " VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), username, tombstones.emailHmac(canonicalEmail),
                Timestamp.from(clock.instant()), UUID.randomUUID());
    }

    @Test
    void aNewAddressBecomesAPendingRegistrationAndIsSentOneActivationLink() throws Exception {
        String username = Registrations.freshUsername();
        String email = Registrations.emailFor(username);

        registrations.register(username, email).andExpect(status().isAccepted());

        var row = jdbc.queryForMap("SELECT role, enabled, activated_at, password_hash FROM users WHERE username = ?",
                username);
        assertThat(row).containsEntry("ROLE", "USER").containsEntry("ENABLED", true);
        assertThat(row.get("ACTIVATED_AT")).as("pending").isNull();
        assertThat(row.get("PASSWORD_HASH")).as("no password is taken at registration (ADR-032)").isNull();
        List<LinkEmail> sent = emails.to(email);
        assertThat(sent).singleElement().satisfies(link -> {
            assertThat(link.type()).isEqualTo(CredentialTokenType.ACTIVATION);
            assertThat(link.link()).hasScheme(URI.create(origins.spa()).getScheme())
                    .hasAuthority(URI.create(origins.spa()).getAuthority()).hasPath("/activate");
        });
    }

    @Test
    void theActivationTokenIsStoredOnlyAsItsDomainSeparatedHashWithATwentyFourHourLifetime() throws Exception {
        String username = Registrations.freshUsername();
        String email = Registrations.emailFor(username);
        registrations.register(username, email).andExpect(status().isAccepted());
        String token = emails.latestToken(email, CredentialTokenType.ACTIVATION).orElseThrow();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        var row = jdbc.queryForMap("SELECT t.type, t.token_hash, t.created_at, t.expires_at, t.used_at"
                + " FROM credential_tokens t JOIN users u ON u.id = t.user_id WHERE u.username = ?", username);
        assertThat(row).containsEntry("TYPE", "ACTIVATION")
                .containsEntry("TOKEN_HASH", CredentialTokenHash.hash(CredentialTokenType.ACTIVATION, token));
        assertThat(row.values()).doesNotContain(token);
        assertThat(row.get("USED_AT")).isNull();
        Instant created = ((java.time.OffsetDateTime) row.get("CREATED_AT")).toInstant();
        Instant expires = ((java.time.OffsetDateTime) row.get("EXPIRES_AT")).toInstant();
        assertThat(java.time.Duration.between(created, expires)).hasHours(24);
    }

    @Test
    void theLinkOriginIsTheConfiguredOneWhateverHostTheRequestNamed() throws Exception {
        String username = Registrations.freshUsername();
        String email = Registrations.emailFor(username);

        registrations.send("/api/register", """
                {"username":"%s","email":"%s","link":"https://attacker.example/activate"}"""
                .formatted(username, email)).andExpect(status().isAccepted());

        assertThat(emails.to(email)).singleElement()
                .satisfies(link -> assertThat(link.link().toString()).startsWith(origins.spa() + "/activate#token="));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Alice", " alice", "alice ", "caféx"})
    void aUsernameThatCanonicalisationWouldChangeIsRejectedNotChanged(String submitted) throws Exception {
        String email = Registrations.emailFor(Registrations.freshUsername());

        registrations.register(submitted, email).andExpect(problem(ErrorCode.VALIDATION_FAILED))
                .andExpect(jsonPath("$.rule").doesNotExist());

        assertThat(usersWithEmail(email)).isZero();
        assertThat(usersNamed(Identifiers.canonical(submitted))).isZero();
        assertThat(emails.to(email)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"administrator", "admin", "root", "bob@example.com", "ab", "a-name-that-is-longer-than-32-chars",
            "semi;colon"})
    void aReservedMalformedOrAtSignUsernameIsRejected(String submitted) throws Exception {
        String email = Registrations.emailFor(Registrations.freshUsername());

        registrations.register(submitted, email).andExpect(problem(ErrorCode.VALIDATION_FAILED))
                .andExpect(jsonPath("$.rule").doesNotExist());

        assertThat(usersWithEmail(email)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-address", "two@@example.test", "no-domain-dot@example", "spaces in@example.test"})
    void aMalformedEmailAddressIsRejected(String submitted) throws Exception {
        String username = Registrations.freshUsername();

        registrations.register(username, submitted).andExpect(problem(ErrorCode.VALIDATION_FAILED));

        assertThat(usersNamed(username)).isZero();
    }

    @Test
    void anEmailAddressIsStoredCanonicalWithTheWholeAddressLowercasedAndNoFolding() throws Exception {
        String username = Registrations.freshUsername();
        String submitted = "  Bob.Smith+Tag." + username + "@Example.COM ";

        registrations.register(username, submitted).andExpect(status().isAccepted());

        String stored = jdbc.queryForObject("SELECT email FROM users WHERE username = ?", String.class, username);
        assertThat(stored).isEqualTo("bob.smith+tag." + username + "@example.com");
        assertThat(emails.to(stored)).hasSize(1);
    }

    @Test
    void aTakenUsernameIsUnavailableAndNothingIsReservedForTheEmail() throws Exception {
        Accounts.Account holder = new Accounts(jdbc, passwordEncoder).user();
        String email = Registrations.emailFor(Registrations.freshUsername());

        registrations.register(holder.username(), email).andExpect(problem(ErrorCode.VALIDATION_FAILED))
                .andExpect(jsonPath("$.rule").value("USERNAME_UNAVAILABLE"));

        assertThat(usersWithEmail(email)).isZero();
        assertThat(emails.to(email)).isEmpty();
    }

    @Test
    void aPendingRegistrationsUsernameIsUnavailableToAnotherAddress() throws Exception {
        String username = Registrations.freshUsername();
        registrations.register(username, Registrations.emailFor(username)).andExpect(status().isAccepted());
        String other = Registrations.emailFor(Registrations.freshUsername());

        registrations.register(username, other).andExpect(problem(ErrorCode.VALIDATION_FAILED))
                .andExpect(jsonPath("$.rule").value("USERNAME_UNAVAILABLE"));

        assertThat(usersWithEmail(other)).isZero();
    }

    @Test
    void aTombstonedUsernameIsUnavailable() throws Exception {
        String username = Registrations.freshUsername();
        tombstone(username, Registrations.emailFor(Registrations.freshUsername()));
        String email = Registrations.emailFor(Registrations.freshUsername());

        registrations.register(username, email).andExpect(problem(ErrorCode.VALIDATION_FAILED))
                .andExpect(jsonPath("$.rule").value("USERNAME_UNAVAILABLE"));

        assertThat(usersWithEmail(email)).isZero();
    }

    @Test
    void aTombstonedEmailAddressGetsTheSame202AndNothingIsCreatedOrSent() throws Exception {
        String deleted = Registrations.freshUsername();
        String email = Registrations.emailFor(deleted);
        tombstone(deleted, email);
        String username = Registrations.freshUsername();

        // Submitted in another case: the tombstone compares the canonical form (ADR-044).
        registrations.register(username, email.toUpperCase(java.util.Locale.ROOT)).andExpect(status().isAccepted());

        assertThat(usersNamed(username)).isZero();
        assertThat(usersWithEmail(email)).isZero();
        assertThat(emails.to(email)).isEmpty();
    }

    @Test
    void anActivatedAccountsAddressGetsTheSame202AndNothingIsCreatedOrSent() throws Exception {
        Accounts.Account holder = new Accounts(jdbc, passwordEncoder).user();
        String email = holder.username() + "@example.test";
        String username = Registrations.freshUsername();

        registrations.register(username, email).andExpect(status().isAccepted());

        assertThat(usersNamed(username)).isZero();
        assertThat(emails.to(email)).isEmpty();
    }

    @Test
    void aRepeatBeforeActivationReplacesThePendingRegistrationAndItsToken() throws Exception {
        String first = Registrations.freshUsername();
        String email = Registrations.emailFor(first);
        registrations.register(first, email).andExpect(status().isAccepted());
        String firstToken = emails.latestToken(email, CredentialTokenType.ACTIVATION).orElseThrow();
        UUID id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);

        String second = Registrations.freshUsername();
        registrations.register(second, email).andExpect(status().isAccepted());
        String secondToken = emails.latestToken(email, CredentialTokenType.ACTIVATION).orElseThrow();

        assertThat(usersWithEmail(email)).isOne();
        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, id)).isEqualTo(second);
        assertThat(usersNamed(first)).as("the first username is released").isZero();
        registrations.activate(firstToken, Registrations.PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
        registrations.activate(secondToken, Registrations.PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void anAdministratorsPendingInviteIsNotReplacedBySelfRegistration() throws Exception {
        String invited = Registrations.freshUsername();
        String email = Registrations.emailFor(invited);
        jdbc.update("INSERT INTO users (id, username, email, role, enabled, created_at) VALUES (?, ?, ?, 'ADMIN', TRUE, ?)",
                UUID.randomUUID(), invited, email, Timestamp.from(clock.instant()));
        String username = Registrations.freshUsername();

        registrations.register(username, email).andExpect(status().isAccepted());

        assertThat(usersNamed(invited)).isOne();
        assertThat(usersNamed(username)).isZero();
        assertThat(emails.to(email)).isEmpty();
    }

    @Test
    @Proves("T-CRED-022")
    void theNewAccountsIdIsAssignedBeforeAnyFlushAndTheAccountAndItsTokenCommitTogether() throws Exception {
        String username = Registrations.freshUsername();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UserAccount account = accounts.save(UserAccount.pendingRegistration(username,
                    Registrations.emailFor(username), clock.instant()));
            assertThat(account.getId()).as("assigned at persist()").isNotNull();
            assertThat(account.getId().version()).as("UUIDv4 (ADR-050)").isEqualTo(4);
            assertThat(usersNamed(username)).as("not yet flushed").isZero();
            status.setRollbackOnly();
        });

        // Make the token insert fail: the account must not commit without it.
        jdbc.execute("ALTER TABLE credential_tokens ADD CONSTRAINT ck_test_no_activation CHECK (type <> 'ACTIVATION')"
                + " NOCHECK");
        try {
            registrations.register(username, Registrations.emailFor(username))
                    .andExpect(problem(ErrorCode.INTERNAL_ERROR));
        } finally {
            jdbc.execute("ALTER TABLE credential_tokens DROP CONSTRAINT ck_test_no_activation");
        }
        assertThat(usersNamed(username)).as("rolled back with the token").isZero();

        registrations.register(username, Registrations.emailFor(username)).andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credential_tokens t JOIN users u ON u.id = t.user_id"
                + " WHERE u.username = ? AND t.type = 'ACTIVATION'", Integer.class, username)).isOne();
    }

    @Test
    void aBodyWithoutAnEmailIsAValidationFailure() throws Exception {
        registrations.send("/api/register", "{\"username\":\"%s\"}".formatted(Registrations.freshUsername()))
                .andExpect(problem(ErrorCode.VALIDATION_FAILED));
    }
    private int holdsOn(String username) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM username_holds WHERE username = ?", Integer.class, username);
    }

    @Test
    @Proves("T-CRED-026")
    void aPendingRegistrationHoldsItsUsernameForTwentyFourHoursThenFreesIt() throws Exception {
        String username = Registrations.freshUsername();
        String squatter = Registrations.emailFor(username);
        registrations.register(username, squatter).andExpect(status().isAccepted());
        String lapsed = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", String.class, squatter);
        String owner = Registrations.emailFor(Registrations.freshUsername());

        clock.advance(Registration.PENDING_PERIOD.minusSeconds(1));
        try (AuditCapture audit = AuditCapture.start()) {
            registrations.register(username, owner).andExpect(problem(ErrorCode.VALIDATION_FAILED))
                    .andExpect(jsonPath("$.rule").value("USERNAME_UNAVAILABLE"));
            assertThat(audit.withMessage(LAPSED)).as("nothing has lapsed yet").isEmpty();
        }

        clock.advance(java.time.Duration.ofSeconds(1));
        try (AuditCapture audit = AuditCapture.start()) {
            registrations.register(username, owner).andExpect(status().isAccepted());
            assertThat(audit.withMessage(LAPSED)).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("event.action", "user-provisioning")
                    .containsEntry("event.type", List.of("deletion"))
                    .containsEntry("user.id", lapsed));
        }
        assertThat(usersWithEmail(squatter)).as("the lapsed pending registration is gone").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deleted_users WHERE user_id = ?", Integer.class,
                UUID.fromString(lapsed))).as("no tombstone").isZero();
        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE username = ?", String.class, username))
                .isEqualTo(owner);
        assertThat(emails.latestToken(owner, CredentialTokenType.ACTIVATION)).isPresent();
    }

    @Test
    @Proves("T-CRED-026")
    void aHoldTakenOnAnActivatedAccountsAddressAlsoLapsesAfterTwentyFourHours() throws Exception {
        Accounts.Account activated = new Accounts(jdbc, passwordEncoder).user();
        String username = Registrations.freshUsername();
        registrations.register(username, activated.username() + "@example.test").andExpect(status().isAccepted());
        assertThat(holdsOn(username)).isOne();
        String other = Registrations.emailFor(Registrations.freshUsername());

        registrations.register(username, other).andExpect(problem(ErrorCode.VALIDATION_FAILED));

        clock.advance(Registration.PENDING_PERIOD);
        registrations.register(username, other).andExpect(status().isAccepted());
        assertThat(usersWithEmail(other)).isOne();
        assertThat(jdbc.queryForObject("SELECT email FROM username_holds WHERE username = ?", String.class, username))
                .isEqualTo(other);
    }

    @Test
    @Proves("T-CRED-026")
    void theSameAddressMayRepeatItsRegistrationWhileItHoldsTheUsername() throws Exception {
        String username = Registrations.freshUsername();
        String email = Registrations.emailFor(username);
        registrations.register(username, email).andExpect(status().isAccepted());
        String id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", String.class, email);
        clock.advance(Registration.PENDING_PERIOD);

        try (AuditCapture audit = AuditCapture.start()) {
            registrations.register(username, email).andExpect(status().isAccepted());
            assertThat(audit.withMessage(LAPSED)).as("its own registration is renewed, not lapsed").isEmpty();
        }
        assertThat(jdbc.queryForObject("SELECT id FROM users WHERE email = ?", String.class, email)).isEqualTo(id);
        registrations.activate(emails.latestToken(email, CredentialTokenType.ACTIVATION).orElseThrow(),
                Registrations.PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    @Proves("T-CRED-026")
    void aRepeatedRegistrationRenewsItsHoldForAnotherTwentyFourHours() throws Exception {
        Accounts.Account activated = new Accounts(jdbc, passwordEncoder).user();
        String username = Registrations.freshUsername();
        String address = activated.username() + "@example.test";
        registrations.register(username, address).andExpect(status().isAccepted());
        clock.advance(Registration.PENDING_PERIOD.minusHours(1));
        registrations.register(username, address).andExpect(status().isAccepted());

        clock.advance(java.time.Duration.ofHours(2));
        registrations.register(username, Registrations.emailFor(Registrations.freshUsername()))
                .andExpect(problem(ErrorCode.VALIDATION_FAILED))
                .andExpect(jsonPath("$.rule").value("USERNAME_UNAVAILABLE"));
    }

    @Test
    @Proves("T-CRED-026")
    void aRegistrationPurgesEveryExpiredHold() throws Exception {
        String stale = Registrations.freshUsername();
        registrations.register(stale, Registrations.emailFor(stale)).andExpect(status().isAccepted());
        clock.advance(Registration.PENDING_PERIOD);

        String next = Registrations.freshUsername();
        registrations.register(next, Registrations.emailFor(next)).andExpect(status().isAccepted());

        assertThat(holdsOn(stale)).as("an unrelated registration purged the expired hold").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM username_holds WHERE expires_at <= ?", Integer.class,
                Timestamp.from(clock.instant()))).isZero();
    }

    @Test
    @Proves("T-CRED-026")
    void anAdministratorsInviteWithItsAdminIssuedTokenNeverLapses() throws Exception {
        String invited = Registrations.freshUsername();
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, username, email, role, enabled, created_at) VALUES (?, ?, ?, 'USER', TRUE, ?)",
                id, invited, Registrations.emailFor(invited), Timestamp.from(clock.instant()));
        jdbc.update("INSERT INTO credential_tokens (id, user_id, type, token_hash, expires_at, created_at, admin_issued)"
                        + " VALUES (?, ?, 'ACTIVATION', ?, ?, ?, TRUE)", UUID.randomUUID(), id,
                UUID.randomUUID().toString().replace("-", "") + "0".repeat(32),
                Timestamp.from(clock.instant().plus(Registration.PENDING_PERIOD)), Timestamp.from(clock.instant()));
        clock.advance(Registration.PENDING_PERIOD.multipliedBy(2));

        try (AuditCapture audit = AuditCapture.start()) {
            registrations.register(invited, Registrations.emailFor(Registrations.freshUsername()))
                    .andExpect(problem(ErrorCode.VALIDATION_FAILED));
            assertThat(audit.withMessage(LAPSED)).isEmpty();
        }
        assertThat(usersNamed(invited)).isOne();
    }

    @Test
    @Proves("T-CRED-026")
    void anAdministratorsInviteNeverLapses() throws Exception {
        String invited = Registrations.freshUsername();
        jdbc.update("INSERT INTO users (id, username, email, role, enabled, created_at) VALUES (?, ?, ?, 'ADMIN', TRUE, ?)",
                UUID.randomUUID(), invited, Registrations.emailFor(invited), Timestamp.from(clock.instant()));
        clock.advance(Registration.PENDING_PERIOD.multipliedBy(2));

        registrations.register(invited, Registrations.emailFor(Registrations.freshUsername()))
                .andExpect(problem(ErrorCode.VALIDATION_FAILED));
        assertThat(usersNamed(invited)).isOne();
    }

    @Test
    @Proves("T-CRED-027")
    void aReRegistrationAndAnActivationOfOnePendingRegistrationRunOneAfterTheOther() throws Exception {
        String first = Registrations.freshUsername();
        String email = Registrations.emailFor(first);
        registrations.register(first, email).andExpect(status().isAccepted());
        String token = emails.latestToken(email, CredentialTokenType.ACTIVATION).orElseThrow();
        String second = Registrations.freshUsername();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            // The test holds the account's row lock, as either path would, while both paths start.
            Future<?> holder = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                    status -> {
                        assertThat(accounts.findForUpdateByEmail(email)).isPresent();
                        locked.countDown();
                        await(release);
                    }));
            locked.await();
            ExecutorService racers = Executors.newFixedThreadPool(2);
            try {
                Future<MvcResult> activation = racers.submit(() ->
                        registrations.activate(token, Registrations.PASSWORD).andReturn());
                Future<MvcResult> reRegistration = racers.submit(() ->
                        registrations.register(second, email).andReturn());
                assertThatThrownBy(() -> activation.get(200, TimeUnit.MILLISECONDS)).as("the activation waits")
                        .isInstanceOf(TimeoutException.class);
                assertThatThrownBy(() -> reRegistration.get(200, TimeUnit.MILLISECONDS)).as("the re-registration waits")
                        .isInstanceOf(TimeoutException.class);
                release.countDown();
                holder.get();

                int activated = activation.get().getResponse().getStatus();
                assertThat(reRegistration.get().getResponse().getStatus()).isEqualTo(202);
                String username = jdbc.queryForObject("SELECT username FROM users WHERE email = ?", String.class, email);
                if (activated == 204) {
                    assertThat(username).as("an activated account is never renamed").isEqualTo(first);
                    assertThat(jdbc.queryForObject("SELECT activated_at FROM users WHERE email = ?", Instant.class,
                            email)).isNotNull();
                } else {
                    assertThat(activated).isEqualTo(400);
                    assertThat(activation.get().getResponse().getContentAsString())
                            .contains(ErrorCode.RESET_TOKEN_INVALID.name());
                    assertThat(username).isEqualTo(second);
                }
            } finally {
                racers.shutdownNow();
            }
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @Proves("T-CRED-027")
    void aRegistrationThatLosesTheRaceForAUsernameGetsUsernameUnavailableNotA500() throws Exception {
        String username = Registrations.freshUsername();
        String winner = Registrations.emailFor(username);
        String loser = Registrations.emailFor(Registrations.freshUsername());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            // A concurrent registration has inserted its hold and not yet committed, so the loser's lookup misses it.
            Future<?> first = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                    status -> {
                        jdbc.update("INSERT INTO username_holds (id, username, email, expires_at) VALUES (?, ?, ?, ?)",
                                UUID.randomUUID(), username, winner,
                                Timestamp.from(clock.instant().plus(Registration.PENDING_PERIOD)));
                        inserted.countDown();
                        await(release);
                    }));
            inserted.await();
            Future<MvcResult> second = pool.submit(() -> registrations.register(username, loser).andReturn());
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).as("it waits on the unique index")
                    .isInstanceOf(TimeoutException.class);
            release.countDown();
            first.get();

            MvcResult result = second.get();
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(result.getResponse().getContentAsString()).contains("USERNAME_UNAVAILABLE");
            assertThat(usersWithEmail(loser)).isZero();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
