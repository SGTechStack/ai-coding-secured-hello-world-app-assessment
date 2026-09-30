package sg.securedhello.security.login;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.session.SessionInformation;
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
import sg.securedhello.audit.LoginFailureReason;
import sg.securedhello.audit.SessionStartReason;
import sg.securedhello.audit.SourceThrottleReason;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.mfa.TotpFactorStatus;
import sg.securedhello.profile.ProfileReader;
import sg.securedhello.security.device.TrustedDevices;
import sg.securedhello.security.lockout.DeferredFailuresRefusal;
import sg.securedhello.security.ratelimit.AuthRateLimiter;
import sg.securedhello.security.ratelimit.LockoutCardinality;
import sg.securedhello.security.ratelimit.TooManyRequests;
import sg.securedhello.security.source.SourceKeyAuthenticationDetailsSource;
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
 *   <li>the displaced mark cleared, so a sign-in on a displaced session is a fresh sign-in that the new login wins;</li>
 *   <li>concurrent-session control: one session per account, and the new login wins (R-AUTH-002). Each session it
 *       displaces writes row 10 there and then, naming the account;</li>
 *   <li>the session-start audit row, after the displaced session is decided and before the id changes, so it
 *       carries the pre-login {@code session.hash} (T-AUD-015);</li>
 *   <li>the session-id change (ASVS 7.2.4) and the session's registration;</li>
 *   <li>{@code AUTH_INSTANT}, stamped here and nowhere else, and beside it the reset of the idle interval to W, which
 *       the anonymous pin had shortened (T-SES-034);</li>
 *   <li>{@code CsrfAuthenticationStrategy}, listed by hand: the DSL adds it to its own composite but not to a
 *       hand-built one, and without it a token fetched before sign-in would survive sign-in (T-CSRF-007).</li>
 * </ol>
 *
 * <p>A successful sign-in from a browser with no valid device cookie for the account earns one (ADR-075), unless the
 * account needs the second factor: an administrator's browser earns it only at the verified code. Every
 * sign-in failure is the same 401 {@code AUTHENTICATION_FAILED} (ADR-033), and never sets or clears a device cookie; a
 * body that is not JSON
 * credentials is 400 {@code VALIDATION_FAILED}, and a spent username budget is 429 {@code TOO_MANY_REQUESTS}. A
 * displaced session's next request is answered 401 too.
 *
 * <p>Sign-out stays behind {@code CsrfFilter}, so a dead session without a token gets 403 (T-CSRF-005). It writes the
 * logout audit row, invalidates the session, sends {@code Clear-Site-Data: "cache", "storage"} on every request, not
 * only secure ones (REJ-010), and answers 204. The {@code "cookies"} directive is deliberately left out, so sign-out
 * keeps the browser's device cookie (ADR-075). The session cookie is expired instead, by Spring Session through the
 * session cookie serializer, under its own name and attributes; CSRF tokens live in the session, so there is no CSRF
 * cookie to expire.
 */
public final class SignIn {

    public static final String LOGIN_PATH = "/api/login";
    public static final String LOGOUT_PATH = "/api/logout";

    /**
     * The directives sent on sign-out. The SPA clears its own state regardless (REJ-010). No {@code "cookies"}: it would
     * clear the device cookie too, and untrust the browser at every sign-out (ADR-075).
     */
    static final String CLEAR_SITE_DATA = "\"cache\", \"storage\"";

    /**
     * The session attribute Spring Session's registry sets on a session it expires ({@code
     * SpringSessionBackedSessionInformation.EXPIRED_ATTR}, package-private upstream). T-SES-010's re-sign-in case fails
     * if it is renamed.
     */
    static final String DISPLACED = "org.springframework.session.security.SpringSessionBackedSessionInformation.EXPIRED";

    private final ProviderManager authenticationManager;
    private final SessionRegistry sessionRegistry;
    private final AuditEmitter audit;
    private final ProblemDetailWriter writer;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final AuthRateLimiter limiter;
    private final SourceKeyAuthenticationDetailsSource detailsSource;
    private final LockoutCardinality cardinality;
    private final ProfileReader profiles;
    private final TrustedDevices devices;

    SignIn(AuthenticationProvider provider, AuthenticationEventPublisher events, SessionRegistry sessionRegistry,
            AuditEmitter audit, ProblemDetailWriter writer, JsonMapper jsonMapper, Clock clock,
            AuthRateLimiter limiter, SourceKeyAuthenticationDetailsSource detailsSource,
            LockoutCardinality cardinality, ProfileReader profiles, TrustedDevices devices) {
        this.authenticationManager = new ProviderManager(provider);
        this.authenticationManager.setAuthenticationEventPublisher(events);
        this.sessionRegistry = sessionRegistry;
        this.audit = audit;
        this.writer = writer;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.limiter = limiter;
        this.detailsSource = detailsSource;
        this.cardinality = cardinality;
        this.profiles = profiles;
        this.devices = devices;
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
        JsonLoginFilter login = new JsonLoginFilter(authenticationManager,
                new JsonCredentialsConverter(jsonMapper, limiter, detailsSource, cardinality, devices));
        login.setSessionAuthenticationStrategy(loginComposite(csrfTokens, csrfHandler, idleWindow));
        login.setSecurityContextRepository(new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository()));
        login.setAuthenticationSuccessHandler(this::loginSucceeded);
        login.setAuthenticationFailureHandler(this::loginFailed);

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
                SignIn::clearDisplacement,
                (authentication, request, response) -> new EvictionAuditingControl(sessionRegistry, audit,
                        userOf(authentication).id()).onAuthentication(authentication, request, response),
                (authentication, request, response) -> audit.emit(AuditEvent.SESSION_START,
                        AccountContext.sessionStart(userOf(authentication).id(), SessionStartReason.LOGIN)),
                new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(sessionRegistry),
                (authentication, request, response) -> request.getSession()
                        .setAttribute(SessionAttributes.AUTH_INSTANT, clock.instant()),
                new IdleIntervalReset(idleWindow),
                csrf));
    }

    /**
     * A sign-in on a session a newer sign-in displaced starts it afresh. The registry marks a displaced session with
     * {@link #DISPLACED}, which the id change would otherwise carry over, so {@code ConcurrentSessionFilter} would end the
     * new sign-in on its next request after the newer session had already been displaced by it: the user would be left
     * with no live session anywhere (review 08-10 M2). The removal reaches the store only when the session is saved at
     * the end of the request (Spring Session's default {@code FlushMode.ON_SAVE}), so the concurrent-session control
     * that follows still sees this session as the displaced one and expires only the other.
     */
    private static void clearDisplacement(Authentication authentication, HttpServletRequest request,
            HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(DISPLACED);
        }
    }

    /**
     * The uniform 401 for every password-axis failure (ADR-033), 400 for a malformed body, and the deliberate 429 for
     * a spent username budget (ADR-010; T-RL-002). Row 6 is transition-keyed: written on the first refusal after the
     * username was last admitted, not on every refusal. A full lockout-cardinality set is the same 429, on row 5,
     * which is keyed by source (ADR-015). A capped password is the uniform 401; its refusal publishes no failure
     * event, so its login-failure row is written here (ADR-013).
     */
    private void loginFailed(HttpServletRequest request, HttpServletResponse response, AuthenticationException failure)
            throws IOException {
        if (failure instanceof JsonCredentialsConverter.LoginThrottledException throttled) {
            if (throttled.refusal().breach()) {
                audit.emit(AuditEvent.IDENTIFIER_THROTTLED, AccountContext.identifierThrottled());
            }
            TooManyRequests.write(writer, request, response, throttled.refusal());
        } else if (failure instanceof JsonCredentialsConverter.LockoutCardinalityException refused) {
            audit.emit(AuditEvent.SOURCE_THROTTLED,
                    AccountContext.sourceThrottled(SourceThrottleReason.RATE_LIMITED_LOCKOUT_CARDINALITY));
            TooManyRequests.write(writer, request, response, refused.refusal());
        } else {
            if (failure instanceof PasswordDisabledException capped) {
                audit.emit(AuditEvent.LOGIN_FAILURE,
                        AccountContext.loginFailure(capped.userId(), LoginFailureReason.PASSWORD_DISABLED));
            } else if (failure instanceof DeferredFailuresRefusal refused) {
                // A correct password refused because failures deferred under contention locked or disabled it first.
                audit.emit(AuditEvent.LOGIN_FAILURE, AccountContext.loginFailure(refused.userId(), refused.reason()));
            }
            writer.write(request, response, failure instanceof JsonCredentialsConverter.MalformedLoginException
                    ? ErrorCode.VALIDATION_FAILED
                    : ErrorCode.AUTHENTICATION_FAILED);
        }
    }

    /**
     * Row 1 and the profile. A sign-in that was not already from a trusted device of this account earns a new one: its
     * device cookie is set here, on success only, never on a failure (ADR-033; ADR-075). An account that needs the
     * second factor earns it at the verified code instead ({@code TotpFactorGrant}), so a password alone neither
     * trusts nor evicts a device of it.
     */
    private void loginSucceeded(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        SignedInUser user = userOf(authentication);
        audit.emit(AuditEvent.LOGIN_SUCCESS, AccountContext.of(user.id()));
        if (!TotpFactorStatus.requiredFor(user)) {
            devices.trust(user.id(), request, response);
        }
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), profiles.read(authentication));
    }

    private LogoutHandler auditLogout() {
        return (request, response, authentication) -> {
            SignedInUser.from(authentication)
                    .ifPresent(user -> audit.emit(AuditEvent.LOGOUT, AccountContext.of(user.id())));
        };
    }

    private static SignedInUser userOf(Authentication authentication) {
        return (SignedInUser) authentication.getPrincipal();
    }

    /**
     * Concurrent-session control, one per sign-in, that writes row 10 for each session it displaces, at displacement
     * (T-AUD-015). The framework picks which sessions to expire; each is handed to it as a {@link Displaced}, whose
     * expiry is the displacement and writes the row. The sessions are all the signing-in account's, so each row names
     * that account.
     */
    static final class EvictionAuditingControl extends ConcurrentSessionControlAuthenticationStrategy {

        private final AuditEmitter audit;
        private final UUID account;

        EvictionAuditingControl(SessionRegistry sessionRegistry, AuditEmitter audit, UUID account) {
            super(sessionRegistry);
            this.audit = audit;
            this.account = account;
        }

        @Override
        protected void allowableSessionsExceeded(List<SessionInformation> sessions, int allowableSessions,
                SessionRegistry registry) {
            // A mutable list: the framework sorts it in place to pick the least recently used sessions.
            super.allowableSessionsExceeded(sessions.stream().map(Displaced::new)
                    .collect(Collectors.toCollection(ArrayList::new)), allowableSessions, registry);
        }

        /** A session of the account the control may expire; expiring a live one displaces it and writes row 10. */
        private final class Displaced extends SessionInformation {

            private final SessionInformation session;

            Displaced(SessionInformation session) {
                super(session.getPrincipal(), session.getSessionId(), session.getLastRequest());
                this.session = session;
            }

            @Override
            public void expireNow() {
                boolean live = !session.isExpired();
                session.expireNow();
                super.expireNow();
                if (live) {
                    audit.emit(AuditEvent.SESSION_EVICTED, AccountContext.sessionEvicted(account));
                }
            }
        }
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
