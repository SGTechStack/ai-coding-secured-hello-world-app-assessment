package com.sgtechstack.helloworldauthapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Binds the {@code app.security} block in {@code application.yml}: the
 * configuration-owned RBAC model described in
 * {@code App-Standards/Appfw-User-Standards/Shared_Recipes/Common_Role-Based_Access_Control_Configuration.md}.
 *
 * This is the single source of truth for the URL guard matrix consumed by
 * {@link SecurityConfig#filterChain}. Nothing here is re-derived or
 * overridden in Java; the filter chain iterates {@link #urlGuards()} and
 * {@link #whitelist()} directly, so changing which endpoints require which
 * authority is a YAML change, not a code change.
 *
 * @param roleMappings  role name (matching {@code Role} enum values, no
 *                       {@code ROLE_} prefix) to the list of fine-grained
 *                       authorities that role is entitled to. Authorities
 *                       reachable via {@link #roleHierarchy()} do not need
 *                       to be repeated for a senior role.
 * @param roleHierarchy Spring Security {@code RoleHierarchyImpl} DSL
 *                       string, e.g. {@code "ROLE_ADMIN > ROLE_USER"}.
 * @param urlGuards     authority name to the list of HTTP method+path
 *                       pairs it guards. Every non-whitelisted endpoint
 *                       must have exactly one authority guarding it here,
 *                       or it is unreachable under the filter chain's
 *                       {@code denyAll()} default.
 * @param whitelist     path patterns reachable without authentication.
 * @param requireHttps  when true, the filter chain refuses plaintext and
 *                       CORS origins must use {@code https}. Defaults to
 *                       true so that opting out is a deliberate, visible
 *                       act; only the dev profile does so.
 * @param hstsMaxAge    {@code Strict-Transport-Security} max-age. Inert
 *                       until TLS terminates in front of the app, since
 *                       Spring Security only emits the header on requests
 *                       that already arrived over HTTPS.
 * @param sessionAbsoluteTimeout hard ceiling on session lifetime,
 *                       independent of idle timeout. Without it a session
 *                       kept warm by periodic activity never expires, so a
 *                       stolen cookie stays valid indefinitely.
 * @param contentSecurityPolicy policy for this service's own responses.
 *                       This service answers with JSON and serves no
 *                       scripts, styles or images, so it can permit
 *                       nothing. It does <em>not</em> protect the SPA,
 *                       which is served from another origin entirely.
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        Map<String, List<String>> roleMappings,
        String roleHierarchy,
        Map<String, List<UrlGuard>> urlGuards,
        List<String> whitelist,
        boolean requireHttps,
        Duration hstsMaxAge,
        Duration sessionAbsoluteTimeout,
        String contentSecurityPolicy,
        List<RateLimit> rateLimits,
        DataSize maxRequestBodySize
) {

    /**
     * One HTTP method + path pattern guarded by an authority.
     *
     * @param method HTTP method name, e.g. {@code "GET"}.
     * @param path   Ant-style path pattern, e.g. {@code "/api/hello"}.
     */
    public record UrlGuard(String method, String path) {
    }

    /**
     * A request-rate limit on one endpoint.
     *
     * <p>Counts requests rather than failures, which is the right measure for
     * endpoints with no notion of failure: registration and password reset
     * succeed from the caller's point of view every time, and the cost being
     * defended against is the work performed regardless of outcome.
     *
     * @param name        identifies the limit, so one caller is counted
     *                    separately per endpoint
     * @param method      HTTP method name
     * @param path        Ant-style path pattern
     * @param maxRequests requests permitted per window, per caller
     * @param window      length of the counting window
     */
    public record RateLimit(String name, String method, String path, int maxRequests, Duration window) {
    }
}
