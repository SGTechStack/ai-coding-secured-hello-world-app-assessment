package com.sgtechstack.helloworldauthapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The H2 web console's security rules, isolated onto their own filter chain
 * and gated on the {@code dev} profile.
 *
 * <p>The console needs three concessions no API endpoint should ever get: it
 * must be reachable without authentication, it posts plain HTML forms with no
 * CSRF token, and it renders itself inside an iframe. Previously all three
 * were granted on the single application-wide chain, justified by the
 * reasoning that the console servlet only exists when
 * {@code spring.h2.console.enabled=true}. That reasoning protected the
 * <em>servlet</em> but not the <em>rules</em>: the whitelist entry, the CSRF
 * exemption and a global {@code frameOptions=sameOrigin} relaxation shipped in
 * every profile, with the H2 driver on the runtime classpath and a blank
 * {@code sa} password behind it. Anything that flipped the console on — a
 * stray property, a copied config, a future profile — exposed unauthenticated
 * full SQL read/write over the whole database.
 *
 * <p>Now the rules and the servlet are gated by the same switch. This class is
 * {@code @Profile("dev")}, so outside dev the bean does not exist,
 * {@code /h2-console/**} matches neither the whitelist nor any url-guard, and
 * {@code SecurityConfig}'s terminal {@code denyAll()} refuses it. The
 * concessions cannot leak onto the API chain because they are not expressed
 * there at all.
 *
 * <p>Ordered ahead of {@link SecurityConfig#filterChain} so console requests
 * are matched here first; everything else falls through to the API chain.
 */
@Configuration
@Profile("dev")
public class H2ConsoleSecurityConfig {

    public static final String H2_CONSOLE_PATHS = "/h2-console/**";

    @Bean
    @Order(1)
    public SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(H2_CONSOLE_PATHS)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));

        return http.build();
    }
}
