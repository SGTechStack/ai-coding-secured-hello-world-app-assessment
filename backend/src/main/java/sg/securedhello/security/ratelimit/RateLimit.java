package sg.securedhello.security.ratelimit;

import java.util.Locale;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;

import sg.securedhello.security.ratelimit.RateLimitProperties.Budget;
import sg.securedhello.security.ratelimit.RateLimitProperties.Route;

/**
 * One row of the budget table: a route, an axis, and where its budget binds. {@link AuthRateLimiter} builds one bucket
 * family per constant. A {@link Axis#SOURCE} row is applied by the early filter to every request its route matches; a
 * submitted-value row is applied at its call site, once the value has been read.
 */
public enum RateLimit {

    /** {@code POST /api/login}, per source key: burst 60, then 1 per second (ADR-010). */
    LOGIN_SOURCE("login", HttpMethod.POST, "/api/login", Axis.SOURCE, RateLimitProperties::login),

    /** {@code POST /api/login}, per submitted username: burst 10, then 1 per 6 s (ADR-010; R-STD-018). */
    LOGIN_USERNAME("login", HttpMethod.POST, "/api/login", Axis.USERNAME, RateLimitProperties::login),

    /** {@code GET /api/csrf}, per source key: burst 30, then 1 per 2 s. */
    CSRF_SOURCE("csrf", HttpMethod.GET, "/api/csrf", Axis.SOURCE, RateLimitProperties::csrf),

    /** {@code PATCH /api/profile/password}, per source key: burst 10, then 1 per 6 s (ADR-008; R-RL-001). */
    PROFILE_PASSWORD_SOURCE("profile-password", HttpMethod.PATCH, "/api/profile/password", Axis.SOURCE,
            RateLimitProperties::profilePassword),

    /** {@code POST /api/register}, per source key: burst 5, then 1 per 12 s (ADR-032). */
    REGISTER_SOURCE("register", HttpMethod.POST, "/api/register", Axis.SOURCE, RateLimitProperties::register),

    /** {@code POST /api/register/activate}, per source key: burst 10, then 1 per 6 s (ADR-007; ADR-032). */
    REGISTER_ACTIVATE_SOURCE("register-activate", HttpMethod.POST, "/api/register/activate", Axis.SOURCE,
            RateLimitProperties::registerActivate),

    /** {@code POST /api/password-reset/request}, per source key: burst 5, then 1 per 12 s (PRD Story 6). */
    PASSWORD_RESET_REQUEST_SOURCE("password-reset-request", HttpMethod.POST, "/api/password-reset/request",
            Axis.SOURCE, RateLimitProperties::passwordResetRequest),

    /**
     * {@code POST /api/password-reset/request}, per submitted email address, canonicalised: burst 3, then 1 per 20 min
     * (Std §5:452). It counts registered and unregistered addresses alike, so its refusal is no existence oracle.
     */
    PASSWORD_RESET_REQUEST_IDENTIFIER("password-reset-request", HttpMethod.POST, "/api/password-reset/request",
            Axis.IDENTIFIER, RateLimitProperties::passwordResetRequest),

    /** {@code POST /api/password-reset/confirm}, per source key: burst 10, then 1 per 6 s (ADR-007; R-STD-019). */
    PASSWORD_RESET_CONFIRM_SOURCE("password-reset-confirm", HttpMethod.POST, "/api/password-reset/confirm",
            Axis.SOURCE, RateLimitProperties::passwordResetConfirm),

    /** {@code POST /api/mfa/totp/enrolment}, per source key: burst 10, then 1 per 6 s (ADR-025). */
    MFA_TOTP_ENROLMENT_SOURCE("mfa-totp-enrolment", HttpMethod.POST, "/api/mfa/totp/enrolment", Axis.SOURCE,
            RateLimitProperties::mfaTotpEnrolment),

    /** {@code POST /api/mfa/totp/enrolment/confirmation}, per source key: burst 20, then 1 per 3 s (REJ-071). */
    MFA_TOTP_ENROLMENT_CONFIRMATION_SOURCE("mfa-totp-enrolment-confirmation", HttpMethod.POST,
            "/api/mfa/totp/enrolment/confirmation", Axis.SOURCE, RateLimitProperties::mfaTotpEnrolmentConfirmation);

    private final String route;
    private final HttpMethod method;
    private final String path;
    private final Axis axis;
    private final Function<RateLimitProperties, @Nullable Route> binding;

    RateLimit(String route, HttpMethod method, String path, Axis axis,
            Function<RateLimitProperties, @Nullable Route> binding) {
        this.route = route;
        this.method = method;
        this.path = path;
        this.axis = axis;
        this.binding = binding;
    }

    /** Where the budget binds: {@code app.security.rate-limit.<route>.<axis>}, then {@code .burst} and so on. */
    public String property() {
        return "app.security.rate-limit." + route + "." + axis.property();
    }

    public HttpMethod method() {
        return method;
    }

    /** The route's path pattern, as the authorization matrix writes it. */
    public String path() {
        return path;
    }

    public Axis axis() {
        return axis;
    }

    /** This row's budget in {@code properties}, or {@code null} if the row is not configured. */
    @Nullable Budget budget(RateLimitProperties properties) {
        Route configured = binding.apply(properties);
        return configured == null ? null : axis.of(configured);
    }

    /** What a bucket is keyed on. */
    public enum Axis {

        /** The source key (ADR-020). */
        SOURCE,
        /** The submitted username, as sent. */
        USERNAME,
        /** The submitted identifier, username or email; the reset request passes its address canonicalised. */
        IDENTIFIER;

        /** The property segment: {@code source}, {@code username} or {@code identifier}. */
        public String property() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Nullable Budget of(Route route) {
            return switch (this) {
                case SOURCE -> route.source();
                case USERNAME -> route.username();
                case IDENTIFIER -> route.identifier();
            };
        }
    }
}
