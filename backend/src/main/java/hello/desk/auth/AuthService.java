package hello.desk.auth;

import hello.desk.config.AppProperties;
import hello.desk.security.IpThrottle;
import hello.desk.security.PasswordPolicy;
import hello.desk.security.SessionService;
import hello.desk.security.TokenHasher;
import hello.desk.user.PasswordResetToken;
import hello.desk.user.PasswordResetTokenRepository;
import hello.desk.user.PublicUser;
import hello.desk.user.Role;
import hello.desk.user.UserAccount;
import hello.desk.user.UserAccountRepository;
import hello.desk.web.ApiException;
import hello.desk.web.ApiMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final IpThrottle ipThrottle;
    private final SessionService sessions;
    private final EmailService emailService;
    private final AppProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthService(
            UserAccountRepository users,
            PasswordResetTokenRepository tokens,
            PasswordEncoder passwordEncoder,
            IpThrottle ipThrottle,
            SessionService sessions,
            EmailService emailService,
            AppProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.ipThrottle = ipThrottle;
        this.sessions = sessions;
        this.emailService = emailService;
        this.properties = properties;
        this.dummyHash = passwordEncoder.encode("unused-timing-pad");
    }

    @Transactional
    public PublicUser register(String username, String email, String password) {
        if (!PasswordPolicy.isAcceptable(password)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiMessages.WEAK_PASSWORD);
        }
        String normalizedName = username.trim();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsername(normalizedName)) {
            throw new ApiException(HttpStatus.CONFLICT, ApiMessages.USERNAME_TAKEN);
        }
        if (users.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, ApiMessages.EMAIL_TAKEN);
        }
        UserAccount user = new UserAccount();
        user.setUsername(normalizedName);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setFailedLoginAttempts(0);
        user.setCreatedAt(Instant.now());
        return PublicUser.of(users.save(user));
    }

    /**
     * Not transactional on purpose: a failed-attempt write must commit even
     * though the method then returns an authentication error to the caller.
     */
    public PublicUser login(
            String username,
            String password,
            String ip,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (ipThrottle.isBlocked(ip)) {
            log.info("audit event=ip_throttled ip={}", ip);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ApiMessages.TOO_MANY_ATTEMPTS);
        }

        UserAccount user = users.findByUsername(username).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyHash);
            ipThrottle.recordFailure(ip);
            log.info("audit event=login_failure username={} ip={} reason=unknown_user", username, ip);
            throw new ApiException(HttpStatus.UNAUTHORIZED, ApiMessages.INVALID_CREDENTIALS);
        }

        boolean passwordMatches = passwordEncoder.matches(password, user.getPasswordHash());
        Instant now = Instant.now();
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
        if (!user.isEnabled() || locked || !passwordMatches) {
            if (user.isEnabled() && !locked && !passwordMatches) {
                int attempts = user.getFailedLoginAttempts() + 1;
                user.setFailedLoginAttempts(attempts);
                if (attempts >= properties.security().lockoutMaxAttempts()) {
                    Instant until = now.plus(properties.security().lockoutDuration());
                    user.setLockedUntil(until);
                    log.info("audit event=lockout_triggered username={} ip={} until={}", user.getUsername(), ip, until);
                }
                users.saveAndFlush(user);
            }
            ipThrottle.recordFailure(ip);
            String reason = !user.isEnabled() ? "disabled" : locked ? "locked" : "bad_password";
            log.info("audit event=login_failure username={} ip={} reason={}", user.getUsername(), ip, reason);
            throw new ApiException(HttpStatus.UNAUTHORIZED, ApiMessages.INVALID_CREDENTIALS);
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        users.saveAndFlush(user);
        sessions.start(user, request, response);
        log.info("audit event=login_success username={} ip={}", user.getUsername(), ip);
        return PublicUser.of(user);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.logout(request, response);
    }

    @Transactional
    public void requestReset(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        log.info("audit event=password_reset_requested email={}", normalized);
        users.findByEmail(normalized).ifPresent(user -> {
            tokens.deleteAll(tokens.findByUser(user));
            tokens.flush();
            String rawToken = newToken();
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user);
            token.setTokenHash(TokenHasher.sha256(rawToken));
            token.setExpiresAt(Instant.now().plus(properties.security().passwordResetTtl()));
            tokens.save(token);
            String link = properties.security().frontendOrigin() + "/reset-password?token=" + rawToken;
            emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), link);
        });
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        if (!PasswordPolicy.isAcceptable(newPassword)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiMessages.WEAK_PASSWORD);
        }
        PasswordResetToken token = tokens.findByTokenHash(TokenHasher.sha256(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, ApiMessages.RESET_INVALID));
        Instant now = Instant.now();
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiMessages.RESET_INVALID);
        }
        UserAccount user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        token.setUsedAt(now);
        users.save(user);
        tokens.save(token);
        sessions.invalidateUsername(user.getUsername());
        log.info("audit event=password_reset_completed username={}", user.getUsername());
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
