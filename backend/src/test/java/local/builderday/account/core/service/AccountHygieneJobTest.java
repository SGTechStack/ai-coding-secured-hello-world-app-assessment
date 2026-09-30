package local.builderday.account.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import local.builderday.support.AuditLogCapture;
import local.builderday.support.TestClocks;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Inactivity measured with a controllable clock: disable after 90 days, soft-delete after 180 (defaults). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClocks.class)
class AccountHygieneJobTest {
  private static final String PASSWORD = "Str0ng!Passw0rd";

  @Autowired AccountHygieneJob job;
  @MockitoSpyBean UserRepository userRepository;
  @Autowired UserProfileService userProfileService;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired TestClocks clocks;
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired EntityManager entityManager;
  @Autowired PlatformTransactionManager transactions;
  @Autowired FindByIndexNameSessionRepository<? extends Session> sessionRepository;
  private String hash;

  @BeforeEach
  void reset() {
    clocks.reset();
    userRepository.deleteAll();
    if (hash == null) hash = passwordEncoder.encode(PASSWORD);
  }

  @Test
  void should_disableOnlyAccountsInactiveLongerThan90Days() {
    var stale = account("staleuser1", Duration.ofDays(91));
    var recent = account("recentuser", Duration.ofDays(89));

    var result = job.run();

    assertThat(result.disabled()).isEqualTo(1);
    assertThat(result.deleted()).isZero();
    var disabled = userRepository.findById(stale).orElseThrow();
    assertThat(disabled.isEnabled()).isFalse();
    assertThat(jdbcTemplate.queryForObject("select disabled_at from users where id = ?", Instant.class, stale))
        .isNotNull();
    assertThat(disabled.getDeletedAt()).isNull();
    assertThat(userRepository.findById(recent).orElseThrow().isEnabled()).isTrue();
  }

  @Test
  void should_softDeleteAccountsInactiveLongerThan180Days_keepingTheTombstone() {
    var abandoned = account("abandoned1", Duration.ofDays(181));
    var disabledOnly = account("dormant123", Duration.ofDays(120));

    var result = job.run();

    assertThat(result.deleted()).isEqualTo(1);
    assertThat(result.disabled()).isEqualTo(1);
    var tombstone = userRepository.findById(abandoned).orElseThrow();
    assertThat(tombstone.getDeletedAt()).isNotNull();
    assertThat(tombstone.isEnabled()).isFalse();
    assertThat(tombstone.getUsername()).isEqualTo("abandoned1");
    assertThat(userRepository.findById(disabledOnly).orElseThrow().getDeletedAt()).isNull();
  }

  @Test
  void should_measureNeverUsedAccountsFromCreation() {
    var neverLoggedIn = userRepository.save(new UserEntity(UUID.randomUUID(), "neverused1",
        "neverused1@test.example.com", hash, "USER", true)).getId();

    clocks.advance(Duration.ofDays(89));
    assertThat(job.run().disabled()).isZero();

    clocks.advance(Duration.ofDays(2));
    assertThat(job.run().disabled()).isEqualTo(1);
    assertThat(userRepository.findById(neverLoggedIn).orElseThrow().isEnabled()).isFalse();
  }

  @Test
  void should_notTouchAnAccountThatLoggedInRecently_andLeaveAlreadyChangedAccountsAlone() {
    account("activeuser", Duration.ofDays(1));
    account("staleuser2", Duration.ofDays(95));

    assertThat(job.run().disabled()).isEqualTo(1);
    assertThat(job.run()).extracting(AccountHygieneJob.Result::disabled, AccountHygieneJob.Result::deleted)
        .containsExactly(0, 0);
  }

  @Test
  void should_endActiveSessions_andBlockLogin_forDisabledAndDeletedAccounts() throws Exception {
    account("sessionuser", Duration.ZERO);
    login("sessionuser", 200);
    assertThat(sessionRepository.findByPrincipalName("sessionuser")).isNotEmpty();

    // The login just recorded "now" as last use. Backdate it past the disable threshold rather than moving the clock,
    // which would also end sessions through the 8-hour absolute timeout and hide what the job itself does.
    lastUsed("sessionuser", Duration.ofDays(91));
    try (var audit = new AuditLogCapture("audit")) {
      job.run();

      assertThat(audit.events()).singleElement().satisfies(event -> {
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(AuditLogCapture.fields(event)).containsEntry("event.action", "account-disabled")
            .containsEntry("event.reason", "inactivity").containsKey("user.id");
        assertThat(event.getFormattedMessage() + AuditLogCapture.fields(event)).doesNotContain("sessionuser");
      });
    }
    assertThat(sessionRepository.findByPrincipalName("sessionuser")).isEmpty();
    login("sessionuser", 401);

    lastUsed("sessionuser", Duration.ofDays(181));
    assertThat(job.run().deleted()).isEqualTo(1);
    login("sessionuser", 401);
  }

  @Test
  void should_recordEachSuccessfulLogin_asTheStartOfInactivity() throws Exception {
    var id = account("loginuser1", Duration.ofDays(95));
    login("loginuser1", 200);

    assertThat(job.run().disabled()).isZero();
    assertThat(userRepository.findById(id).orElseThrow().getLastLoginAt())
        .isAfter(clocks.now().minus(Duration.ofMinutes(1)));
  }

  @Test
  void should_retryAConflictingAccount_andChangeIt_when_theConflictClears() {
    var busy = account("busyuser01", Duration.ofDays(91));
    concurrentWritesOnRead(busy, 2);

    try (var audit = new AuditLogCapture("audit")) {
      assertThat(job.run().disabled()).isEqualTo(1);
      // One event for the committed change; none for the two attempts that lost the race.
      assertThat(audit.events()).singleElement()
          .satisfies(event -> assertThat(AuditLogCapture.fields(event)).containsEntry("user.id", busy.toString()));
    }
    Mockito.reset(userRepository);
    assertThat(userRepository.findById(busy).orElseThrow().isEnabled()).isFalse();
  }

  @Test
  void should_leaveItForTheNextRunAndProcessTheRest_when_anAccountKeepsConflicting() {
    var busy = account("busyuser02", Duration.ofDays(91));
    var other = account("otheruser1", Duration.ofDays(91));
    concurrentWritesOnRead(busy, Integer.MAX_VALUE);

    try (var audit = new AuditLogCapture("audit");
        var jobLog = new AuditLogCapture(AccountHygieneJob.class.getName())) {
      var result = job.run();

      assertThat(result.disabled()).isEqualTo(1);
      assertThat(audit.events()).singleElement()
          .satisfies(event -> assertThat(AuditLogCapture.fields(event)).containsEntry("user.id", other.toString()));
      assertThat(jobLog.events()).filteredOn(event -> event.getLevel() == Level.WARN).singleElement()
          .satisfies(event -> {
            assertThat(AuditLogCapture.fields(event)).containsEntry("event.action", "account-disable")
                .containsEntry("user.id", busy.toString());
            assertThat(event.getFormattedMessage() + AuditLogCapture.fields(event))
                .doesNotContain("busyuser02", "@test.example.com");
          });
    }
    Mockito.reset(userRepository);
    assertThat(userRepository.findById(busy).orElseThrow().isEnabled()).isTrue();
    assertThat(userRepository.findById(other).orElseThrow().isEnabled()).isFalse();

    // The next run picks it up again.
    assertThat(job.run().disabled()).isEqualTo(1);
    assertThat(userRepository.findById(busy).orElseThrow().isEnabled()).isFalse();
  }

  /** The one test double: the job's first {@code times} reads of {@code id} are each followed by a committed write. */
  private void concurrentWritesOnRead(UUID id, int times) {
    var remaining = new AtomicInteger(times);
    doAnswer(invocation -> {
      var read = Optional.ofNullable(entityManager.find(UserEntity.class, id));
      if (remaining.getAndDecrement() > 0) {
        var separate = new TransactionTemplate(transactions);
        separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        separate.executeWithoutResult(status ->
            jdbcTemplate.update("update users set version = version + 1 where id = ?", id));
      }
      return read;
    }).when(userRepository).findById(id);
  }

  private void lastUsed(String username, Duration ago) {
    userProfileService.recordSuccessfulLogin(username, clocks.now().minus(ago));
  }

  /** An enabled account whose last login was {@code inactiveFor} ago on the test clock. */
  private UUID account(String username, Duration inactiveFor) {
    var id = userRepository.save(new UserEntity(UUID.randomUUID(), username, username + "@test.example.com", hash,
        "USER", true)).getId();
    lastUsed(username, inactiveFor);
    return id;
  }

  private void login(String username, int expectedStatus) throws Exception {
    mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().is(expectedStatus));
  }
}
