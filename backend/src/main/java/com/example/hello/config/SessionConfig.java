package com.example.hello.config;

import com.example.hello.auth.session.InMemoryIndexedSessionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Spring Session wiring. Sessions live server-side in the repository below; the browser
 * only ever holds an opaque session id in a hardened cookie.
 */
@Configuration
@EnableSpringHttpSession
public class SessionConfig {

  public static final String SESSION_COOKIE_NAME = "SESSION";

  @Bean
  public InMemoryIndexedSessionRepository sessionRepository(AppSecurityProperties props) {
    return new InMemoryIndexedSessionRepository(props.sessionTimeout());
  }

  @Bean
  public CookieSerializer cookieSerializer(AppSecurityProperties props) {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName(SESSION_COOKIE_NAME);
    serializer.setCookiePath("/");
    serializer.setUseHttpOnlyCookie(true);
    serializer.setUseSecureCookie(props.cookieSecure());
    serializer.setSameSite(props.cookieSameSite());
    return serializer;
  }

  @Bean
  public HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
  }
}
