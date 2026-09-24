package com.example.helloauth.auth;

import com.example.helloauth.audit.AuditLogger;
import com.example.helloauth.config.AppProperties;
import com.example.helloauth.user.User;
import com.example.helloauth.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * The login path's ordered brute-force defense (ticket 11, ratified in
 * ticket 03's research):
 *
 * <ol>
 *   <li><b>IP throttle</b> — checked before anything that could move account
 *       state. A throttled attempt never reaches credential verification, so
 *       it can never increment {@code failed_login_attempts}: with
 *       {@code ip-throttle.max-failures < lockout.max-failures} one source IP
 *       can never lock an account (the anti-DoS invariant).</li>
 *   <li><b>Account lock</b> — {@code locked_until} rejects before credentials
 *       run, so a locked attempt neither extends the lock nor burns either
 *       failure counter.</li>
 *   <li><b>Credentials</b> — the {@link AuthenticationManager}. Failure
 *       records on <em>both</em> layers; success resets the account counter
 *       but <em>not</em> the IP bucket (window decay only — shared-IP
 *       laundering).</li>
 * </ol>
 *
 * The provider's own pre-auth checks ({@code isAccountNonLocked},
 * {@code isEnabled}) remain as a backstop — a {@code LockedException}/
 * {@code DisabledException} surfacing from {@code authenticate} propagates
 * untouched and records nothing, since credential verification never ran.
 *
 * <p>Not {@code @Transactional}: the failure path exits via exception, which
 * would roll back the very counter update it just recorded. Each
 * {@code save} commits on its own; a lost-update race between concurrent
 * failures is an accepted demo limitation (single-writer-per-account in
 * practice).
 */
@Service
public class LoginService {

    private final UserRepository users;
    private final AuthenticationManager authenticationManager;
    private final IpThrottleService ipThrottle;
    private final Clock clock;
    private final AppProperties properties;
    private final AuditLogger audit;

    public LoginService(UserRepository users,
            AuthenticationManager authenticationManager,
            IpThrottleService ipThrottle, Clock clock, AppProperties properties,
            AuditLogger audit) {
        this.users = users;
        this.authenticationManager = authenticationManager;
        this.ipThrottle = ipThrottle;
        this.clock = clock;
        this.properties = properties;
        this.audit = audit;
    }

    /**
     * Runs the ordered defense and returns the authenticated
     * {@link Authentication}.
     *
     * @param remoteAddr {@code getRemoteAddr()} of the request — the IP-throttle
     *     key; {@code null} skips the IP layer (defensive — never null in practice)
     * @throws LoginThrottledException the source IP exhausted its failure budget
     * @throws LockedException the account's {@code locked_until} is in the future
     * @throws org.springframework.security.core.AuthenticationException any
     *     credential/pre-auth failure — surfaces to the client as the generic 401
     */
    public Authentication login(String username, String rawPassword, String remoteAddr) {
        // (1) IP throttle first — before even the user lookup.
        if (remoteAddr != null && ipThrottle.isThrottled(remoteAddr)) {
            // No authentication event can fire for a rejection before
            // authenticate() runs — the audit emission is direct (ticket 14).
            audit.loginThrottled(username, remoteAddr);
            throw new LoginThrottledException();
        }

        // (2) Account lock — before credentials, so a locked attempt can't
        //     re-arm locked_until or count at either layer.
        User user = users.findByUsername(username).orElse(null);
        Instant now = clock.instant();
        if (isLocked(user, now)) {
            audit.loginFailed(username, AuditLogger.Reasons.LOCKED);
            throw new LockedException("Account is locked");
        }

        // (3) Credential verification.
        try {
            Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                    username, rawPassword));
            resetFailureState(user);
            return authentication;
        } catch (BadCredentialsException | UsernameNotFoundException ex) {
            // Unknown usernames record only at the IP layer — no phantom
            // counters, and the response stays identical either way.
            if (user != null) {
                recordFailure(user, now);
            }
            if (remoteAddr != null) {
                ipThrottle.recordFailure(remoteAddr);
            }
            throw ex;
        }
    }

    private boolean isLocked(User user, Instant now) {
        return user != null && user.getLockedUntil() != null
            && user.getLockedUntil().isAfter(now);
    }

    /**
     * N consecutive failures "within a window" — the window is literal via
     * {@code last_failed_at}: a failure more than {@code window} after the
     * previous one restarts the streak at 1 rather than accumulating across
     * quiet periods.
     */
    private void recordFailure(User user, Instant now) {
        AppProperties.Lockout lockout = properties.getLockout();
        Instant lastFailedAt = user.getLastFailedAt();
        int failures = lastFailedAt != null
                && !lastFailedAt.plus(lockout.getWindow()).isBefore(now)
            ? user.getFailedLoginAttempts() + 1
            : 1;
        user.setFailedLoginAttempts(failures);
        user.setLastFailedAt(now);
        Instant lockedUntil = failures >= lockout.getMaxFailures()
            ? now.plus(lockout.getCooldown())
            : null;
        if (lockedUntil != null) {
            user.setLockedUntil(lockedUntil);
        }
        // Save first, audit second — the lock is only a fact once the row
        // persisted; an audit line for a failed save would be a lie.
        users.save(user);
        if (lockedUntil != null) {
            audit.accountLocked(user.getUsername(), lockedUntil);
        }
    }

    /** Success clears the account's failure state — the IP bucket is untouched. */
    private void resetFailureState(User user) {
        if (user != null && (user.getFailedLoginAttempts() > 0
                || user.getLastFailedAt() != null || user.getLockedUntil() != null)) {
            user.setFailedLoginAttempts(0);
            user.setLastFailedAt(null);
            user.setLockedUntil(null);
            users.save(user);
        }
    }
}
