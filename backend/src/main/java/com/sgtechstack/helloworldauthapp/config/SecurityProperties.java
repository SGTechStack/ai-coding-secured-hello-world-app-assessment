package com.sgtechstack.helloworldauthapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        Map<String, List<String>> roleMappings,
        String roleHierarchy,
        Map<String, List<UrlGuard>> urlGuards,
        List<String> whitelist
) {

    /**
     * One HTTP method + path pattern guarded by an authority.
     *
     * @param method HTTP method name, e.g. {@code "GET"}.
     * @param path   Ant-style path pattern, e.g. {@code "/api/hello"}.
     */
    public record UrlGuard(String method, String path) {
    }
}
