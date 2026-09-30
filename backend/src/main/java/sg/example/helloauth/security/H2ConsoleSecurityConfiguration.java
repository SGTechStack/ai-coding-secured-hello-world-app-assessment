package sg.example.helloauth.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.h2console.autoconfigure.H2ConsoleProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import sg.example.helloauth.DevProfile;

/**
 * The H2 console, a development tool with its own login, gets its own filter chain ahead of the
 * API's. Only the dev profile may enable it; startup fails anywhere else, so it never exists in a
 * real deployment.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("spring.h2.console.enabled")
class H2ConsoleSecurityConfiguration {

    H2ConsoleSecurityConfiguration(Environment environment) {
        if (!DevProfile.isActive(environment)) {
            throw new IllegalStateException("spring.h2.console.enabled may be true only in the dev profile");
        }
    }

    /** The console posts its own forms without our CSRF token, and draws itself in frames. */
    @Bean
    @Order(1)
    SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http, H2ConsoleProperties console) throws Exception {
        http
                .securityMatcher(PathPatternRequestMatcher.withDefaults().matcher(console.getPath() + "/**"))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }
}
