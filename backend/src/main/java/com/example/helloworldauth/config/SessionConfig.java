package com.example.helloworldauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.Session;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers the principal-name-indexed session store used to invalidate all of
 * a user's sessions on password reset (Story 7).
 *
 * <p>This deliberately does NOT enable app-wide Spring Session
 * ({@code @EnableSpringHttpSession}): the login/logout flow relies on the
 * servlet container's {@code HttpSession} plus
 * {@code HttpSessionSecurityContextRepository}, and taking that over would
 * change session-fixation and logout semantics. Instead the app keeps its
 * servlet sessions and additionally MIRRORS each authenticated session into this
 * {@link InMemoryIndexedSessionRepository} at login (see {@code LoginController}),
 * so a user's sessions can be found by principal name.
 *
 * <p>The stock {@link org.springframework.session.MapSessionRepository} does not
 * implement {@link org.springframework.session.FindByIndexNameSessionRepository}
 * (it is a plain {@code SessionRepository}), which is why this demo supplies its
 * own in-memory store that does, resolving the principal-name index from the
 * {@code SPRING_SECURITY_CONTEXT} attribute via {@code PrincipalNameIndexResolver}.
 * The password-reset confirm flow injects
 * {@code FindByIndexNameSessionRepository} and calls
 * {@code findByPrincipalName(username)} + {@code deleteById(...)}.
 */
@Configuration
public class SessionConfig {

    @Bean
    public InMemoryIndexedSessionRepository sessionRepository() {
        return new InMemoryIndexedSessionRepository(new ConcurrentHashMap<String, Session>());
    }
}
