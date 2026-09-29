package sg.securedhello.architecture.fixtures;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import sg.securedhello.user.PasswordHistoryEntry;
import sg.securedhello.user.UserAccount;

/**
 * Deliberate rule violations, fed to the rules by {@code ArchitectureRulesSelfTest} so that a rule that silently
 * matches nothing fails the build. This package is excluded from the suite-wide architecture checks, and its
 * {@code @Proves} values are placeholders, not test-plan rows.
 */
public final class ArchitectureViolations {

    private ArchitectureViolations() {
    }

    public static final class ReadsInstantNow {
        Object read() {
            return Instant.now();
        }
    }

    public static final class ReadsLocalDateNow {
        Object read() {
            return LocalDate.now();
        }
    }

    public static final class ReadsLocalDateTimeNow {
        Object read() {
            return LocalDateTime.now();
        }
    }

    public static final class ReadsZonedDateTimeNow {
        Object read() {
            return ZonedDateTime.now();
        }
    }

    public static final class ReadsOffsetDateTimeNow {
        Object read() {
            return OffsetDateTime.now();
        }
    }

    public static final class ConstructsDate {
        Object read() {
            return new Date();
        }
    }

    public static final class ReadsCurrentTimeMillis {
        long read() {
            return System.currentTimeMillis();
        }
    }

    public static final class ReadsNanoTime {
        long read() {
            return System.nanoTime();
        }
    }

    /** Allowed: time from an injected clock. */
    public static final class ReadsInjectedClock {
        Object read(Clock clock) {
            return Instant.now(clock);
        }
    }

    public static final class SleepsOnThread {
        void pause() throws InterruptedException {
            Thread.sleep(1);
        }
    }

    public static final class SleepsOnTimeUnit {
        void pause() throws InterruptedException {
            TimeUnit.MILLISECONDS.sleep(1);
        }
    }

    public static final class SendsErrorWithStatus {
        void fail(HttpServletResponse response) throws IOException {
            response.sendError(401);
        }
    }

    public static final class SendsErrorWithMessage {
        void fail(HttpServletResponse response) throws IOException {
            response.sendError(500, "boom");
        }
    }

    public static final class SendsErrorOnAWrapper {
        void fail(HttpServletResponseWrapper response) throws IOException {
            response.sendError(403);
        }
    }

    /** Allowed: setting a status and writing through the writer. */
    public static final class SetsStatus {
        void fail(HttpServletResponse response) {
            response.setStatus(401);
        }
    }

    public static final class ReadsHttpRemoteAddr {
        String read(HttpServletRequest request) {
            return request.getRemoteAddr();
        }
    }

    public static final class ReadsServletRemoteAddr {
        String read(ServletRequest request) {
            return request.getRemoteAddr();
        }
    }

    public static final class ReferencesRemoteAddr {
        Function<HttpServletRequest, String> read() {
            return HttpServletRequest::getRemoteAddr;
        }
    }

    public static final class ReadsDetailsRemoteAddress {
        String read(WebAuthenticationDetails details) {
            return details.getRemoteAddress();
        }
    }

    public static final class EnablesRememberMe {
        void configure(HttpSecurity http) throws Exception {
            http.rememberMe(Customizer.withDefaults());
        }
    }

    public static final class StoresCsrfInACookie {
        void configure(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));
        }
    }

    public static final class UsesCsrfPostProcessor {
        Object token() {
            return SecurityMockMvcRequestPostProcessors.csrf();
        }
    }

    @WebMvcTest
    public static class ProvingWebMvcSlice {
        @PlaceholderProves
        void proves() {
        }
    }

    /** Inherits its slice from a superclass. */
    public static class ProvingSubclassOfSlice extends ProvingWebMvcSliceBase {
        @PlaceholderProves
        void proves() {
        }
    }

    @WebMvcTest
    public abstract static class ProvingWebMvcSliceBase {
    }

    /** A {@code @Nested}-style inner class of a slice. */
    @WebMvcTest
    public static class OuterSlice {
        public class InnerProving {
            @PlaceholderProves
            void proves() {
            }
        }
    }

    /** Allowed: a slice with no {@code @Proves} (controller-shape tests, ADR-065). */
    @WebMvcTest
    public static class NonProvingSlice {
    }

    /** Allowed: a proving test on the full context. */
    @SpringBootTest
    public static class ProvingFullContext {
        @PlaceholderProves
        void proves() {
        }
    }

    public static final class EmitsWithThrowable {
        public void emit(Object event, Throwable cause) {
        }
    }

    public static final class AttachesCause {
        void log(org.slf4j.Logger audit, Exception e) {
            audit.atError().setCause(e).log();
        }
    }

    public static final class LogsThrowable {
        void log(org.slf4j.Logger audit, Exception e) {
            audit.error("failed", e);
        }
    }

    public static final class LogsWithoutThrowable {
        void log(org.slf4j.Logger audit) {
            audit.atInfo().addKeyValue("k", "v").log();
            audit.error("failed {}", "x");
        }
    }

    /** Named like the login converter, and looks the account up before the provider has run. */
    public static final class JsonCredentialsConverter {
        boolean exists(sg.securedhello.user.UserAccountRepository accounts, String username) {
            return accounts.findByUsername(username).isPresent();
        }
    }

    public static final class EncodesAPassword {
        Object encode(PasswordEncoder encoder) {
            return encoder.encode("a password set around the policy");
        }
    }

    public static final class WritesTheCredentialColumn {
        void write(UserAccount account) {
            account.replacePasswordHash("{bcrypt}not-through-the-policy");
        }
    }

    public static final class WritesARetainedHash {
        Object write() {
            return new PasswordHistoryEntry(UUID.randomUUID(), "{bcrypt}not-through-the-policy", Instant.EPOCH);
        }
    }

    public static final class ReadsTheCredentialColumn {
        Object read(UserAccount account) {
            return account.getPasswordHash();
        }
    }

    /** Authenticates on a reset path, where a capped account would be refused the reset that restores it. */
    public static final class AuthenticatesOnTheResetPath {
        Object authenticate(org.springframework.security.authentication.AuthenticationManager manager) {
            return manager.authenticate(null);
        }
    }
}
