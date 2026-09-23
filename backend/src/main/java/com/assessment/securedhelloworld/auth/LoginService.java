package com.assessment.securedhelloworld.auth;

import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
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

import java.time.Instant;

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

    public LoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttemptService,
            SessionRegistry sessionRegistry) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.sessionRegistry = sessionRegistry;
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public void login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String clientIp = ClientIpResolver.resolve(httpRequest);
        loginAttemptService.assertIpNotThrottled(clientIp);

        User user = userRepository.findByUsername(request.getUsername()).orElse(null);

        if (user == null) {
            loginAttemptService.recordFailureForIp(clientIp);
            log.info("Login failed: unknown username, ip={}", clientIp);
            throw new AuthenticationFailedException();
        }

        if (user.isLocked(Instant.now())) {
            log.info("Login rejected: account locked username={}", user.getUsername());
            throw new AuthenticationFailedException();
        }

        if (!user.isEnabled() || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttemptService.recordFailureForIp(clientIp);
            loginAttemptService.recordFailedAttempt(user);
            log.info("Login failed: bad credentials username={}", user.getUsername());
            throw new AuthenticationFailedException();
        }

        loginAttemptService.recordSuccess(user);
        establishSession(user, httpRequest, httpResponse);
        log.info("Login succeeded username={}", user.getUsername());
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
