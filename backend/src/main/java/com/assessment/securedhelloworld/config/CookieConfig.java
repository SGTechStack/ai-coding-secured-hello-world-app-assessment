package com.assessment.securedhelloworld.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Attributes for Spring Session's own "SESSION" cookie. {@code server.servlet.session.cookie.*}
 * properties do NOT apply once Spring Session is on the classpath (ticket 01) — it uses this
 * serializer instead, which is why these attributes are set explicitly here rather than left to
 * Spring Boot defaults (IM8 as-11).
 */
@Configuration
public class CookieConfig {

    @Value("${app.security.cookie-secure:false}")
    private boolean cookieSecure;

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(cookieSecure);
        serializer.setSameSite("Lax");
        return serializer;
    }
}
