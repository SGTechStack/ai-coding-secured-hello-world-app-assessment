package sg.securedhello.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.http.HttpMethod;
import org.springframework.validation.annotation.Validated;

/**
 * The authorization matrix (ADR-043): which routes are open to anyone, and which role reaches which route. It binds
 * once at context refresh; a restart is the only reload path.
 *
 * <p>{@link SecurityConfig} applies it in a fixed order: the whitelist first, then the explicit role-definition
 * {@code denyAll()}, then the role guards, then {@code anyRequest().denyAll()}. A route with no row is denied.
 *
 * @param whitelist    routes open to anyone, including anonymous callers
 * @param roles        for each role name ({@code USER} or {@code ADMIN}), the routes that role may call
 * @param devWhitelist routes open to anyone under {@code dev} only, for dev-only handlers; set in
 *                     {@code application-dev.yml}, applied only under {@code dev}, and refused at startup outside it,
 *                     so outside dev such a route is denied as any unknown route is
 */
@Validated
@ConfigurationProperties("app.security.authorization")
public record AuthorizationMatrix(
        @DefaultValue List<@Valid Route> whitelist,
        @DefaultValue Map<@Pattern(regexp = "USER|ADMIN") String, List<@Valid Route>> roles,
        @DefaultValue List<@Valid Route> devWhitelist) {

    /**
     * The role guards by route: each route once, with every role that may call it, in matrix order. Spring applies the
     * first matching rule, so a route two roles share (the self-read) must be one {@code hasAnyRole} rule, never one
     * rule per role, or the first role's rule would refuse the second.
     */
    public Map<Route, List<String>> rolesByRoute() {
        Map<Route, List<String>> byRoute = new LinkedHashMap<>();
        roles.forEach((role, routes) -> routes.forEach(route ->
                byRoute.computeIfAbsent(route, key -> new ArrayList<>()).add(role)));
        return byRoute;
    }

    /**
     * One matrix entry: an explicit method and a path pattern.
     *
     * @param method the HTTP method; every entry names one
     * @param path   a {@code PathPattern} under {@code /api}
     */
    public record Route(@NotNull HttpMethod method, @NotNull @Pattern(regexp = "/api(/.*)?") String path) {
    }
}
