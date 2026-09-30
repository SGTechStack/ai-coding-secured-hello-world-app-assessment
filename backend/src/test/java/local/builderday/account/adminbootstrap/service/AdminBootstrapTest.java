package local.builderday.account.adminbootstrap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import java.time.Instant;
import java.util.UUID;
import local.builderday.account.adminbootstrap.config.AdminBootstrapProperties;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import local.builderday.account.core.service.PasswordPolicy;
import local.builderday.account.core.service.UserProfileService;
import local.builderday.support.AuditLogCapture;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Admin bootstrap service-method seam (Story 12, ADR 0009). Prior art: {@code AccountHygieneJobTest} — a real
 * {@code @SpringBootTest} on H2, {@code userRepository.deleteAll()} per test, {@code AuditLogCapture}. Credentials vary
 * per test, so each builds its own {@link AdminBootstrap} from a fresh {@link AdminBootstrapProperties} over the real
 * collaborators; the auto-wired runner has already run once with no credentials, which is a no-op.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminBootstrapTest {
  private static final String USERNAME = "admin.one";
  private static final String PASSWORD = "Str0ng!Passw0rd";

  @Autowired UserRepository userRepository;
  @Autowired UserProfileService userProfileService;
  @Autowired PasswordPolicy passwordPolicy;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired LockProvider lockProvider;
  @Autowired MockMvc mvc;

  @BeforeEach
  void reset() {
    userRepository.deleteAll();
  }

  private AdminBootstrap bootstrapWith(String username, String password) {
    return new AdminBootstrap(new AdminBootstrapProperties(username, password), userRepository, userProfileService,
        passwordPolicy, passwordEncoder, lockProvider);
  }

  @Test
  void should_createAnEnabledEmaillessAdmin_withOnePasswordHistoryEntry_whenNoneHasEverExisted() {
    bootstrapWith(USERNAME, PASSWORD).bootstrap();

    var admin = userRepository.findByUsername(USERNAME).orElseThrow();
    assertThat(admin.getRole()).isEqualTo("ADMIN");
    assertThat(admin.getEmail()).isNull();
    assertThat(admin.isEnabled()).isTrue();
    assertThat(admin.getDeletedAt()).isNull();
    assertThat(passwordHistoryCount(admin.getId())).isEqualTo(1);
    assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
  }

  @Test
  void should_storeTheUsernameTrimmedAndLowercased() {
    bootstrapWith("  Admin.One ", PASSWORD).bootstrap();

    assertThat(userRepository.findByUsername("admin.one")).isPresent();
  }

  @Test
  void should_letTheSeededAdminSignIn_withRoleAdmin() throws Exception {
    bootstrapWith(USERNAME, PASSWORD).bootstrap();

    mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk());
    assertThat(userRepository.findByUsername(USERNAME).orElseThrow().getRole()).isEqualTo("ADMIN");
  }

  @Test
  void should_notCreateASecondAdmin_onASecondRun() {
    bootstrapWith(USERNAME, PASSWORD).bootstrap();
    bootstrapWith("admin.two", PASSWORD).bootstrap();

    assertThat(userRepository.findAll()).singleElement()
        .satisfies(account -> assertThat(account.getUsername()).isEqualTo(USERNAME));
  }

  @Test
  void should_notCreateAnAdmin_whenADisabledAdminAlreadyExists() {
    saveAdmin("olddisabled", false, null);

    bootstrapWith(USERNAME, PASSWORD).bootstrap();

    assertThat(userRepository.findByUsername(USERNAME)).isEmpty();
  }

  @Test
  void should_notCreateAnAdmin_whenATombstonedAdminAlreadyExists() {
    saveAdmin("oldtombstoned", false, Instant.now());

    bootstrapWith(USERNAME, PASSWORD).bootstrap();

    assertThat(userRepository.findByUsername(USERNAME)).isEmpty();
  }

  @Test
  void should_writeASuccessAuditEvent_withTheAccountIdAndWithoutTheUsername() {
    try (var audit = new AuditLogCapture("audit")) {
      bootstrapWith(USERNAME, PASSWORD).bootstrap();

      var accountId = userRepository.findByUsername(USERNAME).orElseThrow().getId().toString();
      assertThat(audit.events()).singleElement().satisfies(event -> {
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(AuditLogCapture.fields(event)).containsEntry("event.action", "account-created")
            .containsEntry("event.outcome", "success").containsEntry("event.reason", "admin-bootstrap")
            .containsEntry("user.id", accountId);
        assertThat(event.getFormattedMessage() + AuditLogCapture.fields(event)).doesNotContain(USERNAME);
      });
    }
  }

  @Test
  void should_sendNoAccountCreatedNotification() {
    // bootstrap has no EmailService dependency and createAccount sends none (registration notifies separately), so
    // no notification is even possible. The Admin has no email, so there is no address to notify.
    bootstrapWith(USERNAME, PASSWORD).bootstrap();

    assertThat(userRepository.findByUsername(USERNAME).orElseThrow().getEmail()).isNull();
  }

  @Test
  void should_completeWithoutCreatingOrAuditing_whenNoCredentialsAndNoAdmin() {
    try (var audit = new AuditLogCapture("audit")) {
      bootstrapWith(null, null).bootstrap();

      assertThat(userRepository.findAll()).isEmpty();
      assertThat(audit.events()).isEmpty();
    }
  }

  @Test
  void should_failStartup_whenOnlyOneCredentialIsSet_andNoAdmin() {
    try (var audit = new AuditLogCapture("audit")) {
      assertThatThrownBy(() -> bootstrapWith(USERNAME, null).bootstrap())
          .isInstanceOf(AdminBootstrapException.class);

      assertThat(userRepository.findAll()).isEmpty();
      // Nothing was attempted, so no audit event.
      assertThat(audit.events()).isEmpty();
    }
  }

  @Test
  void should_completeWithoutCreating_whenOnlyOneCredentialIsSet_butAnAdminExists() {
    saveAdmin("existingadmin", true, null);

    bootstrapWith(USERNAME, null).bootstrap();

    assertThat(userRepository.findByUsername(USERNAME)).isEmpty();
  }

  @Test
  void should_completeWithoutCreating_whenCredentialsAreLeftOver_butAnAdminExists() {
    saveAdmin("existingadmin", true, null);

    bootstrapWith(USERNAME, PASSWORD).bootstrap();

    assertThat(userRepository.findAll()).singleElement()
        .satisfies(account -> assertThat(account.getUsername()).isEqualTo("existingadmin"));
  }

  @Test
  void should_failWithUsernameInvalid_andNotContainSubmittedValues() {
    try (var audit = new AuditLogCapture("audit")) {
      var thrown = catchThrowableOfType(AdminBootstrapException.class,
          () -> bootstrapWith("bad name", PASSWORD).bootstrap());

      assertThat(thrown.reason()).isEqualTo("username-invalid");
      assertThat(thrown.getMessage()).contains("USERNAME_INVALID_CHARACTER").doesNotContain("bad name", PASSWORD);
      assertThat(userRepository.findAll()).isEmpty();
      assertThat(audit.events()).singleElement().satisfies(event -> {
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(AuditLogCapture.fields(event)).containsEntry("event.outcome", "failure")
            .containsEntry("event.reason", "username-invalid").doesNotContainKey("user.id");
      });
    }
  }

  @Test
  void should_failWithPasswordPolicy_andNotContainSubmittedValues() {
    try (var audit = new AuditLogCapture("audit")) {
      var thrown = catchThrowableOfType(AdminBootstrapException.class,
          () -> bootstrapWith(USERNAME, "short").bootstrap());

      assertThat(thrown.reason()).isEqualTo("password-policy");
      assertThat(thrown.getMessage()).contains("PASSWORD_TOO_SHORT").doesNotContain(USERNAME, "short");
      assertThat(userRepository.findAll()).isEmpty();
      assertThat(audit.events()).singleElement().satisfies(event ->
          assertThat(AuditLogCapture.fields(event)).containsEntry("event.reason", "password-policy")
              .doesNotContainKey("user.id"));
    }
  }

  @Test
  void should_failWithUsernameTaken_whenTheUsernameBelongsToAUser_andLeaveThatAccountUnchanged() {
    var existing = userProfileService.createAccount(USERNAME, USERNAME + "@test.example.com",
        passwordEncoder.encode(PASSWORD), "USER").orElseThrow();

    try (var audit = new AuditLogCapture("audit")) {
      var thrown = catchThrowableOfType(AdminBootstrapException.class,
          () -> bootstrapWith(USERNAME, PASSWORD).bootstrap());

      assertThat(thrown.reason()).isEqualTo("username-taken");
      var unchanged = userRepository.findById(existing).orElseThrow();
      assertThat(unchanged.getRole()).isEqualTo("USER");
      assertThat(userRepository.findAll()).singleElement()
          .satisfies(account -> assertThat(account.getRole()).isEqualTo("USER"));
      assertThat(audit.events()).singleElement().satisfies(event ->
          assertThat(AuditLogCapture.fields(event)).containsEntry("event.reason", "username-taken")
              .doesNotContainKey("user.id"));
    }
  }

  @Test
  void should_warnToRemoveTheVariables_whenAnAdminExistsAndCredentialsAreSet() {
    saveAdmin("existingadmin", true, null);

    try (var appLog = new AuditLogCapture("local.builderday.account.adminbootstrap.service.AdminBootstrap")) {
      bootstrapWith(USERNAME, PASSWORD).bootstrap();

      assertThat(appLog.events()).anySatisfy(event -> {
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("Remove ADMIN_USERNAME").doesNotContain(USERNAME, PASSWORD);
      });
    }
  }

  @Test
  void should_createNothing_whenTheLockIsHeldElsewhere_thenCreateOnceReleased() {
    var configuration = new LockConfiguration(Instant.now(), AdminBootstrap.LOCK_NAME, java.time.Duration.ofMinutes(1),
        java.time.Duration.ZERO);
    SimpleLock held = lockProvider.lock(configuration).orElseThrow();
    try {
      bootstrapWith(USERNAME, PASSWORD).bootstrap();
      assertThat(userRepository.findByUsername(USERNAME)).isEmpty();
    } finally {
      held.unlock();
    }

    bootstrapWith(USERNAME, PASSWORD).bootstrap();
    assertThat(userRepository.findByUsername(USERNAME)).isPresent();
  }

  @Test
  void should_createTheAdmin_whenAnEmaillessNonAdminAccountAlreadyExists() {
    // The local profile seeds an emailless johndoe (USER). The seeded Admin also has no email, so the two must not
    // collide on a null email. Regression for the createAccount duplicate check.
    userRepository.save(new UserEntity(UUID.randomUUID(), "johndoe", null,
        passwordEncoder.encode(PASSWORD), "USER", true));

    bootstrapWith(USERNAME, PASSWORD).bootstrap();

    var admin = userRepository.findByUsername(USERNAME).orElseThrow();
    assertThat(admin.getRole()).isEqualTo("ADMIN");
    assertThat(admin.getEmail()).isNull();
  }

  @Test
  void should_failWithError_andAuditError_whenTheInsertThrows() {
    var failingProfiles = org.mockito.Mockito.mock(UserProfileService.class);
    org.mockito.Mockito.when(failingProfiles.createAccount(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("ADMIN")))
        .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("boom"));
    var bootstrap = new AdminBootstrap(new AdminBootstrapProperties(USERNAME, PASSWORD), userRepository,
        failingProfiles, passwordPolicy, passwordEncoder, lockProvider);

    try (var audit = new AuditLogCapture("audit")) {
      var thrown = catchThrowableOfType(AdminBootstrapException.class, bootstrap::bootstrap);

      assertThat(thrown.reason()).isEqualTo("error");
      assertThat(thrown.getMessage()).doesNotContain(USERNAME, PASSWORD);
      assertThat(audit.events()).singleElement().satisfies(event -> {
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(AuditLogCapture.fields(event)).containsEntry("event.reason", "error").doesNotContainKey("user.id");
      });
    }
  }

  private void saveAdmin(String username, boolean enabled, Instant deletedAt) {
    var entity = new UserEntity(UUID.randomUUID(), username, null, passwordEncoder.encode(PASSWORD), "ADMIN", enabled);
    if (deletedAt != null) entity.setDeletedAt(deletedAt);
    userRepository.save(entity);
  }

  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  private long passwordHistoryCount(UUID accountId) {
    Long count = jdbcTemplate.queryForObject(
        "select count(*) from password_history where user_id = ?", Long.class, accountId);
    return count == null ? 0 : count;
  }
}
