package com.sgtechstack.helloauth.config;

import java.time.Duration;
import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.sgtechstack.helloauth.auth.LogoutAuditHandler;
import com.sgtechstack.helloauth.security.ProblemJsonSecurityHandlers;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	public static final String CSRF_HEADER = "X-CSRF-TOKEN";

	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http, CorsConfigurationSource corsConfigurationSource,
			CsrfTokenRepository csrfTokenRepository, SecurityContextRepository securityContextRepository,
			ProblemJsonSecurityHandlers problemHandlers, LogoutAuditHandler logoutAuditHandler) {
		http.cors(cors -> cors.configurationSource(corsConfigurationSource))
			// Synchronizer-token CSRF: the token lives in the server-side session and the SPA reads
			// it from GET /api/auth/csrf, so it works even when the SPA cannot read API cookies.
			.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			// No saved requests: an API 401 must not create a session.
			.requestCache(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(authorize -> authorize
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
						"/api/auth/password-reset/request", "/api/auth/password-reset/confirm")
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.requestMatchers("/api/**").authenticated()
				.anyRequest().denyAll())
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(problemHandlers)
				.accessDeniedHandler(problemHandlers))
			// POST only (CSRF-protected). Invalidates the session, which also expires the cookie.
			.logout(logout -> logout.logoutUrl("/api/auth/logout")
				.addLogoutHandler(logoutAuditHandler)
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			.headers(headers -> headers
				.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
				.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)));
		return http.build();
	}

	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
		repository.setHeaderName(CSRF_HEADER);
		return repository;
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(),
				new HttpSessionSecurityContextRepository());
	}

	@Bean
	PasswordEncoder passwordEncoder(@Value("${app.security.bcrypt-strength}") int strength) {
		return new BCryptPasswordEncoder(strength);
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(properties.allowedOrigins());
		config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
		config.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, CSRF_HEADER));
		config.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER));
		config.setAllowCredentials(true);
		config.setMaxAge(Duration.ofHours(1));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}

}
