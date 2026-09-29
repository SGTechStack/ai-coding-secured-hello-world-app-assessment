package com.eitri.auth;

import com.eitri.audit.AuditAccount;
import com.eitri.audit.AuditLogger;
import com.eitri.config.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}/auth")
class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);
    private static final ApiError INVALID_CREDENTIALS = new ApiError("Invalid username or password");
    private static final ApiError INVALID_LOGIN_REQUEST = new ApiError("Invalid login request");
    private static final ApiError TOO_MANY_REQUESTS = new ApiError("Too many requests");
    private static final ApiError AUTHENTICATION_UNAVAILABLE = ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR);

    private final AccountAuthenticationService accountAuthentication;
    private final LoginIpThrottle ipThrottle;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final AuditLogger auditLogger;
    private final AuthenticationAttemptContext authenticationAttempt;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SessionRegistry sessionRegistry;
    private final SessionLoginCoordinator sessionLoginCoordinator;

    AuthController(
            AccountAuthenticationService accountAuthentication,
            LoginIpThrottle ipThrottle,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            AuditLogger auditLogger,
            AuthenticationAttemptContext authenticationAttempt,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SessionRegistry sessionRegistry,
            SessionLoginCoordinator sessionLoginCoordinator) {
        this.accountAuthentication = accountAuthentication;
        this.ipThrottle = ipThrottle;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.auditLogger = auditLogger;
        this.authenticationAttempt = authenticationAttempt;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.sessionRegistry = sessionRegistry;
        this.sessionLoginCoordinator = sessionLoginCoordinator;
    }

    @PostMapping("/login")
    ResponseEntity<?> login(
            @RequestBody LoginRequest login,
            HttpServletRequest request,
            HttpServletResponse response) {
        List<String> invalidFields = invalidFields(login);
        if (!invalidFields.isEmpty()) {
            LOGGER.atWarn()
                    .addKeyValue("validation.fields", invalidFields)
                    .setMessage("Invalid login request")
                    .log();
            return ResponseEntity.badRequest().body(INVALID_LOGIN_REQUEST);
        }

        String username = login.username().toLowerCase(Locale.ROOT);
        // Checked before credentials, so throttled attempts never reach any account's failure counter.
        String sourceIp = request.getRemoteAddr();
        LoginIpThrottle.Decision throttle = ipThrottle.check(sourceIp);
        if (!throttle.allowed()) {
            auditLogger.loginRateLimited(sourceIp, request);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(throttle.retryAfterSeconds()))
                    .body(TOO_MANY_REQUESTS);
        }

        authenticationAttempt.start();
        try {
            AccountAuthenticationService.Result result =
                    accountAuthentication.authenticate(username, login.password());
            if (!result.succeeded()) {
                ipThrottle.recordFailure(sourceIp);
                if (result.newlyLocked()) {
                    auditLogger.accountLocked(result.knownAccount(), request);
                }
                auditLogger.loginFailed(result.knownAccount(), request);
                invalidateIncomingSession(request);
                SecurityContextHolder.clearContext();
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(INVALID_CREDENTIALS);
            }

            Authentication authentication = result.authentication();
            AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
            return sessionLoginCoordinator.coordinate(
                    principal.accountId(),
                    () -> completeLogin(authentication, principal, request, response));
        } catch (AuthenticationServiceException exception) {
            auditLogger.loginSystemFailed(authenticationAttempt.knownAccount(), request, exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(AUTHENTICATION_UNAVAILABLE);
        } finally {
            authenticationAttempt.clear();
        }
    }

    private ResponseEntity<?> completeLogin(
            Authentication authentication,
            AccountPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        List<String> previousSessionIds = sessionRegistry.getAllSessions(principal, false).stream()
                .map(SessionInformation::getSessionId)
                .toList();

        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        csrfTokenRepository.saveToken(null, request, response);

        auditConcurrentExpirations(principal.auditAccount(), previousSessionIds);
        auditLogger.loginSucceeded(principal.auditAccount(), request);
        return ResponseEntity.ok(CurrentUserResponse.from(principal));
    }

    @GetMapping("/me")
    CurrentUserResponse currentUser(Authentication authentication) {
        return CurrentUserResponse.from((AccountPrincipal) authentication.getPrincipal());
    }

    private void auditConcurrentExpirations(AuditAccount account, List<String> previousSessionIds) {
        for (String sessionId : previousSessionIds) {
            SessionInformation session = sessionRegistry.getSessionInformation(sessionId);
            if (session != null && session.isExpired()) {
                auditLogger.concurrentSessionExpired(account, sessionId);
            }
        }
    }

    private static void invalidateIncomingSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private static List<String> invalidFields(LoginRequest login) {
        List<String> invalidFields = new ArrayList<>(2);
        String username = login.username();
        if (username == null || username.isBlank() || !PasswordPolicy.USERNAME.matcher(username).matches()) {
            invalidFields.add("username");
        }
        String password = login.password();
        // BCrypt ignores input beyond 72 bytes, so no longer password can ever be valid.
        if (password == null
                || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_BYTES) {
            invalidFields.add("password");
        }
        return invalidFields;
    }
}
