package sg.securedhello.security.ratelimit;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.hibernate.validator.constraints.time.DurationMin;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The budget table (spec, Lockout, throttling and rate limits) under {@code app.security.rate-limit}. Each route is one
 * {@link Route}, and each of its axes binds {@code <route>.<axis>.burst} and {@code <route>.<axis>.refill-period}
 * (T-RL-010). The values live in {@code application.yml} and nowhere else; there are no defaults here.
 *
 * <p>To budget a new route, add its {@link Route} component here, its rows to {@code application.yml}, and one
 * {@link RateLimit} constant. See {@link AuthRateLimiter}.
 *
 * @param login       {@code POST /api/login}: source and username axes
 * @param csrf        {@code GET /api/csrf}: source axis
 * @param profilePassword {@code PATCH /api/profile/password}: source axis
 * @param sessionMiss the per-source session-store miss budget, on every request (ADR-017)
 * @param register    {@code POST /api/register}: source axis
 * @param registerActivate {@code POST /api/register/activate}: source axis
 * @param passwordResetRequest {@code POST /api/password-reset/request}: source and identifier axes
 * @param passwordResetConfirm {@code POST /api/password-reset/confirm}: source axis
 * @param mfaTotpEnrolment {@code POST /api/mfa/totp/enrolment}: source axis
 * @param mfaTotpEnrolmentConfirmation {@code POST /api/mfa/totp/enrolment/confirmation}: source axis
 * @param mfaTotpVerification {@code POST /api/mfa/totp/verification}: source axis
 */
@Validated
@ConfigurationProperties("app.security.rate-limit")
public record RateLimitProperties(@Valid Route login, @Valid Route csrf, @NotNull @Valid SessionMiss sessionMiss,
        @Valid Route profilePassword, @Valid Route register, @Valid Route registerActivate,
        @Valid Route passwordResetRequest, @Valid Route passwordResetConfirm, @Valid Route mfaTotpEnrolment,
        @Valid Route mfaTotpEnrolmentConfirmation, @Valid Route mfaTotpVerification) {

    /**
     * One route's budgets, one per axis it is throttled on; the axes it is not throttled on stay unset.
     *
     * @param source     per source key
     * @param username   per submitted username
     * @param identifier per submitted identifier (username or email)
     */
    public record Route(@Valid @Nullable Budget source, @Valid @Nullable Budget username,
            @Valid @Nullable Budget identifier) {
    }

    /**
     * A token bucket: {@code burst} requests at once, then one more per {@code refillPeriod}, refilled greedily.
     *
     * @param burst        the bucket's capacity
     * @param refillPeriod the time one token takes to come back
     */
    public record Budget(@Positive long burst, @NotNull @DurationMin(millis = 1) Duration refillPeriod) {
    }

    /**
     * {@code capacity} session-store lookups that did not resolve, per source key, restored in full every
     * {@code window} (ADR-017; T-RL-016; T-RL-022).
     *
     * @param capacity the misses one source may cause per window
     * @param window   the window, counted from the source's first miss
     */
    public record SessionMiss(@Positive long capacity, @NotNull @DurationMin(millis = 1) Duration window) {
    }
}
