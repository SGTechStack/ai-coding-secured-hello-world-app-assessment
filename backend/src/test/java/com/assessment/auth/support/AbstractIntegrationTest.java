package com.assessment.auth.support;

import com.assessment.auth.password.PasswordHistoryRepository;
import com.assessment.auth.password.PasswordResetTokenRepository;
import com.assessment.auth.security.RateLimitFilter;
import com.assessment.auth.user.DeletedUserRepository;
import com.assessment.auth.user.Role;
import com.assessment.auth.user.UserRepository;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * The base for every integration test in this suite (spec.md S12).
 *
 * <p><strong>Full HTTP on a random port, not MockMvc</strong> — see {@link ApiClient} for why.
 *
 * <p>The application context is <strong>shared and cached</strong> across every subclass: same
 * annotations, same context key, one Tomcat and one schema migration for the whole suite. That is a
 * deliberate trade, and {@link #restoreBaseline()} is the price of it. Nothing here uses
 * {@code @DirtiesContext}: a context restart per class would also re-run
 * {@code AdminBootstrapRunner}, and story 1.21's idempotence branch is the one thing that must be
 * observed across restarts rather than destroyed by them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestClockConfig.class)
@ContextConfiguration(initializers = PostgresContextInitializer.class)
public abstract class AbstractIntegrationTest {

  /** Matches {@code app.admin.username} in {@code application-test.yaml}. */
  protected static final String ADMIN_USERNAME = "rootadmin";

  /** Matches {@code app.admin.password}. Valid under the real policy, so the seed is not special-cased. */
  protected static final String ADMIN_PASSWORD = "orchard signal ridge";

  @LocalServerPort protected int port;

  @Autowired protected UserRepository userRepository;
  @Autowired protected PasswordHistoryRepository passwordHistoryRepository;
  @Autowired protected PasswordResetTokenRepository passwordResetTokenRepository;
  @Autowired protected DeletedUserRepository deletedUserRepository;
  @Autowired protected JdbcTemplate jdbcTemplate;
  @Autowired protected PasswordEncoder passwordEncoder;
  @Autowired protected RateLimitFilter rateLimitFilter;
  @Autowired private Clock injectedClock;

  /** A fresh conversation for the test that needs only one. */
  protected ApiClient api;

  @BeforeEach
  void restoreBaseline() {
    api = new ApiClient(port);
    clock().reset();
    // The counters are in-memory, per-process and keyed on 127.0.0.1, and the login limit is 50 a
    // minute. A suite that did not reset them would have the limiter starving the tests that are
    // trying to assert something else -- and it would do it intermittently, depending on order.
    rateLimitFilter.resetCounters();
    resetAdmin();
    // Only the most recently issued token is ever valid (Std:112), and two tests assert on
    // `findAll()` having exactly one row. A token left behind by an earlier test makes that
    // assertion a lie about what the code did.
    jdbcTemplate.update("delete from password_reset_tokens");
  }

  /**
   * Restores the single-administrator baseline: password, forced-change flag, lock state, enabled
   * state and role.
   *
   * <p>The demotion of every <em>other</em> {@code USER_MANAGER} is the part worth explaining. Story
   * 1.17's last-administrator test has to promote a second manager and juggle roles to reach the
   * branch it is testing, and story 1.21's test asserts the seed produced <strong>exactly one</strong>
   * manager. Without this line the two are order-dependent, and the failure would look like a
   * bootstrap defect rather than test bleed.
   */
  protected void resetAdmin() {
    jdbcTemplate.update(
        "update users set password_hash = ?, require_password_change = true, "
            + "failed_login_attempts = 0, locked_until = null, enabled = true, "
            + "disabled_at = null, role = ? where username = ?",
        passwordEncoder.encode(ADMIN_PASSWORD),
        Role.USER_MANAGER,
        ADMIN_USERNAME);
    jdbcTemplate.update(
        "update users set role = ? where role = ? and username <> ?",
        Role.USER,
        Role.USER_MANAGER,
        ADMIN_USERNAME);
  }

  /**
   * A logged-in administrator with the forced-change flag cleared.
   *
   * <p>The flag is cleared <em>before</em> logging in rather than by walking the three-step first
   * boot, because the tier-0 {@code PasswordChangeFilter} would otherwise pre-empt the authorization
   * matrix on every call and every administrator test would assert 403. The three-step boot itself is
   * tested directly, once, by story 1.11's forced-change test.
   */
  protected ApiClient adminSession() {
    jdbcTemplate.update(
        "update users set require_password_change = false where username = ?", ADMIN_USERNAME);
    ApiClient client = new ApiClient(port);
    client.login(ADMIN_USERNAME, ADMIN_PASSWORD);
    return client;
  }

  /** The fixed clock. Every deadline in this application is advanced through it, never slept out. */
  protected MutableClock clock() {
    return (MutableClock) injectedClock;
  }

  /** Absolute URL for a path outside the API base path. */
  protected String rootUrl(String path) {
    return "http://localhost:" + port + path;
  }
}
