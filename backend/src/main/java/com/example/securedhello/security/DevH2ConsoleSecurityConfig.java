package com.example.securedhello.security;

import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Local-only access to the H2 console. This chain does not exist outside the {@code dev} profile,
 * where the API chain's default deny covers the console path.
 */
@Configuration
@Profile("dev")
class DevH2ConsoleSecurityConfig {

	@Bean
	@Order(1)
	SecurityFilterChain h2ConsoleSecurityFilterChain(HttpSecurity http) throws Exception {
		return http.securityMatcher(PathRequest.toH2Console())
			.authorizeHttpRequests((auth) -> auth.anyRequest().permitAll())
			.csrf((csrf) -> csrf.disable())
			.headers((headers) -> headers.frameOptions((frame) -> frame.sameOrigin()))
			.build();
	}

}
