package com.sgtechstack.helloworldauthapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgtechstack.helloworldauthapp.auth.IpLoginThrottle;
import com.sgtechstack.helloworldauthapp.auth.IpThrottleFilter;
import com.sgtechstack.helloworldauthapp.auth.LoginFailureHandler;
import com.sgtechstack.helloworldauthapp.auth.LoginSuccessHandler;
import com.sgtechstack.helloworldauthapp.auth.LogoutSuccessResponseHandler;
import com.sgtechstack.helloworldauthapp.auth.RestAccessDeniedHandler;
import com.sgtechstack.helloworldauthapp.auth.RestAuthenticationEntryPoint;
import com.sgtechstack.helloworldauthapp.auth.RestSessionExpiredStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyAuthoritiesMapper;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Session-cookie auth: form login produces a server-side session backed by
 * a secure cookie, logout invalidates it, and CSRF protection covers every
 * state-changing endpoint since a cookie is an ambient credential a
 * malicious page could otherwise ride along with.
 *
 * CORS is wired here via {@code .cors(...)} using the {@link CorsConfig}
 * bean, not left to Spring MVC's {@code WebMvcConfigurer}: login and
 * logout are handled entirely by Spring Security's filter chain, which
 * runs before MVC dispatch, so MVC-level CORS configuration never sees
 * those requests.
 *
 * Session-fixation protection is Spring Security's default: the session ID
 * is rotated on successful authentication (ChangeSessionIdAuthenticationStrategy),
 * so no explicit override is configured below.
 *
 * This chain carries no CSRF exemptions and no relaxed frame-options. The H2
 * console needs both, and gets them on its own dev-gated chain in
 * {@link H2ConsoleSecurityConfig} rather than from concessions made here.
 *
 * <h2>Configuration-owned authorization</h2>
 *
 * The {@code authorizeHttpRequests} rules in {@link #filterChain} are not
 * literals in this class. They are built at startup from {@code
 * app.security} in {@code application.yml} (bound onto {@link
 * SecurityProperties}), following the "Configuration-Owned RBAC" pattern
 * in
 * {@code App-Standards/Appfw-User-Standards/Shared_Recipes/Common_Role-Based_Access_Control_Configuration.md}:
 * a role-to-authority mapping, a role hierarchy, and a URL guard matrix all
 * live in YAML, so changing which endpoints require which authority is a
 * config change reviewable in a PR diff, not a Java change. The chain ends
 * in {@code denyAll()} rather than {@code authenticated()} — deny-by-default,
 * per the standard — so an endpoint that is neither whitelisted nor covered
 * by a {@code url-guards} entry is unreachable by anyone, including an
 * authenticated user.
 *
 * Reachability across the role hierarchy needs two cooperating pieces, not
 * one:
 * <ul>
 *   <li>{@link #roleHierarchy} plus the {@link RoleHierarchyAuthoritiesMapper}
 *       attached in {@link #authenticationProvider} expand a {@code
 *       ROLE_<x>} authority to every {@code ROLE_<y>} reachable below it in
 *       the hierarchy string.</li>
 *   <li>That expansion alone does not reach fine-grained authorities like
 *       {@code HELLO_READ} — {@code RoleHierarchyAuthoritiesMapper} only
 *       expands {@code ROLE_*} strings to other {@code ROLE_*} strings.
 *       Granting a senior role every fine-grained authority mapped to a
 *       junior role is therefore resolved once, in {@code
 *       AppUserDetailsService}, which walks the same {@link RoleHierarchy}
 *       to union every reachable role's {@code role-mappings} entry before
 *       building the principal's authority set.</li>
 * </ul>
 *
 * Two deliberate deviations from the standard's reference implementation,
 * both scoped to this app's size and shape:
 * <ul>
 *   <li>No {@code ImmutableSecurityHandler}: that pattern blocks role
 *       mutation via a {@code @RepositoryEventHandler} hook on a Spring
 *       Data REST resource, and this app never exposes {@code
 *       UserRepository} that way. {@code
 *       com.sgtechstack.helloworldauthapp.admin.RoleMutationGuard} plays
 *       the equivalent role instead: a single, independently testable
 *       checkpoint that the one sanctioned role-mutation path
 *       ({@code AdminUserManagementService#changeRole}) routes through.</li>
 *   <li>{@code role-mappings} in YAML is the source of truth for which
 *       fine-grained authorities a role holds, but it is not fed directly
 *       into {@code hasRole(...)} calls here — enforcement is entirely
 *       {@code hasAuthority(...)} against {@code url-guards}, with {@code
 *       role-mappings} consumed by {@code AppUserDetailsService} at
 *       authentication time to build each principal's authority set.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Tracks each principal's active {@code HttpSession}s so a password
     * reset (or any future "log out everywhere" action) can invalidate
     * every session belonging to a user, not just the one making the
     * request. Requires {@link org.springframework.security.web.session.HttpSessionEventPublisher}
     * (registered as a servlet listener bean below) so the registry is
     * notified when sessions are created and destroyed.
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
    }

    /**
     * Built from {@code app.security.role-hierarchy} rather than a literal
     * Java constant, per the configuration-owned RBAC model (see
     * {@link SecurityProperties}). Today this only expresses
     * {@code ROLE_ADMIN > ROLE_USER}, but a future senior role (e.g.
     * {@code MANAGER}) slots in by editing YAML, not this class.
     */
    @Bean
    public RoleHierarchy roleHierarchy(SecurityProperties securityProperties) {
        return RoleHierarchyImpl.fromHierarchy(securityProperties.roleHierarchy());
    }

    /**
     * Explicit {@code DaoAuthenticationProvider}, rather than relying on
     * Spring Boot's implicit auto-configuration from the {@code
     * UserDetailsService}/{@code PasswordEncoder} beans, so a {@link
     * RoleHierarchyAuthoritiesMapper} can be attached. Without this, {@code
     * Authentication.getAuthorities()} would only ever contain the single
     * literal {@code ROLE_<x>} authority {@link UserPrincipal} assigns; with
     * it, an authenticated {@code ROLE_ADMIN} principal's authorities are
     * expanded at login to include every authority reachable via {@link
     * #roleHierarchy}, e.g. {@code ROLE_USER}. This is what lets {@code
     * hasAuthority}/{@code hasRole} checks in the filter chain honour the
     * hierarchy without re-deriving it per request.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            RoleHierarchy roleHierarchy
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setAuthoritiesMapper(new RoleHierarchyAuthoritiesMapper(roleHierarchy));
        return provider;
    }

    /**
     * The API chain. Ordered after {@link H2ConsoleSecurityConfig}'s dev-only
     * chain (which claims {@code /h2-console/**} when that profile is active)
     * so that everything else lands here.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            LoginSuccessHandler loginSuccessHandler,
            LoginFailureHandler loginFailureHandler,
            LogoutSuccessResponseHandler logoutSuccessHandler,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            IpLoginThrottle ipLoginThrottle,
            ObjectMapper objectMapper,
            SessionRegistry sessionRegistry,
            RestSessionExpiredStrategy sessionExpiredStrategy,
            RestAccessDeniedHandler accessDeniedHandler,
            SecurityProperties securityProperties
    ) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // Runs before Spring Security's own login-processing filter, so
                // a throttled IP is rejected before an authentication attempt
                // is even made.
                .addFilterBefore(
                        new IpThrottleFilter(ipLoginThrottle, objectMapper),
                        UsernamePasswordAuthenticationFilter.class
                )
                // No CSRF exemptions. The H2 console needs one (it posts plain
                // HTML forms with no token), but it gets it on its own dev-only
                // chain in H2ConsoleSecurityConfig rather than punching a hole
                // in the chain that serves the real API.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                )
                // Frame-options is left at Spring Security's DENY default. It
                // was previously relaxed to sameOrigin application-wide so the
                // H2 console could render itself in an iframe — which weakened
                // clickjacking protection on every API response to benefit a
                // dev-only tool. That relaxation now lives on the H2 console's
                // own chain.
                // Session-fixation protection: Spring Security's default session
                // management already rotates the session ID on authentication
                // (changeSessionId()). maximumSessions/sessionRegistry here is for
                // tracking, not capping, concurrent sessions: it feeds SessionRegistry
                // so a password reset can invalidate every session for a user, not
                // to limit how many a user may have open.
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(sessionExpiredStrategy)
                )
                // Configuration-owned URL guard matrix: every rule below comes
                // from app.security in application.yml (see
                // SecurityProperties), not a literal in this class. Adding or
                // changing which endpoints require which authority is a YAML
                // change. The chain ends in denyAll() (zero-trust), not
                // authenticated(): an endpoint that is neither whitelisted nor
                // covered by a url-guard entry is unreachable by anyone,
                // including an authenticated user, rather than defaulting to
                // "any logged-in user may call it".
                .authorizeHttpRequests(authorize -> {
                    securityProperties.whitelist()
                            .forEach(pattern -> authorize.requestMatchers(pattern).permitAll());

                    securityProperties.urlGuards().forEach((authority, guards) ->
                            guards.forEach(guard -> authorize
                                    .requestMatchers(HttpMethod.valueOf(guard.method()), guard.path())
                                    .hasAuthority(authority)));

                    authorize.anyRequest().denyAll();
                })
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(loginSuccessHandler)
                        .failureHandler(loginFailureHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(logoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .deleteCookies("SESSION")
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );

        return http.build();
    }
}
