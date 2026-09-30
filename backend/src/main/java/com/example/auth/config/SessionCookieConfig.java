package com.example.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pins the {@code SESSION} cookie's security attributes (ADR-0009) instead of trusting Boot's
 * mapping. When there is no embedded web server -- a WAR deployment, or MockMvc tests -- Boot copies
 * them from the servlet container's {@code SessionCookieConfig}, whose defaults are HttpOnly=false
 * and no SameSite, overriding Spring Session's own safe defaults. This customizer runs last, so the
 * cookie is HttpOnly with the profile's SameSite/Secure in every deployment mode.
 */
@Configuration
public class SessionCookieConfig {

    @Bean
    public DefaultCookieSerializerCustomizer sessionCookieHardening(
            @Value("${server.servlet.session.cookie.same-site:Strict}") String sameSite,
            @Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
        return serializer -> {
            serializer.setUseHttpOnlyCookie(true);
            serializer.setSameSite(sameSite);
            serializer.setUseSecureCookie(secure);
        };
    }
}
