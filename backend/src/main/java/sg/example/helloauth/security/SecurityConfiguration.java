package sg.example.helloauth.security;

import static org.springframework.security.web.header.writers.ClearSiteDataHeaderWriter.Directive.CACHE;
import static org.springframework.security.web.header.writers.ClearSiteDataHeaderWriter.Directive.COOKIES;
import static org.springframework.security.web.header.writers.ClearSiteDataHeaderWriter.Directive.STORAGE;

import java.util.List;
import java.util.Map;

import jakarta.servlet.DispatcherType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.authentication.password.CompromisedPasswordDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HeaderWriterLogoutHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ClearSiteDataHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import sg.example.helloauth.account.AccountPrincipal;
import sg.example.helloauth.account.Role;
import sg.example.helloauth.api.ApiProperties;
import sg.example.helloauth.api.RequestBodyLimitFilter;
import sg.example.helloauth.audit.AuditLogger;
import sg.example.helloauth.loginprotection.AccountLockout;
import sg.example.helloauth.loginprotection.ThrottleFilter;
import sg.example.helloauth.loginprotection.Throttling;
import sg.example.helloauth.logging.UserIdMdcFilter;
import sg.example.helloauth.password.CompromisedPasswordCheckUnavailableException;
import sg.example.helloauth.session.SessionControl;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    private static final String BCRYPT = "bcrypt";
    private static final int BCRYPT_COST = 12;

    private static final long ONE_YEAR_IN_SECONDS = 31_536_000;

    /**
     * The API's single filter chain. CSRF keeps Spring's defaults: tokens stored in the session
     * and XOR-masked. {@code csrf.spa()} must never be used, because it switches to cookie storage.
     * Security errors come out as Problem Details like every other error.
     */
    @Bean
    SecurityFilterChain apiFilterChain(HttpSecurity http, ApiProperties api, SessionControl sessionControl,
            AccountLockout lockout, Throttling throttling, AuditLogger audit,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors) throws Exception {
        SecurityErrorResponses securityErrors = new SecurityErrorResponses(errors, audit);
        LoginHandlers loginHandlers = new LoginHandlers(sessionControl, lockout, throttling, audit, errors);
        http
                .addFilterBefore(sessionControl.lifetimeFilter(), SecurityContextHolderFilter.class)
                .addFilterAfter(new UserIdMdcFilter(), SecurityContextHolderFilter.class)
                // After CORS, so the SPA can read the rejection.
                .addFilterAfter(new RequestBodyLimitFilter(api.maxRequestBodySize(), errors), CorsFilter.class)
                // After CSRF, so only a request the SPA could have sent uses up an attempt.
                .addFilterAfter(new ThrottleFilter(throttling, api, errors), CsrfFilter.class)
                .csrf(Customizer.withDefaults())
                .cors(Customizer.withDefaults())
                // Spring's defaults already add nosniff, X-Frame-Options: DENY and no-cache on every
                // response. HSTS goes only on HTTPS responses, as RFC 6797 requires.
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(ONE_YEAR_IN_SECONDS)
                                .includeSubDomains(true))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; object-src 'none'"))
                        .permissionsPolicyHeader(permissions -> permissions
                                .policy("geolocation=(), microphone=(), camera=()")))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, api.path("/csrf"), "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, api.path("/register"), api.path("/login"),
                                api.path("/password-reset/request"), api.path("/password-reset/confirm")).permitAll()
                        .requestMatchers(api.path("/admin/**")).hasRole(Role.ADMIN.name())
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        // Naming the page switches off Spring's generated HTML login page.
                        .loginPage(api.path("/login"))
                        .loginProcessingUrl(api.path("/login"))
                        .successHandler(loginHandlers)
                        .failureHandler(loginHandlers))
                // Logout keeps CSRF protection. Invalidating the session also makes Spring
                // Session expire its cookie. Clear-Site-Data is written on HTTPS requests only.
                .logout(logout -> logout
                        .logoutUrl(api.path("/logout"))
                        .addLogoutHandler((request, response, authentication) -> {
                            // An already-expired session has no one to log out.
                            if (authentication != null
                                    && authentication.getPrincipal() instanceof AccountPrincipal principal) {
                                audit.loggedOut(principal.id(), request);
                            }
                        })
                        .addLogoutHandler(new HeaderWriterLogoutHandler(
                                new ClearSiteDataHeaderWriter(CACHE, COOKIES, STORAGE)))
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK)))
                // One session per user: a new login ends the older one, which then gets 401.
                .sessionManagement(session -> session
                        .maximumSessions(1)
                        .sessionRegistry(sessionControl.sessionRegistry())
                        .expiredSessionStrategy(securityErrors))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrors)
                        .accessDeniedHandler(securityErrors));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties cors, ApiProperties api) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "X-Correlation-ID"));
        // Not CORS-safelisted, so the SPA couldn't tell a Throttled user when to try again.
        config.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(api.path("/**"), config);
        return source;
    }

    /**
     * Form login's provider. Spring Security already makes every failure cost the same one
     * password check: an unknown username is checked against a dummy hash, and a Locked or
     * Disabled Account's password is still checked before it is turned away.
     * <p>
     * It refuses a correct password that the compromised-password check reports as breached,
     * but when the check is unavailable the login goes ahead: an outage must not lock every
     * Account out. Setting a password, by contrast, fails closed.
     */
    @Bean
    DaoAuthenticationProvider authenticationProvider(UserDetailsService accounts, PasswordEncoder passwordEncoder,
            CompromisedPasswordChecker compromisedPasswords) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(accounts);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setCompromisedPasswordChecker(password -> {
            try {
                return compromisedPasswords.check(password);
            } catch (CompromisedPasswordCheckUnavailableException ex) {
                log.warn("Compromised-password check unavailable; login proceeds unchecked", ex);
                return new CompromisedPasswordDecision(false);
            }
        });
        return provider;
    }

    /** BCrypt (cost 12) by default; the stored prefix lets a later move to Argon2id be configuration only. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder(BCRYPT, Map.of(BCRYPT, new BCryptPasswordEncoder(BCRYPT_COST)));
    }
}
