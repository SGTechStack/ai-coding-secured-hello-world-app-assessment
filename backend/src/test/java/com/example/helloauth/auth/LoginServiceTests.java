package com.example.helloauth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.helloauth.MutableClock;
import com.example.helloauth.audit.AuditLogger;
import com.example.helloauth.config.AppProperties;
import com.example.helloauth.user.Role;
import com.example.helloauth.user.User;
import com.example.helloauth.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Service-seam coverage for {@link LoginService} — the narrower ratified seam
 * (ticket 05) where the ordering invariant is pinned precisely:
 * IP-throttle → account-lock → credentials, with the counter semantics that
 * follow from it. Mocks at the repository/manager boundary; real
 * {@link IpThrottleService} and {@link MutableClock} so time and bucket
 * behavior are exercised, not stubbed.
 *
 * <p>Configured thresholds: lockout 3 / 10m window / 15m cooldown, IP
 * throttle 2 / 10m window.
 */
class LoginServiceTests {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final String IP = "10.0.0.1";

    private UserRepository users;
    private AuthenticationManager authenticationManager;
    private MutableClock clock;
    private IpThrottleService ipThrottle;
    private LoginService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        authenticationManager = mock(AuthenticationManager.class);
        clock = new MutableClock(T0);

        AppProperties properties = new AppProperties();
        properties.getLockout().setMaxFailures(3);
        properties.getLockout().setWindow(Duration.ofMinutes(10));
        properties.getLockout().setCooldown(Duration.ofMinutes(15));
        properties.getIpThrottle().setMaxFailures(2);
        properties.getIpThrottle().setWindow(Duration.ofMinutes(10));

        ipThrottle = new IpThrottleService(clock, properties);
        service = new LoginService(
            users, authenticationManager, ipThrottle, clock, properties,
            new AuditLogger());
    }

    private User user() {
        User user = new User();
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("$2a$hash");
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setCreatedAt(T0);
        return user;
    }

    private Authentication success() {
        return UsernamePasswordAuthenticationToken.authenticated(
            "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private void badCredentials() {
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("bad"));
    }

    // ------------------------------------------------------------------
    // Ordering invariant — throttled attempts move nothing
    // ------------------------------------------------------------------

    @Test
    void throttledAttemptPerformsNoLookupOrAuthentication() {
        // Prime the bucket to the threshold (2) via direct records.
        ipThrottle.recordFailure(IP);
        ipThrottle.recordFailure(IP);

        assertThatThrownBy(() -> service.login("alice", "pw", IP))
            .isInstanceOf(LoginThrottledException.class);

        // The throttle gate precedes even the user lookup — the strongest
        // pin on the ordering invariant: a throttled attempt can observe
        // nothing and change nothing.
        verifyNoInteractions(users, authenticationManager);
    }

    @Test
    void throttledAttemptCannotIncrementTheAccountCounter() {
        User user = user();
        ipThrottle.recordFailure(IP);
        ipThrottle.recordFailure(IP);

        assertThatThrownBy(() -> service.login("alice", "pw", IP))
            .isInstanceOf(LoginThrottledException.class);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(users, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Account lock — checked before credentials
    // ------------------------------------------------------------------

    @Test
    void lockedAccountIsRejectedBeforeCredentialVerification() {
        User user = user();
        user.setLockedUntil(T0.plusSeconds(600));
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login("alice", "correct", IP))
            .isInstanceOf(LockedException.class);

        // Rejection happened before credentials: no authentication, no save,
        // and the IP bucket stayed empty — a locked attempt counts nowhere.
        verifyNoInteractions(authenticationManager);
        verify(users, never()).save(any());
        assertThat(ipThrottle.isThrottled(IP)).isFalse();
    }

    @Test
    void expiredLockAllowsTheAuthenticationAttempt() {
        User user = user();
        user.setLockedUntil(T0.minusSeconds(1));
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(success());

        Authentication result = service.login("alice", "correct", IP);

        assertThat(result.isAuthenticated()).isTrue();
    }

    // ------------------------------------------------------------------
    // Failure accounting — counter, window, lock trigger
    // ------------------------------------------------------------------

    @Test
    void badCredentialsIncrementsCounterAndRecordsIpFailure() {
        User user = user();
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        badCredentials();

        assertThatThrownBy(() -> service.login("alice", "bad", IP))
            .isInstanceOf(BadCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLastFailedAt()).isEqualTo(T0);
        assertThat(user.getLockedUntil()).isNull();
        verify(users).save(user);
    }

    @Test
    void nthFailureInsideWindowSetsLockedUntil() {
        User user = user();
        user.setFailedLoginAttempts(2);
        user.setLastFailedAt(T0.minusSeconds(60)); // inside the 10m window
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        badCredentials();

        assertThatThrownBy(() -> service.login("alice", "bad", IP))
            .isInstanceOf(BadCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(user.getLockedUntil())
            .isEqualTo(T0.plus(Duration.ofMinutes(15)));
    }

    @Test
    void failureOutsideWindowRestartsTheStreakAtOne() {
        User user = user();
        user.setFailedLoginAttempts(2);
        user.setLastFailedAt(T0.minusSeconds(601)); // just past the 10m window
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        badCredentials();

        assertThatThrownBy(() -> service.login("alice", "bad", IP))
            .isInstanceOf(BadCredentialsException.class);

        // Stale streak decays — "within a window" is literal.
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLastFailedAt()).isEqualTo(T0);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void failureAtExactlyTheWindowEdgeStillExtendsTheStreak() {
        User user = user();
        user.setFailedLoginAttempts(2);
        user.setLastFailedAt(T0.minus(Duration.ofMinutes(10))); // exactly at edge
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        badCredentials();

        assertThatThrownBy(() -> service.login("alice", "bad", IP))
            .isInstanceOf(BadCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(user.getLockedUntil()).isNotNull();
    }

    @Test
    void unknownUsernameRecordsOnlyTheIpFailure() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        badCredentials();

        assertThatThrownBy(() -> service.login("ghost", "bad", IP))
            .isInstanceOf(BadCredentialsException.class);

        // No phantom counter rows — but the attempt still burns IP budget:
        // after the second spray the IP throttles regardless of usernames.
        verify(users, never()).save(any());
        assertThatThrownBy(() -> service.login("other", "bad", IP))
            .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.login("third", "bad", IP))
            .isInstanceOf(LoginThrottledException.class);
    }

    @Test
    void providerStatusExceptionsRecordNothing() {
        // A DisabledException/LockedException surfacing from authenticate
        // means a pre-auth check fired — credential verification never ran,
        // so neither counter moves (the service-level lock check is the
        // primary path; this is the backstop).
        User user = user();
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any()))
            .thenThrow(new DisabledException("disabled"));

        assertThatThrownBy(() -> service.login("alice", "pw", IP))
            .isInstanceOf(DisabledException.class);

        verify(users, never()).save(any());
        ipThrottle.recordFailure(IP); // one more real failure below threshold…
        assertThat(ipThrottle.isThrottled(IP)).isFalse(); // …proves none was recorded
    }

    // ------------------------------------------------------------------
    // Success semantics — resets the account, never the IP bucket
    // ------------------------------------------------------------------

    @Test
    void successResetsTheAccountFailureState() {
        User user = user();
        user.setFailedLoginAttempts(2);
        user.setLastFailedAt(T0.minusSeconds(30));
        user.setLockedUntil(T0.minusSeconds(5)); // expired lock, stale column
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(success());

        service.login("alice", "correct", IP);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastFailedAt()).isNull();
        assertThat(user.getLockedUntil()).isNull();
        verify(users).save(user);
    }

    @Test
    void successDoesNotResetTheIpBucket() {
        // One failure, then a success, then one more failure — if success
        // laundered the bucket the third attempt would not throttle.
        // Per-principal stubbing: re-stubbing authenticate() inside when()
        // would invoke the previously stubbed thenThrow during recording.
        User alice = user();
        User bob = user();
        bob.setUsername("bob");
        when(users.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(users.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(authenticationManager.authenticate(argThat(
                token -> token != null && "alice".equals(token.getName()))))
            .thenThrow(new BadCredentialsException("bad"));
        when(authenticationManager.authenticate(argThat(
                token -> token != null && "bob".equals(token.getName()))))
            .thenReturn(success());

        assertThatThrownBy(() -> service.login("alice", "bad", IP))
            .isInstanceOf(BadCredentialsException.class);
        service.login("bob", "correct", IP);
        assertThatThrownBy(() -> service.login("alice", "bad", IP))
            .isInstanceOf(BadCredentialsException.class);

        // Two recorded failures despite the intervening success → throttled.
        assertThatThrownBy(() -> service.login("alice", "bad", IP))
            .isInstanceOf(LoginThrottledException.class);
    }

    @Test
    void successfulLoginWithCleanStateSkipsTheSave() {
        User user = user(); // all counters already clean
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(success());

        service.login("alice", "correct", IP);

        verify(users, never()).save(any());
    }

    @Test
    void nullRemoteAddrSkipsTheIpLayer() {
        User user = user();
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        badCredentials();

        // Defensive path: no remote address means no throttle bookkeeping,
        // but the account counter still records.
        assertThatThrownBy(() -> service.login("alice", "bad", null))
            .isInstanceOf(BadCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
    }
}
