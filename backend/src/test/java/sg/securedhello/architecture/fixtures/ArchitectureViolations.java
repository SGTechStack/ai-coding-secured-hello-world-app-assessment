package sg.securedhello.architecture.fixtures;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.IsoChronology;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

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

import sg.securedhello.mfa.TotpFactorRemoval;
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

    public static final class ReadsLocalTimeNow {
        Object read() {
            return LocalTime.now();
        }
    }

    public static final class ReadsYearNow {
        Object read() {
            return Year.now();
        }
    }

    public static final class ReadsYearMonthNow {
        Object read() {
            return YearMonth.now();
        }
    }

    /** A zone is not a clock: {@code now(ZoneId)} still reads the system clock. */
    public static final class ReadsNowInAZone {
        Object read() {
            return LocalDate.now(ZoneId.of("UTC"));
        }
    }

    public static final class BuildsSystemUtcClock {
        Object read() {
            return Clock.systemUTC();
        }
    }

    public static final class BuildsSystemDefaultZoneClock {
        Object read() {
            return Clock.systemDefaultZone();
        }
    }

    public static final class ReadsCalendarInstance {
        Object read() {
            return Calendar.getInstance();
        }
    }

    public static final class ReferencesInstantNow {
        Supplier<Instant> read() {
            return Instant::now;
        }
    }

    public static final class ReferencesDateConstructor {
        Supplier<Date> read() {
            return Date::new;
        }
    }

    public static final class ConstructsGregorianCalendar {
        Object read() {
            return new GregorianCalendar();
        }
    }

    public static final class ConstructsGregorianCalendarInAZone {
        Object read() {
            return new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT);
        }
    }

    public static final class ReadsChronologyDateNow {
        Object read() {
            return IsoChronology.INSTANCE.dateNow();
        }
    }

    public static final class BuildsSystemInstantSource {
        Object read() {
            return InstantSource.system();
        }
    }

    /** Allowed: {@code Instant::now} resolved to {@code now(Clock)}, and a clock derived from an injected one. */
    public static final class ReferencesNowWithAClock {
        Function<Clock, Instant> read() {
            return Instant::now;
        }

        Clock tick(Clock clock) {
            return Clock.tick(clock, Duration.ofSeconds(1));
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

    /** A controller that reaches a repository itself, around the guarded service method. */
    @org.springframework.web.bind.annotation.RestController
    public static final class ControllerReachesARepository {
        Object read(sg.securedhello.user.UserAccountRepository accounts) {
            return accounts.findAll();
        }
    }

    /** A controller that runs SQL itself. */
    @org.springframework.web.bind.annotation.RestController
    public static final class ControllerRunsSql {
        Object read(org.springframework.jdbc.core.JdbcTemplate jdbc) {
            return jdbc.queryForList("SELECT 1");
        }
    }

    /** A controller that holds a JPA entity manager. */
    @org.springframework.web.bind.annotation.RestController
    public static final class ControllerUsesJpa {
        Object read(jakarta.persistence.EntityManager entities) {
            return entities.find(UserAccount.class, UUID.randomUUID());
        }
    }

    /** A plain {@code @Controller}, not a {@code @RestController}, that uses a JDBC client. */
    @org.springframework.stereotype.Controller
    public static final class PlainControllerUsesJdbcClient {
        Object read(org.springframework.jdbc.core.simple.JdbcClient jdbc) {
            return jdbc.sql("SELECT 1").query().listOfRows();
        }
    }

    /** A controller that goes through a service, as the admin controller does. */
    @org.springframework.web.bind.annotation.RestController
    public static final class ControllerUsesAService {
        Object read(sg.securedhello.admin.AdminActions actions) {
            return actions.toString();
        }
    }

    /** Disables an account without AdminActionGuard. */
    public static final class DisablesAroundTheGuard {
        void disable(UserAccount account) {
            account.setEnabled(false);
        }
    }

    /** Promotes an account without AdminActionGuard. */
    public static final class PromotesAroundTheGuard {
        void promote(UserAccount account) {
            account.setRole("ADMIN");
        }
    }

    /** Deletes an account without AdminActionGuard. */
    public static final class DeletesAroundTheGuard {
        void delete(sg.securedhello.user.Tombstones tombstones, UserAccount account) {
            tombstones.deleteLeavingTombstone(account, java.util.UUID.randomUUID());
        }
    }

    /** Removes a TOTP factor from outside the guarded service. */
    public static final class ResetsAFactorAroundTheGuard {
        void reset(TotpFactorRemoval removal) {
            removal.remove(UUID.randomUUID());
        }
    }

    /** Detects a terminal by {@code System.console()} nullness, as the runner must not (ADR-073). */
    public static final class DetectsATerminal {
        boolean interactive() {
            return System.console() != null;
        }
    }

    /** Returns an entity from a controller, bare and inside generic wrappers. */
    @org.springframework.web.bind.annotation.RestController
    public static final class ReturnsAnEntity {
        public UserAccount one() {
            return null;
        }
    }

    /** Returns an entity as a generic argument, two levels down. */
    @org.springframework.web.bind.annotation.RestController
    public static final class ReturnsAWrappedEntity {
        public org.springframework.http.ResponseEntity<java.util.List<? extends UserAccount>> list() {
            return null;
        }
    }

    /** Returns an array of entities. */
    @org.springframework.web.bind.annotation.RestController
    public static final class ReturnsAnEntityArray {
        public UserAccount[] all() {
            return null;
        }
    }

    /** Returns a response record, as every real controller does. */
    @org.springframework.web.bind.annotation.RestController
    public static final class ReturnsARecord {
        public record View(String name) {
        }

        public org.springframework.http.ResponseEntity<java.util.List<View>> list() {
            return null;
        }
    }

    /** Encodes bytes in the credential-token alphabet, as a fabricated token would be. */
    public static final class FabricatesATokenString {
        String token(byte[] bytes) {
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
    }

    /** Draws random bytes, the other half of a fabricated token. */
    public static final class DrawsRandomBytes {
        byte[] bytes() {
            byte[] bytes = new byte[32];
            new java.security.SecureRandom().nextBytes(bytes);
            return bytes;
        }
    }
}
