package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Ticket-14 fail-fast coverage — misconfiguration must refuse to boot rather
 * than silently weaken a security invariant. Each case boots a real
 * application context and asserts the startup failure names the offending
 * property.
 *
 * <p>Overrides go through command-line arguments, not
 * {@code SpringApplicationBuilder.properties}: default properties sit below
 * {@code application.yml} in precedence, so they'd be overridden back to the
 * shipped values and prove nothing. Command-line args outrank config files.
 * {@code --server.port=0} keeps each boot off the real port.
 *
 * <ul>
 *   <li><b>Config invariant</b> (reviewer decision): the anti-DoS guarantee —
 *       one IP can never lock an account — holds only while
 *       {@code ip-throttle.max-failures < lockout.max-failures}. An override
 *       breaking that refuses to start.</li>
 *   <li><b>Prod admin creds</b>: the prod profile binds
 *       {@code APP_ADMIN_USERNAME}/{@code APP_ADMIN_PASSWORD} with no
 *       fallback — absence fails startup instead of leaving no way into the
 *       admin module.</li>
 * </ul>
 */
class StartupConfigValidationTests {

    @Test
    void equalThrottleAndLockoutThresholdsRefuseToStart() {
        // The boundary itself: >= is the breaking condition, so equality
        // must already fail.
        assertThatThrownBy(() -> new SpringApplicationBuilder(
                HelloAuthApplication.class)
            .run(
                "--server.port=0",
                "--app.ip-throttle.max-failures=5",
                "--app.lockout.max-failures=5"))
        .hasStackTraceContaining("app.ip-throttle.max-failures")
        .hasStackTraceContaining("app.lockout.max-failures");
    }

    @Test
    void throttleAboveLockoutRefusesToStart() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(
                HelloAuthApplication.class)
            .run(
                "--server.port=0",
                "--app.ip-throttle.max-failures=10",
                "--app.lockout.max-failures=5"))
        .hasStackTraceContaining("app.ip-throttle.max-failures");
    }

    @Test
    void prodProfileWithoutAdminCredentialsRefusesToStart() {
        // CORS is supplied so the failure can only be the admin creds:
        // ProdAdminCredentialsValidator (@Profile("prod")) throws on the
        // blank-default binding during context refresh.
        assertThatThrownBy(() -> new SpringApplicationBuilder(
                HelloAuthApplication.class)
            .profiles("prod")
            .run(
                "--server.port=0",
                "--DB_URL=jdbc:h2:mem:missing-admin-tests",
                "--DB_USERNAME=sa",
                "--DB_PASSWORD=",
                "--APP_CORS_ALLOWED_ORIGINS=https://app.example.com"))
        .hasStackTraceContaining("APP_ADMIN_USERNAME");
    }

    @Test
    void prodProfileWithSubMinimumAdminPasswordRefusesToStart() {
        // AdminSeeder treats a short seed password as warn-only — fine for
        // dev defaults, unacceptable in prod where the validator must fail
        // fast below app.password-min-length (12).
        assertThatThrownBy(() -> new SpringApplicationBuilder(
                HelloAuthApplication.class)
            .profiles("prod")
            .run(
                "--server.port=0",
                "--DB_URL=jdbc:h2:mem:short-admin-pw-tests",
                "--DB_USERNAME=sa",
                "--DB_PASSWORD=",
                "--APP_ADMIN_USERNAME=root",
                "--APP_ADMIN_PASSWORD=short",
                "--APP_CORS_ALLOWED_ORIGINS=https://app.example.com"))
        .hasStackTraceContaining("APP_ADMIN_PASSWORD");
    }
}
