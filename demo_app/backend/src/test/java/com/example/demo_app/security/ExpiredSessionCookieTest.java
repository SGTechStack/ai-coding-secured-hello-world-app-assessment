package com.example.demo_app.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

/** The logout cookie must match the configured session cookie, or the browser keeps the old one. */
class ExpiredSessionCookieTest {

  @Test
  void copiesTheConfiguredDomain() {
    org.springframework.boot.web.server.Cookie config =
        new org.springframework.boot.web.server.Cookie();
    config.setDomain("example.test");

    Cookie cookie = SecurityConfig.expiredSessionCookie(config);

    assertThat(cookie.getDomain()).isEqualTo("example.test");
    assertThat(cookie.getMaxAge()).isZero();
  }

  @Test
  void leavesTheDomainUnsetWhenNoneIsConfigured() {
    Cookie cookie =
        SecurityConfig.expiredSessionCookie(new org.springframework.boot.web.server.Cookie());

    assertThat(cookie.getDomain()).isNull();
  }
}
