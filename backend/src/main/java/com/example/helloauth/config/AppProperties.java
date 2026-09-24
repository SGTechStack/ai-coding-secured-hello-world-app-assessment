package com.example.helloauth.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized security tunables ({@code app.*}) per the ratified config
 * decision. Bound via {@code @ConfigurationPropertiesScan} on the
 * application class.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Minimum password length — the only password policy rule (ticket 07). */
    private int passwordMinLength = 12;

    private final Lockout lockout = new Lockout();
    private final IpThrottle ipThrottle = new IpThrottle();
    private final PasswordReset passwordReset = new PasswordReset();
    private final Admin admin = new Admin();
    private final Cors cors = new Cors();

    public int getPasswordMinLength() {
        return passwordMinLength;
    }

    public void setPasswordMinLength(int passwordMinLength) {
        this.passwordMinLength = passwordMinLength;
    }

    public Lockout getLockout() {
        return lockout;
    }

    public IpThrottle getIpThrottle() {
        return ipThrottle;
    }

    public PasswordReset getPasswordReset() {
        return passwordReset;
    }

    public Admin getAdmin() {
        return admin;
    }

    public Cors getCors() {
        return cors;
    }

    /**
     * Per-account brute-force lockout (ticket 11): {@code maxFailures}
     * consecutive failures measured within a sliding {@code window} anchored
     * at {@code last_failed_at} set {@code locked_until = now + cooldown}.
     */
    public static class Lockout {

        /** Consecutive failures within {@link #window} that trigger the lock. */
        private int maxFailures = 5;

        /** Observation window — a failure older than this doesn't extend the streak. */
        private Duration window = Duration.ofMinutes(10);

        /** How long a locked account rejects even correct credentials. */
        private Duration cooldown = Duration.ofMinutes(15);

        public int getMaxFailures() {
            return maxFailures;
        }

        public void setMaxFailures(int maxFailures) {
            this.maxFailures = maxFailures;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public Duration getCooldown() {
            return cooldown;
        }

        public void setCooldown(Duration cooldown) {
            this.cooldown = cooldown;
        }
    }

    /**
     * Per-IP sliding-window throttle (ticket 11), independent of any account.
     *
     * <p>{@code maxFailures} must stay <em>below</em>
     * {@code lockout.max-failures}: the IP gate is checked first, so a single
     * source IP then can never record enough failures to lock an account —
     * that is the literal anti-DoS AC ("an attacker cannot lock out a user by
     * failing their password from one IP"). The research's illustrative 20
     * would have permitted exactly that, so the bound is deliberate.
     */
    public static class IpThrottle {

        /** Per-IP failure budget within {@link #window} before 429s start. */
        private int maxFailures = 4;

        /**
         * Per-IP request budget for the anonymous auth mutations —
         * {@code register} and {@code password-reset/request} — within
         * {@link #window} (security-review F-04). An independent bucket from
         * {@link #maxFailures}: it counts every hit, success or failure,
         * because the endpoints themselves are the resource (a user row +
         * BCrypt hash per register, a token row per reset request). No
         * ordering constraint vs the lockout — it gates no account state.
         */
        private int anonMaxRequests = 20;

        /** Sliding window anchored at the IP's most recent failure. */
        private Duration window = Duration.ofMinutes(10);

        /** Bound on cache keys — guards memory against spoofed-IP key spray. */
        private long maxEntries = 10_000;

        public int getMaxFailures() {
            return maxFailures;
        }

        public void setMaxFailures(int maxFailures) {
            this.maxFailures = maxFailures;
        }

        public int getAnonMaxRequests() {
            return anonMaxRequests;
        }

        public void setAnonMaxRequests(int anonMaxRequests) {
            this.anonMaxRequests = anonMaxRequests;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public long getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(long maxEntries) {
            this.maxEntries = maxEntries;
        }
    }

    /** Password-reset token mechanics (ticket 12). */
    public static class PasswordReset {

        /**
         * Token lifetime — the spec pins 15–30 min; the default sits at the
         * tight end. Measured against the injected {@code Clock}.
         */
        private Duration tokenTtl = Duration.ofMinutes(15);

        /**
         * SPA page the stubbed email links to — the token is appended as
         * {@code ?token=…}. A deployment knob (like the CORS origin), not a
         * secret.
         */
        private String linkBaseUrl = "http://localhost:3000/reset-password";

        /**
         * Whether the stubbed {@code EmailService} logs the full reset link —
         * the developer convenience that makes the flow exercisable without
         * SMTP (security-review F-02). The link carries the plaintext token,
         * which is a live bearer credential: prod sets this {@code false} so
         * a log-shipping pipeline can never aggregate one, and the stub logs
         * a suppressed-delivery line naming only the recipient.
         */
        private boolean logResetLink = true;

        // NOTE: no cleanupInterval field — the janitor cadence
        // (app.password-reset.cleanup-interval) is owned by application.yml
        // and consumed directly by the @Scheduled placeholder; a dead typed
        // binding here would only look load-bearing.

        public Duration getTokenTtl() {
            return tokenTtl;
        }

        public void setTokenTtl(Duration tokenTtl) {
            this.tokenTtl = tokenTtl;
        }

        public String getLinkBaseUrl() {
            return linkBaseUrl;
        }

        public void setLinkBaseUrl(String linkBaseUrl) {
            this.linkBaseUrl = linkBaseUrl;
        }

        public boolean isLogResetLink() {
            return logResetLink;
        }

        public void setLogResetLink(boolean logResetLink) {
            this.logResetLink = logResetLink;
        }
    }

    /**
     * Initial-admin bootstrap credentials (ticket 13), env-var backed.
     * Blank username/password means "not configured" — the seeder stands
     * down. The dev profile ships documented defaults; the prod profile
     * (ticket 14) makes the env vars required so missing config fails fast
     * at startup rather than leaving no way into the admin module.
     */
    public static class Admin {

        /** Login name for the seeded admin ({@code APP_ADMIN_USERNAME}). */
        private String username;

        /** Raw password for the seeded admin — BCrypt-hashed before storage. */
        private String password;

        /**
         * The seeded admin's email — required by the {@code users} schema
         * even though the seed flow never verifies it
         * ({@code APP_ADMIN_EMAIL}).
         */
        private String email = "admin@localhost";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    /**
     * CORS allow-list (ticket 14): the SPA origins permitted to call the API
     * with credentials. The dev default is the Vite origin; the prod profile
     * binds {@code APP_CORS_ALLOWED_ORIGINS} (comma-separated) with a blank
     * default — an unset allow-list yields no permitted origins, which is
     * fail-closed (the SPA can't call the API until one is configured).
     */
    public static class Cors {

        // No Java-side origin: the YAML placeholder
        // (${APP_CORS_ALLOWED_ORIGINS:http://localhost:3000}) always resolves,
        // so a default here would be unreachable — the dev origin would have
        // to be edited in two places. Empty means "bind failed" → no origins.
        private List<String> allowedOrigins = List.of();

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }
}
