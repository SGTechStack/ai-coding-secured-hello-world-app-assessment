package com.example.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;

/**
 * Unit tests for {@link SecurityConfig#csrfTokenRepository}: the XSRF-TOKEN
 * cookie must carry the same SameSite/Secure attributes as the session cookie
 * in each profile, plus the parent domain prod needs for a sibling-subdomain
 * SPA. Exercised directly rather than via {@code @SpringBootTest}, since every
 * Spring test context is pinned to the dev profile. Asserts on the {@link
 * Cookie} itself: MockHttpServletResponse's Set-Cookie rendering drops the
 * SameSite attribute, which a real container (Tomcat) does write out.
 */
class CsrfCookieAttributesTest {

    @Test
    void devShapedCookieIsLaxHostOnlyAndNotSecure() {
        Cookie cookie = savedCsrfCookie(SecurityConfig.csrfTokenRepository("Lax", false, ""));

        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.isHttpOnly()).isFalse();
    }

    @Test
    void prodShapedCookieIsStrictSecureAndScopedToParentDomain() {
        Cookie cookie = savedCsrfCookie(SecurityConfig.csrfTokenRepository("Strict", true, ".example.com"));

        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getDomain()).isEqualTo(".example.com");
        assertThat(cookie.isHttpOnly()).isFalse();
    }

    private static Cookie savedCsrfCookie(CookieCsrfTokenRepository repository) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken token = repository.generateToken(request);

        repository.saveToken(token, request, response);

        return response.getCookie("XSRF-TOKEN");
    }
}
