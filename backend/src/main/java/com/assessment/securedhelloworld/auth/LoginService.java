package com.assessment.securedhelloworld.auth;

import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import com.assessment.securedhelloworld.logging.LogSanitizer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Clock;

/**
 * Login orchestration: credential check, generic-error enumeration
 * resistance, lockout-window enforcement, and session creation.
 *
 * <p>Failed-attempt counting and lockout triggering (Story 3) are handled
 * here; the fixed threshold/duration read from configuration is applied by
 * {@code LoginAttemptService}, wired in ticket 04.
 */
@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy = new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final LoginAttemptService loginAttemptService;
    private final SessionRegistry sessionRegistry;
    private final LoginCountService loginCountService;
    private final Clock clock;
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;

    public LoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttemptService,
            SessionRegistry sessionRegistry,
            LoginCountService loginCountService,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.sessionRegistry = sessionRegistry;
        this.loginCountService = loginCountService;
        this.clock = clock;
        this.loginSuccessCounter = Counter.builder("app.auth.login")
                .tag("outcome", "success")
                .description("Login attempts by outcome (this instance only - see login_counts table for the fleet-wide total)")
                .register(meterRegistry);
        this.loginFailureCounter = Counter.builder("app.auth.login")
                .tag("outcome", "failure")
                .description("Login attempts by outcome (this instance only - see login_counts table for the fleet-wide total)")
                .register(meterRegistry);
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public void login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);

        if (user == null) {
            loginFailureCounter.increment();
            loginCountService.increment(LoginOutcome.FAILURE);
            log.info("Login failed: unknown username");
            throw new AuthenticationFailedException();
        }

        if (user.isLocked(clock.instant())) {
            loginFailureCounter.increment();
            loginCountService.increment(LoginOutcome.FAILURE);
            log.info("Login rejected: account locked username={}", LogSanitizer.sanitize(user.getUsername()));
            throw new AuthenticationFailedException();
        }

        if (!user.isEnabled() || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            boolean justLocked = user.recordFailedAttempt(loginAttemptService.policy(), clock);
            userRepository.save(user);
            if (justLocked) {
                log.warn("Account locked username={} attempts={}",
                        LogSanitizer.sanitize(user.getUsername()), user.getFailedLoginAttempts());
            }
            loginFailureCounter.increment();
            loginCountService.increment(LoginOutcome.FAILURE);
            log.info("Login failed: bad credentials username={}", LogSanitizer.sanitize(user.getUsername()));
            throw new AuthenticationFailedException();
        }

        user.recordSuccess(clock);
        userRepository.save(user);
        establishSession(user, httpRequest, httpResponse);
        loginSuccessCounter.increment();
        loginCountService.increment(LoginOutcome.SUCCESS);
        log.info("Login succeeded username={}", LogSanitizer.sanitize(user.getUsername()));
    }

    private void establishSession(User user, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AppUserDetails principal = new AppUserDetails(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        sessionRegistry.registerNewSession(httpRequest.getSession(true).getId(), principal);
    }
}
