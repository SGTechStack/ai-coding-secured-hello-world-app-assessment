package sg.example.helloauth.session;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.session.web.http.DefaultCookieSerializer;

import sg.example.helloauth.DevProfile;

/**
 * The Spring Session cookie. {@code HttpOnly} and {@code SameSite=Lax} are fixed; {@code Secure}
 * is on unless the dev profile turns it off (ADR-0004). Declared here rather than left to Boot,
 * so the same settings apply however the app is started.
 */
@Configuration(proxyBeanMethods = false)
class SessionCookieConfiguration {

    static final String COOKIE_NAME = "SESSION";

    @Bean
    DefaultCookieSerializer cookieSerializer(SessionProperties properties, Environment environment) {
        if (!properties.secureCookie() && !DevProfile.isActive(environment)) {
            throw new IllegalStateException("app.session.secure-cookie may be false only in the dev profile");
        }
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(COOKIE_NAME);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(properties.secureCookie());
        return serializer;
    }
}
