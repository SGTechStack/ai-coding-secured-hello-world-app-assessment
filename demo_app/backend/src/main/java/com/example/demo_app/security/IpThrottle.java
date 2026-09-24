package com.example.demo_app.security;

import com.example.demo_app.web.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An in-memory, single-instance, per-IP limiter: at most {@link ThrottleProperties.Limit#
 * maxAttempts()} counted attempts per client address in a fixed window that starts at the first
 * counted attempt and ends {@link ThrottleProperties.Limit#window()} later, on the injected {@link
 * Clock}.
 *
 * <p>The client address is always {@link HttpServletRequest#getRemoteAddr()}. Behind a proxy, the
 * {@code prod} profile's {@code server.forward-headers-strategy: native} lets Tomcat resolve it
 * from {@code X-Forwarded-For} for trusted proxies only; this class never reads that header, so a
 * client can't pick its own key.
 *
 * <p>At most {@code maxTrackedIps} addresses are remembered. Past that, the address whose window
 * started longest ago is forgotten, so a flood of addresses can't exhaust memory; the price is that
 * such a flood can reset an older address's count.
 *
 * <p>Each endpoint has its own instance (see {@code ThrottleConfig}). Login calls {@link #check}
 * before authenticating and {@link #recordAttempt} only on failure; an endpoint where every request
 * counts calls {@link #acquire}.
 */
public final class IpThrottle {

  private final int maxAttempts;
  private final Duration window;
  private final Clock clock;
  private final Map<String, Window> windows;

  /** One address's current window. */
  private record Window(Instant start, int attempts) {}

  IpThrottle(ThrottleProperties.Limit limit, int maxTrackedIps, Clock clock) {
    this.maxAttempts = limit.maxAttempts();
    this.window = limit.window();
    this.clock = clock;
    // Insertion order: a key only moves to the end when its window restarts.
    this.windows =
        new LinkedHashMap<>() {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Window> eldest) {
            return size() > maxTrackedIps;
          }
        };
  }

  /**
   * Throws {@link TooManyRequestsException} if the client has used up its attempts in the current
   * window. Counts nothing.
   */
  public synchronized void check(HttpServletRequest request) {
    Instant now = clock.instant();
    Window current = currentWindow(request.getRemoteAddr(), now);
    if (current != null && current.attempts() >= maxAttempts) {
      throw new TooManyRequestsException(retryAfter(current, now));
    }
  }

  /** Counts one attempt by the client, opening a new window if none is running. */
  public synchronized void recordAttempt(HttpServletRequest request) {
    String ip = request.getRemoteAddr();
    Instant now = clock.instant();
    Window current = currentWindow(ip, now);
    windows.put(
        ip,
        current == null
            ? new Window(now, 1)
            : new Window(current.start(), current.attempts() + 1));
  }

  /** {@link #check} then {@link #recordAttempt}: every allowed request counts. */
  public synchronized void acquire(HttpServletRequest request) {
    check(request);
    recordAttempt(request);
  }

  /** How many addresses are remembered right now; never more than {@code maxTrackedIps}. */
  synchronized int trackedAddresses() {
    return windows.size();
  }

  /** The address's running window, or {@code null} (forgetting it) if it has ended. */
  private Window currentWindow(String ip, Instant now) {
    Window current = windows.get(ip);
    if (current != null && !now.isBefore(current.start().plus(window))) {
      windows.remove(ip);
      return null;
    }
    return current;
  }

  /** Whole seconds until the window ends, rounded up and at least one. */
  private Duration retryAfter(Window current, Instant now) {
    long millis = Duration.between(now, current.start().plus(window)).toMillis();
    return Duration.ofSeconds(Math.max(1, (millis + 999) / 1000));
  }
}
