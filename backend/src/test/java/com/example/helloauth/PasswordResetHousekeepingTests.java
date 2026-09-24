package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.helloauth.passwordreset.EmailService;
import com.example.helloauth.passwordreset.PasswordResetService;
import com.example.helloauth.passwordreset.PasswordResetToken;
import com.example.helloauth.passwordreset.PasswordResetTokenJanitor;
import com.example.helloauth.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Ticket-14 reviewer-decision coverage (see
 * {@code docs/agents/reviewer-decisions.md}) — reset-token housekeeping,
 * driven at the HTTP seam where behavior is observable (via the shared
 * {@link ApiTestSupport} fixture) and at the repository seam for the
 * atomic-consume guarantee:
 *
 * <ul>
 *   <li><b>One live token:</b> a new request invalidates the account's
 *       outstanding links, and a confirm invalidates whatever else is
 *       outstanding.</li>
 *   <li><b>Atomic consume:</b> {@code UPDATE … WHERE used_at IS NULL} —
 *       the second claim of the same token gets 0 rows.</li>
 *   <li><b>Table cleanup:</b> the janitor deletes consumed/expired rows
 *       and leaves live tokens alone.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetHousekeepingTests extends ApiTestSupport {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    PasswordResetTokenJanitor janitor;

    @Autowired
    MutableClock clock;

    @MockitoBean
    EmailService emailService;

    @TestConfiguration
    static class MutableClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(T0);
        }
    }

    @BeforeEach
    void clean() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        clock.setInstant(T0);
    }

    @AfterEach
    void cleanAfter() {
        // Leave nothing behind — other classes sharing this context's
        // in-memory H2 clean users-only, and a leftover token row would
        // FK-block their deleteAll.
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // One live token — new request kills earlier links
    // ------------------------------------------------------------------

    @Test
    void newRequestInvalidatesTheAccountsOutstandingTokens() throws Exception {
        seedUser("alice", "alice@example.com");

        requestReset("alice@example.com").andExpect(status().isOk());
        requestReset("alice@example.com").andExpect(status().isOk());

        // Both links were emailed; the first is already superseded.
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(2)).sendPasswordResetLink(
            anyString(), captor.capture());
        String firstToken = captor.getAllValues().get(0);
        String secondToken = captor.getAllValues().get(1);

        // The superseded link is dead — the same generic 400 as any spent
        // token — while the fresh one still works.
        confirmReset(firstToken, NEW_PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail")
                .value("This reset link is invalid or has expired."));
        confirmReset(secondToken, NEW_PASSWORD).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // One live token — a confirm kills outstanding siblings
    // ------------------------------------------------------------------

    @Test
    void confirmInvalidatesOtherOutstandingTokensForTheAccount()
            throws Exception {
        User alice = seedUser("alice", "alice@example.com");
        // Two live links for one account — as concurrent requests could
        // mint before the request-side invalidation lands.
        seedToken(alice, "first-live-token");
        seedToken(alice, "second-live-token");

        confirmReset("first-live-token", NEW_PASSWORD).andExpect(status().isOk());

        confirmReset("second-live-token", NEW_PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail")
                .value("This reset link is invalid or has expired."));
        assertThat(tokenRepository
            .findByTokenHash(PasswordResetService.hashToken("second-live-token"))
            .orElseThrow().getUsedAt()).isNotNull();
    }

    // ------------------------------------------------------------------
    // Atomic consume — the concurrent-confirm race is closed
    // ------------------------------------------------------------------

    @Test
    @org.springframework.transaction.annotation.Transactional
    void consumeIfUnusedClaimsTheTokenExactlyOnce() {
        User alice = seedUser("alice", "alice@example.com");
        seedToken(alice, "race-target");

        // The claim is a single UPDATE … WHERE used_at IS NULL: the first
        // caller gets the row, every concurrent claimant gets 0 — there is
        // no read-modify-write window to double-consume through.
        assertThat(tokenRepository.consumeIfUnused(
            PasswordResetService.hashToken("race-target"), T0)).isEqualTo(1);
        assertThat(tokenRepository.consumeIfUnused(
            PasswordResetService.hashToken("race-target"), T0)).isZero();
    }

    // ------------------------------------------------------------------
    // Table cleanup — consumed/expired rows are deleted, live ones kept
    // ------------------------------------------------------------------

    @Test
    void janitorDeletesDeadRowsAndKeepsLiveTokens() {
        User alice = seedUser("alice", "alice@example.com");
        User bob = seedUser("bob", "bob@example.com");
        PasswordResetToken live = seedToken(alice, "live-token");

        PasswordResetToken used = seedToken(alice, "used-token");
        used.setUsedAt(T0.minusSeconds(60));
        tokenRepository.save(used);
        seedToken(bob, "expired-token", T0.minusSeconds(1));

        assertThat(janitor.purgeDeadTokens()).isEqualTo(2);

        List<PasswordResetToken> remaining = tokenRepository.findAll();
        assertThat(remaining).singleElement()
            .satisfies(t -> assertThat(t.getId()).isEqualTo(live.getId()));
    }

    @Test
    void janitorLogsThePurgeCountOnlyWhenRowsWereDeleted() {
        // The deleted>0 guard keeps the ops log truthful: a line only when
        // rows actually went away, with the count in it — a silent purge or
        // a "Purged 0" line are both failures.
        Logger janitorLog = (Logger) LoggerFactory
            .getLogger(PasswordResetTokenJanitor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        janitorLog.addAppender(appender);
        try {
            User alice = seedUser("alice", "alice@example.com");
            PasswordResetToken used = seedToken(alice, "used-token");
            used.setUsedAt(T0.minusSeconds(60));
            tokenRepository.save(used);

            assertThat(janitor.purgeDeadTokens()).isEqualTo(1);
            assertThat(appender.list).singleElement()
                .satisfies(e -> assertThat(e.getFormattedMessage())
                    .isEqualTo("Purged 1 dead password-reset token(s)."));

            // Nothing left to delete — the next run must stay silent.
            assertThat(janitor.purgeDeadTokens()).isZero();
            assertThat(appender.list).singleElement();
        } finally {
            janitorLog.detachAppender(appender);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** A live token row expiring 15 min out — the configured TTL. */
    private PasswordResetToken seedToken(User user, String plaintext) {
        return seedToken(user, plaintext, T0.plus(Duration.ofMinutes(15)));
    }
}
