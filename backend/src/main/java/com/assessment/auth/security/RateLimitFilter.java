package com.assessment.auth.security;

import com.assessment.auth.audit.AuditAction;
import com.assessment.auth.audit.AuditEvent;
import com.assessment.auth.audit.AuditLogger;
import com.assessment.auth.audit.AuditReason;
import com.assessment.auth.common.ApiErrorCode;
import com.assessment.auth.common.ProblemDetailWriter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.slf4j.event.Level;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Per-account and per-IP rate limiting (spec.md S6, ticket 07).
 *
 * <p>Runs <strong>before authentication and outside the authorization matrix</strong>, so a
 * rejection here is never an authorization decision. That placement is also what resolves the
 * apparent conflict with Std:247: a 429 with a {@code Retry-After} would leak information about an
 * authentication <em>outcome</em>, but this rejection happens before any outcome exists. Std:260
 * makes {@code Retry-After} mandatory ("must include"), so it is not a choice.
 *
 * <p>Three counters, two of them here:
 *
 * <ul>
 *   <li>per-account, 10/min on login, keyed on the username in the body
 *   <li>per-IP, 50/min on login, keyed on {@code getRemoteAddr()} <strong>verbatim</strong>
 *   <li>per-IP on both reset endpoints — a recorded deviation from Std:124's per-account rule,
 *       because an anonymous endpoint has no account to key on
 * </ul>
 *
 * <p>The third counter — account lockout — lives in {@link AccountAuthenticationProvider} and is
 * durable in the database. These two are in-memory and operate independently of it, so repeated
 * failures from one source cannot be used to lock a legitimate user out across the board.
 *
 * <p><strong>{@code X-Forwarded-For} is ignored entirely.</strong> With no gateway guaranteed to
 * strip it, honouring it would let any caller pick their own rate-limit bucket.
 *
 * <p>Caffeine supplies {@code expireAfterAccess} eviction, so idle buckets disappear with no
 * scheduled sweep — which matters, because {@code @Scheduled} is banned application-wide.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private final String basePath;
  private final RateLimitProperties properties;
  private final ProblemDetailWriter problemDetailWriter;
  private final AuditLogger auditLogger;
  private final ObjectMapper objectMapper;

  private final Cache<String, Bucket> accountBuckets;
  private final Cache<String, Bucket> ipBuckets;
  private final Cache<String, Bucket> resetRequestBuckets;
  private final Cache<String, Bucket> resetConfirmBuckets;

  public RateLimitFilter(
      String basePath,
      RateLimitProperties properties,
      ProblemDetailWriter problemDetailWriter,
      AuditLogger auditLogger,
      ObjectMapper objectMapper) {
    this.basePath = basePath;
    this.properties = properties;
    this.problemDetailWriter = problemDetailWriter;
    this.auditLogger = auditLogger;
    this.objectMapper = objectMapper;
    this.accountBuckets = newCache();
    this.ipBuckets = newCache();
    this.resetRequestBuckets = newCache();
    this.resetConfirmBuckets = newCache();
  }

  private static Cache<String, Bucket> newCache() {
    // Two minutes of idleness on a one-minute window: long enough that an active caller keeps its
    // bucket, short enough that the map cannot grow without bound under key churn.
    return Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(2)).maximumSize(100_000).build();
  }

  /** A sliding one-minute window: greedy refill spreads the tokens across the period. */
  private static Bucket bucket(int perMinute) {
    return Bucket.builder()
        .addLimit(
            limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
        .build();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String ip = request.getRemoteAddr();

    if (matches(request, "POST", basePath + "/auth/password-reset/request")) {
      if (rejected(response, resetRequestBuckets, ip, properties.resetRequestPerMinute(), ip)) {
        return;
      }
    } else if (matches(request, "POST", basePath + "/auth/password-reset/confirm")) {
      if (rejected(response, resetConfirmBuckets, ip, properties.resetConfirmPerMinute(), ip)) {
        return;
      }
    } else if (matches(request, "POST", basePath + "/auth/login")) {
      CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
      if (rejected(response, ipBuckets, ip, properties.ipPerMinute(), ip)) {
        return;
      }
      String username = usernameFrom(cached);
      if (!username.isEmpty()
          && rejected(response, accountBuckets, username, properties.accountPerMinute(), ip)) {
        return;
      }
      chain.doFilter(cached, response);
      return;
    }

    chain.doFilter(request, response);
  }

  private boolean matches(HttpServletRequest request, String method, String path) {
    return method.equalsIgnoreCase(request.getMethod()) && path.equals(request.getRequestURI());
  }

  private String usernameFrom(CachedBodyHttpServletRequest request) {
    try {
      JsonNode body = objectMapper.readTree(request.body());
      JsonNode username = body == null ? null : body.get("username");
      return username == null || username.isNull() ? "" : username.asString();
    } catch (RuntimeException ex) {
      // A malformed body is not a rate-limit concern. The login filter will reject it.
      return "";
    }
  }

  private boolean rejected(
      HttpServletResponse response, Cache<String, Bucket> cache, String key, int perMinute, String ip)
      throws IOException {
    Bucket bucket = cache.get(key, unused -> bucket(perMinute));
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      return false;
    }
    long retryAfterSeconds =
        Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
    response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
    auditLogger.emit(
        AuditEvent.of(AuditAction.AUTHENTICATION, AuditReason.RATE_LIMIT_EXCEEDED, Level.WARN)
            .outcome("failure")
            .sourceIp(ip)
            .build());
    // Written by the shared ProblemDetailWriter. NOT sendError -- that is banned chain-wide and
    // could not carry the `code` anyway (spec.md S4, ArchUnit rule 5).
    problemDetailWriter.write(
        response, ApiErrorCode.RATE_LIMITED, "Too many requests. Try again shortly.");
    return true;
  }

  /** Exposed for tests that need to name the endpoints this filter guards. */
  public List<String> guardedPaths() {
    return List.of(
        basePath + "/auth/login",
        basePath + "/auth/password-reset/request",
        basePath + "/auth/password-reset/confirm");
  }

  /**
   * Discards all counters.
   *
   * <p>For tests only, and it exists because the counters are per-process and per-IP: an
   * integration suite makes far more than {@code ipPerMinute} requests from {@code 127.0.0.1} in a
   * minute, so without a reset between tests the limiter starves the tests that are trying to
   * assert something else. Nothing in the application calls this — a counter that could be cleared
   * at runtime would not be a rate limit.
   */
  public void resetCounters() {
    accountBuckets.invalidateAll();
    ipBuckets.invalidateAll();
    resetRequestBuckets.invalidateAll();
    resetConfirmBuckets.invalidateAll();
  }
}
