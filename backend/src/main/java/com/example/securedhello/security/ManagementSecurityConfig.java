package com.example.securedhello.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.example.securedhello.config.ManagementProperties;

/**
 * The Actuator management port's own filter chain (story 111). It exists because Spring Boot runs the
 * management port's server inside the application's servlet container, so the API chain's
 * default-deny would otherwise refuse the two endpoints an operator reads there.
 * <p>
 * Ordered before {@code SecurityConfig}'s API chain but matching only the management port, so the API
 * chain and the API's public paths are untouched: on the API port, an Actuator path is still refused
 * by the API chain's default-deny. The port exposes only {@code health} and {@code prometheus} and is
 * bound to {@code management.server.address}, by default loopback only. That binding is not the only
 * protection (IM8 as-13): {@code prometheus} needs the scraper's HTTP Basic credential
 * ({@link ScrapeCredential}), and refuses every scrape when none is configured. {@code health} stays
 * open, because it answers with the status alone and probes carry no credential. Nothing here gets a
 * Session. The README's deployment note still says never to route the port publicly.
 */
@Configuration
class ManagementSecurityConfig {

	@Bean
	@Order(5)
	SecurityFilterChain managementSecurityFilterChain(HttpSecurity http, Environment environment,
			ManagementProperties properties) throws Exception {
		http.securityMatcher(managementPort(environment))
			// The only paths the management server maps are the exposed endpoints; anything else is 404.
			// prometheus needs the scrape credential; health is status only, and unmapped paths stay 404.
			.authorizeHttpRequests((auth) -> auth.requestMatchers("/actuator/prometheus", "/actuator/prometheus/**")
				.hasAuthority(ScrapeCredential.SCRAPER)
				.anyRequest()
				.permitAll())
			// This chain's own manager, so the scraper can never authenticate as an Account or vice versa.
			.authenticationManager(ScrapeCredential.authenticationManager(properties))
			.httpBasic(Customizer.withDefaults())
			// Nothing here reads or writes a Session, so a scrape never creates one.
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		return http.build();
	}

	/**
	 * Requests that arrived on the management port. Read from the running servers rather than from
	 * {@code management.server.port}, because that may be {@code 0}, leaving the operating system to
	 * choose the port. Matches nothing unless a management server is running on a port of its own, so
	 * this chain can never widen the API port, whatever the configuration says.
	 * <p>
	 * Read per request, never cached. Caching this was tried and reverted: a cached value was observed
	 * pointing at a port nothing serves, while the live management port fell through to the API chain's
	 * default-deny and both endpoints answered 401. Under {@code management.server.port=0} the
	 * management server can rebind to a new port during one application context's life — instrumenting
	 * the cache showed a single matcher instance, in a single {@code Environment}, whose management port
	 * moved while the API port stayed put, which is why the fault only appears once a run has started
	 * several contexts.
	 * <p>
	 * Caching also buys nothing. A request the API port serves costs one property lookup and returns at
	 * the first check; the second lookup only runs for traffic that did arrive on the management port,
	 * which is a few scrapes a minute and never a hot path.
	 */
	private static RequestMatcher managementPort(Environment environment) {
		return (HttpServletRequest request) -> {
			Integer management = environment.getProperty("local.management.port", Integer.class);
			if (management == null || management.intValue() != request.getLocalPort()) {
				return false;
			}
			Integer api = environment.getProperty("local.server.port", Integer.class);
			// Sharing the API's port means Actuator is served by the API chain, which denies it by default.
			return api == null || api.intValue() != management.intValue();
		};
	}

}
