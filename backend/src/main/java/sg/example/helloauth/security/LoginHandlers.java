package sg.example.helloauth.security;

import static org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

import sg.example.helloauth.account.AccountPrincipal;
import sg.example.helloauth.api.ApiException;
import sg.example.helloauth.audit.AuditLogger;
import sg.example.helloauth.loginprotection.AccountLockout;
import sg.example.helloauth.loginprotection.Throttling;
import sg.example.helloauth.session.SessionControl;

/** What form login does once an attempt has succeeded or failed. The SPA gets JSON, never a redirect. */
final class LoginHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private final SessionControl sessionControl;
    private final AccountLockout lockout;
    private final Throttling throttling;
    private final AuditLogger audit;
    private final HandlerExceptionResolver errors;

    LoginHandlers(SessionControl sessionControl, AccountLockout lockout, Throttling throttling, AuditLogger audit,
            HandlerExceptionResolver errors) {
        this.sessionControl = sessionControl;
        this.lockout = lockout;
        this.throttling = throttling;
        this.audit = audit;
        this.errors = errors;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
        sessionControl.loggedIn(request.getSession());
        lockout.loginSucceeded(principal.id());
        throttling.loginSucceeded(request);
        audit.loginSucceeded(principal.id(), request);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    /** Every login failure, whatever its cause, gets the one generic error. */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException ex) {
        audit.loginFailed(request);
        lockout.loginFailed(request.getParameter(SPRING_SECURITY_FORM_USERNAME_KEY), request);
        errors.resolveException(request, response, null, ApiException.invalidCredentials());
    }
}
