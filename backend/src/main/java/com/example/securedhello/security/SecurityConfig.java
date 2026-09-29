package com.example.securedhello.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.example.securedhello.audit.AuditAction;
import com.example.securedhello.audit.AuditEvent;
import com.example.securedhello.audit.AuditLog;
import com.example.securedhello.config.ApiProperties;
import com.example.securedhello.config.CorsProperties;
import com.example.securedhello.logging.AccountIdMdcFilter;
import com.example.securedhello.logging.AccountIdentified;
import com.example.securedhello.web.ProblemResponses;

import jakarta.servlet.DispatcherType;

@Configuration
@EnableWebSecurity
// @PreAuthorize on the Account administration service, in addition to the URL rules below.
@EnableMethodSecurity
class SecurityConfig {

	private static final String PERMISSIONS_POLICY = "accelerometer=(), autoplay=(), camera=(), "
			+ "display-capture=(), fullscreen=(), geolocation=(), gyroscope=(), magnetometer=(), "
			+ "microphone=(), midi=(), payment=(), usb=()";

	/** Session-stored CSRF tokens (ADR 0002); shared with {@link SessionControl}, which replaces them. */
	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		return new HttpSessionCsrfTokenRepository();
	}

	@Bean
	@Order(10)
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ApiProperties api, CorsProperties cors,
			AuditLog auditLog, CsrfTokenRepository csrfTokenRepository, SessionControl sessionControl)
			throws Exception {
		http.cors((c) -> c.configurationSource(corsConfigurationSource(api, cors)))
			.csrf((csrf) -> csrf.csrfTokenRepository(csrfTokenRepository))
			// Size check runs before CSRF, authorization and every controller.
			.addFilterBefore(new RequestBodyLimitFilter(api.maxRequestBodyBytes()), CsrfFilter.class)
			// Idle and absolute Session timeouts, once the Session's authentication is loaded.
			.addFilterBefore(new SessionLifetimeFilter(sessionControl), CsrfFilter.class)
			// user.id in MDC once the Session's authentication is loaded, before any rejection.
			.addFilterBefore(new AccountIdMdcFilter(), CsrfFilter.class)
			.authorizeHttpRequests((auth) -> auth
			.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
			.requestMatchers(HttpMethod.GET, api.path("/csrf")).permitAll()
			.requestMatchers(HttpMethod.POST, api.path("/register"), api.path("/login"),
					api.path("/password-reset/request"), api.path("/password-reset/confirm")).permitAll()
			.requestMatchers(HttpMethod.GET, api.path("/hello"), api.path("/me")).authenticated()
			.requestMatchers(HttpMethod.POST, api.path("/logout")).authenticated()
			.requestMatchers(HttpMethod.PATCH, api.path("/me/password")).authenticated()
			// Every method and path under /admin, known or not; the service checks the role again.
			.requestMatchers(api.path("/admin/**")).hasRole("ADMIN")
			// Default deny: anything not matched above is refused.
			.anyRequest().denyAll())
			.headers((headers) -> headers
				// Sent on every response; the deployment terminates TLS in front of the app (ADR 0001).
				.httpStrictTransportSecurity((hsts) -> hsts.requestMatcher(AnyRequestMatcher.INSTANCE)
					.maxAgeInSeconds(31_536_000)
					.includeSubDomains(true))
				.contentSecurityPolicy((csp) -> csp.policyDirectives("default-src 'self'; object-src 'none'"))
				.frameOptions((frame) -> frame.deny())
				.contentTypeOptions(Customizer.withDefaults())
				.permissionsPolicyHeader((policy) -> policy.policy(PERMISSIONS_POLICY)))
			.exceptionHandling((ex) -> ex.authenticationEntryPoint(authenticationEntryPoint())
				.accessDeniedHandler(accessDeniedHandler(auditLog)));
		return http.build();
	}

	/** One CORS policy for the whole API base path; no per-endpoint overrides. */
	private static CorsConfigurationSource corsConfigurationSource(ApiProperties api, CorsProperties cors) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(cors.allowedOrigins());
		config.setAllowCredentials(true);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "X-Correlation-ID"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration(api.path("/**"), config);
		return source;
	}

	private static AccessDeniedHandler accessDeniedHandler(AuditLog auditLog) {
		return (request, response, exception) -> {
			if (exception instanceof CsrfException) {
				auditLog.record(AuditEvent.failure(AuditAction.ACCESS_CONTROL, "csrf_invalid")
					.request(request)
					.sessionHashOf(request));
				ProblemResponses.write(response, HttpStatus.FORBIDDEN, "csrf_invalid",
						"The CSRF token is missing or invalid.");
			}
			else {
				AuditEvent event = AuditEvent.failure(AuditAction.ACCESS_CONTROL, "access_denied").request(request);
				Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
				if (authentication != null && authentication.getPrincipal() instanceof AccountIdentified account) {
					event.userId(account.accountId());
				}
				else {
					event.sessionHashOf(request);
				}
				auditLog.record(event);
				ProblemResponses.write(response, HttpStatus.FORBIDDEN, "access_denied", "Access is denied.");
			}
		};
	}

	private static AuthenticationEntryPoint authenticationEntryPoint() {
		return (request, response, exception) -> ProblemResponses.write(response, HttpStatus.UNAUTHORIZED,
				"authentication_required", "Authentication is required.");
	}

}
