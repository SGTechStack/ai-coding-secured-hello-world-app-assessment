package sg.securedhello.security.login;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.session.ConcurrentSessionFilter;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.audit.SessionStartReason;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.profile.Profile;
import sg.securedhello.session.SessionAttributes;
import sg.securedhello.user.SignedInUser;

import tools.jackson.databind.json.JsonMapper;

/**
 * Password sign-in ({@code POST /api/login}) and sign-out ({@code POST /api/logout}), added to the security filter
 * chain by {@link #configure}.
 *
 * <p>Sign-in is a JSON login filter ({@code AbstractAuthenticationProcessingFilter} with a JSON converter, the design's
 * "Route C"), because that route keeps the filter-level session strategy. The strategy is the <em>login
 * composite</em> (ADR-038), in a fixed order:
 * <ol>
 *   <li>concurrent-session control: one session per account, and the new login wins (R-AUTH-002);</li>
 *   <li>the session-start audit row, after the displaced session is decided and before the id changes, so it
 *       carries the pre-login {@code session.hash} (T-AUD-015);</li>
 *   <li>the session-id change (ASVS 7.2.4) and the session's registration;</li>
 *   <li>{@code AUTH_INSTANT}, stamped here and nowhere else, and beside it the reset of the idle interval to W, which
 *       the anonymous pin had shortened (T-SES-034);</li>
 *   <li>{@code CsrfAuthenticationStrategy}, listed by hand: the DSL adds it to its own composite but not to a
 *       hand-built one, and without it a token fetched before sign-in would survive sign-in (T-CSRF-007).</li>
 * </ol>
 *
 * <p>Every sign-in failure is the same 401 {@code AUTHENTICATION_FAILED} (ADR-033); a body that is not JSON
 * credentials is 400 {@code VALIDATION_FAILED}. A displaced session's next request is answered 401 too.
 *
 * <p>Sign-out stays behind {@code CsrfFilter}, so a dead session without a token gets 403 (T-CSRF-005). It writes the
 * logout audit row, invalidates the session, sends {@code Clear-Site-Data} on every request, not only secure ones
 * (REJ-010), and answers 204.
 */
public final class SignIn {

    public static final String LOGIN_PATH = "/api/login";
    public static final String LOGOUT_PATH = "/api/logout";

    /** The directives sent on sign-out. The SPA clears its own state regardless (REJ-010). */
    static final String CLEAR_SITE_DATA = "\"cache\", \"cookies\", \"storage\"";

    private final ProviderManager authenticationManager;
    private final SessionRegistry sessionRegistry;
    private final AuditEmitter audit;
    private final ProblemDetailWriter writer;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    SignIn(AuthenticationProvider provider, AuthenticationEventPublisher events, SessionRegistry sessionRegistry,
            AuditEmitter audit, ProblemDetailWriter writer, JsonMapper jsonMapper, Clock clock) {
        this.authenticationManager = new ProviderManager(provider);
        this.authenticationManager.setAuthenticationEventPublisher(events);
        this.sessionRegistry = sessionRegistry;
        this.audit = audit;
        this.writer = writer;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /**
     * Adds the login filter, the concurrent-session filter and sign-out to {@code http}.
     *
     * @param csrfTokens  the chain's CSRF token repository, which the login composite rotates
     * @param csrfHandler the chain's CSRF request handler
     * @param idleWindow  W, the session repository's resolved idle interval
     */
    public void configure(HttpSecurity http, CsrfTokenRepository csrfTokens, CsrfTokenRequestHandler csrfHandler,
            Duration idleWindow) {
        JsonLoginFilter login = new JsonLoginFilter(authenticationManager, new JsonCredentialsConverter(jsonMapper));
        login.setSessionAuthenticationStrategy(loginComposite(csrfTokens, csrfHandler, idleWindow));
        login.setSecurityContextRepository(new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository()));
        login.setAuthenticationSuccessHandler(this::loginSucceeded);
        login.setAuthenticationFailureHandler((request, response, failure) -> writer.write(request, response,
                failure instanceof JsonCredentialsConverter.MalformedLoginException
                        ? ErrorCode.VALIDATION_FAILED
                        : ErrorCode.AUTHENTICATION_FAILED));

        http.addFilterAt(login, UsernamePasswordAuthenticationFilter.class)
                .addFilter(new ConcurrentSessionFilter(sessionRegistry, event ->
                        writer.write(event.getRequest(), event.getResponse(), ErrorCode.AUTHENTICATION_FAILED)))
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults()
                                .matcher(HttpMethod.POST, LOGOUT_PATH))
                        // Both run before the framework's handlers, while the session and principal still exist.
                        .addLogoutHandler(auditLogout())
                        .addLogoutHandler((request, response, authentication) ->
                                response.setHeader("Clear-Site-Data", CLEAR_SITE_DATA))
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
    }

    private SessionAuthenticationStrategy loginComposite(CsrfTokenRepository csrfTokens,
            CsrfTokenRequestHandler csrfHandler, Duration idleWindow) {
        CsrfAuthenticationStrategy csrf = new CsrfAuthenticationStrategy(csrfTokens);
        csrf.setRequestHandler(csrfHandler);
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry),
                (authentication, request, response) -> audit.emit(AuditEvent.SESSION_START,
                        AccountContext.sessionStart(userOf(authentication).id(), SessionStartReason.LOGIN)),
                new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(sessionRegistry),
                (authentication, request, response) -> request.getSession()
                        .setAttribute(SessionAttributes.AUTH_INSTANT, clock.instant()),
                new IdleIntervalReset(idleWindow),
                csrf));
    }

    private void loginSucceeded(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        SignedInUser user = userOf(authentication);
        audit.emit(AuditEvent.LOGIN_SUCCESS, AccountContext.of(user.id()));
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), Profile.of(user));
    }

    private LogoutHandler auditLogout() {
        return (request, response, authentication) -> {
            if (authentication != null && authentication.getPrincipal() instanceof SignedInUser user) {
                audit.emit(AuditEvent.LOGOUT, AccountContext.of(user.id()));
            }
        };
    }

    private static SignedInUser userOf(Authentication authentication) {
        return (SignedInUser) authentication.getPrincipal();
    }

    /** The JSON login filter on {@code POST /api/login}. */
    static final class JsonLoginFilter extends AbstractAuthenticationProcessingFilter {

        JsonLoginFilter(ProviderManager authenticationManager, JsonCredentialsConverter converter) {
            super(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, LOGIN_PATH),
                    authenticationManager);
            setAuthenticationConverter(converter);
        }
    }
}
