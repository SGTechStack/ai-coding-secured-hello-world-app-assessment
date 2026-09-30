package sg.example.helloauth.loginprotection;

import static org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import sg.example.helloauth.api.ApiProperties;
import sg.example.helloauth.api.TooManyRequestsException;

/**
 * Answers a Throttled login, registration or password reset with 429 before any work is done for
 * it: no password is hashed or checked for breaches. (A reset request is also Throttled per
 * email, once its body has been read.)
 */
public final class ThrottleFilter extends OncePerRequestFilter {

    private final Throttling throttling;
    private final HandlerExceptionResolver errors;
    private final RequestMatcher login;
    private final RequestMatcher registration;
    private final RequestMatcher passwordResetRequest;
    private final RequestMatcher passwordResetConfirm;

    public ThrottleFilter(Throttling throttling, ApiProperties api, HandlerExceptionResolver errors) {
        this.throttling = throttling;
        this.errors = errors;
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        this.login = paths.matcher(HttpMethod.POST, api.path("/login"));
        this.registration = paths.matcher(HttpMethod.POST, api.path("/register"));
        this.passwordResetRequest = paths.matcher(HttpMethod.POST, api.path("/password-reset/request"));
        this.passwordResetConfirm = paths.matcher(HttpMethod.POST, api.path("/password-reset/confirm"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Duration> wait = Optional.empty();
        if (login.matches(request)) {
            wait = throttling.admitLogin(request.getParameter(SPRING_SECURITY_FORM_USERNAME_KEY), request);
        } else if (registration.matches(request)) {
            wait = throttling.admitRegistration(request);
        } else if (passwordResetRequest.matches(request)) {
            wait = throttling.admitPasswordResetRequest(request);
        } else if (passwordResetConfirm.matches(request)) {
            wait = throttling.admitPasswordResetConfirm(request);
        }
        if (wait.isPresent()) {
            errors.resolveException(request, response, null, new TooManyRequestsException(wait.get()));
        } else {
            chain.doFilter(request, response);
        }
    }
}
