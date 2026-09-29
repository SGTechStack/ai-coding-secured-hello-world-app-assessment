package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eitri.audit.AuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

class AuthControllerTest {

    @Test
    void throttledRequestReturnsBeforeAuthenticationOrAttemptContextMutation() {
        AccountAuthenticationService accountAuthentication = mock(AccountAuthenticationService.class);
        LoginIpThrottle ipThrottle = mock(LoginIpThrottle.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        AuthenticationAttemptContext authenticationAttempt = mock(AuthenticationAttemptContext.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(ipThrottle.check("203.0.113.10")).thenReturn(LoginIpThrottle.Decision.rejected(6));
        AuthController controller = new AuthController(
                accountAuthentication,
                ipThrottle,
                mock(SecurityContextRepository.class),
                mock(CsrfTokenRepository.class),
                auditLogger,
                authenticationAttempt,
                mock(SessionAuthenticationStrategy.class),
                mock(SessionRegistry.class),
                mock(SessionLoginCoordinator.class));

        var response = controller.login(
                new LoginRequest("JohnDoe", "Password123!"), request, mock(HttpServletResponse.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("6");
        assertThat(response.getBody())
                .usingRecursiveComparison()
                .isEqualTo(new com.eitri.config.ApiError("Too many requests"));
        verify(auditLogger).loginRateLimited("203.0.113.10", request);
        verifyNoInteractions(accountAuthentication, authenticationAttempt);
    }
}
