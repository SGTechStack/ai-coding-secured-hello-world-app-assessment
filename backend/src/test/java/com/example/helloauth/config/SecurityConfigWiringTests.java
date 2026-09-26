package com.example.helloauth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Bean-wiring tests for {@link SecurityConfig}. The HTTP-seam tests only
 * exercise these factory methods during context creation — which coverage
 * tools attribute to whichever test boots the context — so the wiring itself
 * is asserted here by invoking the methods directly. These are deliberately
 * behavioral: not just non-null, but the effect each bean must have.
 */
@SpringBootTest
class SecurityConfigWiringTests {

    private static final String PASSWORD = "correct horse battery";

    /** HttpSecurity is a Boot-provided prototype bean — a fresh instance per
     *  lookup, same as the one the real chains are built from. */
    @Autowired
    ObjectProvider<HttpSecurity> httpSecurity;

    @Autowired
    CsrfTokenRepository csrfTokenRepository;

    @Autowired
    SecurityContextRepository securityContextRepository;

    @Test
    void securityFilterChainBuilds() throws Exception {
        assertThat(new SecurityConfig().securityFilterChain(
                httpSecurity.getObject(), csrfTokenRepository,
                securityContextRepository))
            .isNotNull();
    }

    @Test
    void h2ConsoleSecurityFilterChainBuilds() throws Exception {
        assertThat(new SecurityConfig()
                .h2ConsoleSecurityFilterChain(httpSecurity.getObject()))
            .isNotNull();
    }

    @Test
    void passwordEncoderIsBcrypt() {
        PasswordEncoder encoder = new SecurityConfig().passwordEncoder();
        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
        String hash = encoder.encode(PASSWORD);
        assertThat(hash).startsWith("$2");
        assertThat(encoder.matches(PASSWORD, hash)).isTrue();
    }

    @Test
    void securityContextRepositoryIsHttpSessionBacked() {
        assertThat(new SecurityConfig().securityContextRepository())
            .isInstanceOf(HttpSessionSecurityContextRepository.class);
    }

    @Test
    void csrfTokenRepositoryIsSessionBacked() {
        assertThat(new SecurityConfig().csrfTokenRepository())
            .isInstanceOf(HttpSessionCsrfTokenRepository.class);
    }

    @Test
    void csrfTokenIsStoredInTheSessionAndNeverInACookie() {
        // Synchronizer Token pattern: saving a token writes a session
        // attribute and no Set-Cookie at all; loading reads it back from the
        // same session only.
        CsrfTokenRepository repository = new SecurityConfig().csrfTokenRepository();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken token = repository.generateToken(request);
        repository.saveToken(token, request, response);

        assertThat(token.getHeaderName()).isEqualTo("X-XSRF-TOKEN");
        assertThat(response.getCookies()).isEmpty();
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(repository.loadToken(request).getToken()).isEqualTo(token.getToken());

        MockHttpServletRequest otherSession = new MockHttpServletRequest();
        assertThat(repository.loadToken(otherSession)).isNull();
    }

    @Test
    void csrfRejectionIsAMarkedProblemDetailOtherDenialsStayBare() throws Exception {
        AccessDeniedHandler handler = SecurityConfig.accessDeniedHandler();

        MockHttpServletResponse csrf = new MockHttpServletResponse();
        handler.handle(new MockHttpServletRequest(), csrf,
            new MissingCsrfTokenException(null));
        assertThat(csrf.getStatus()).isEqualTo(403);
        assertThat(csrf.getContentType()).isEqualTo("application/problem+json");
        assertThat(csrf.getContentAsString()).contains("\"title\":\"Invalid CSRF token\"");

        MockHttpServletResponse role = new MockHttpServletResponse();
        handler.handle(new MockHttpServletRequest(), role,
            new AccessDeniedException("not an admin"));
        assertThat(role.getStatus()).isEqualTo(403);
        assertThat(role.getContentAsString()).doesNotContain("CSRF");
    }

    @Test
    void sessionAuthenticationStrategyIsComposite() {
        // The composite is what gives login both CSRF rotation and
        // session-id change — a bare or null strategy would skip both.
        assertThat(new SecurityConfig()
                .sessionAuthenticationStrategy(csrfTokenRepository))
            .isInstanceOf(CompositeSessionAuthenticationStrategy.class);
    }

    @Test
    void clockProducesInstants() {
        assertThat(new SecurityConfig().clock()).isNotNull();
        assertThat(new SecurityConfig().clock().instant())
            .isInstanceOf(java.time.Instant.class);
    }

    @Test
    void authenticationEventPublisherForwardsEvents() {
        RecordingApplicationEventPublisher events =
            new RecordingApplicationEventPublisher();
        AuthenticationEventPublisher publisher =
            new SecurityConfig().authenticationEventPublisher(events);

        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
            "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        publisher.publishAuthenticationSuccess(auth);

        assertThat(events.published)
            .anyMatch(AuthenticationSuccessEvent.class::isInstance);
    }

    @Test
    void authenticationManagerAuthenticatesAndPublishesEvents() {
        RecordingApplicationEventPublisher events =
            new RecordingApplicationEventPublisher();
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(PASSWORD);
        UserDetailsService userDetailsService = username ->
            org.springframework.security.core.userdetails.User
                .withUsername(username)
                .password(hash)
                .roles("USER")
                .build();

        AuthenticationManager manager = new SecurityConfig().authenticationManager(
            userDetailsService, encoder,
            new DefaultAuthenticationEventPublisher(events));

        Authentication auth = manager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated("alice", PASSWORD));

        assertThat(auth.isAuthenticated()).isTrue();
        // The event publisher must be wired onto the ProviderManager —
        // ticket-11's lockout listeners depend on these events firing.
        assertThat(events.published)
            .anyMatch(AuthenticationSuccessEvent.class::isInstance);
    }

    @Test
    void authenticationManagerRejectsBadCredentials() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(PASSWORD);
        UserDetailsService userDetailsService = username ->
            org.springframework.security.core.userdetails.User
                .withUsername(username)
                .password(hash)
                .roles("USER")
                .build();

        AuthenticationManager manager = new SecurityConfig().authenticationManager(
            userDetailsService, encoder,
            new DefaultAuthenticationEventPublisher(
                new RecordingApplicationEventPublisher()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            manager.authenticate(UsernamePasswordAuthenticationToken
                .unauthenticated("alice", "wrong password here")))
            .isInstanceOf(
                org.springframework.security.core.AuthenticationException.class);
    }

    @Test
    void corsConfigurationMatchesTheRatifiedAllowList() {
        // The allow-list comes from AppProperties (YAML owns the dev default)
        // — set it the way binding would and prove it reaches the config.
        AppProperties properties = new AppProperties();
        properties.getCors().setAllowedOrigins(List.of("http://localhost:3000"));
        CorsConfigurationSource source =
            new SecurityConfig().corsConfigurationSource(properties);
        CorsConfiguration config = source.getCorsConfiguration(
            new MockHttpServletRequest("GET", "/api/hello"));

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins())
            .containsExactly("http://localhost:3000");
        assertThat(config.getAllowedMethods()).containsExactlyInAnyOrder(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(config.getAllowedHeaders()).containsExactlyInAnyOrder(
            "Content-Type", "X-XSRF-TOKEN", "Authorization");
        assertThat(config.getAllowCredentials()).isTrue();
    }

    @Test
    void sessionCookieCustomizerAppliesEveryConfiguredAttribute() {
        // Set every cookie attribute so each conditional leg is observable.
        ServerProperties properties = new ServerProperties();
        Cookie cookie = properties.getServlet().getSession().getCookie();
        cookie.setName("MYSESSION");
        cookie.setPath("/api");
        cookie.setDomain("example.com");
        cookie.setMaxAge(Duration.ofMinutes(5));
        // httpOnly=false: the serializer defaults to HttpOnly=true, so only
        // the false direction proves the property is actually applied.
        cookie.setHttpOnly(false);
        cookie.setSecure(true);
        cookie.setSameSite(Cookie.SameSite.STRICT);

        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        DefaultCookieSerializerCustomizer customizer =
            new SecurityConfig().sessionCookieCustomizer(properties);
        customizer.customize(serializer);

        MockHttpServletResponse response = new MockHttpServletResponse();
        serializer.writeCookieValue(new CookieSerializer.CookieValue(
            new MockHttpServletRequest(), response, "session-id-value"));
        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);

        // DefaultCookieSerializer base64-encodes the value — assert the name,
        // not the raw value.
        assertThat(setCookie).isNotNull()
            .contains("MYSESSION=")
            .contains("Path=/api")
            .contains("Domain=example.com")
            .contains("Max-Age=300")
            .doesNotContain("HttpOnly")
            .contains("Secure")
            .contains("SameSite=Strict");
    }

    @Test
    void sessionCookieCustomizerAppliesHttpOnlyTrue() {
        // The other direction: httpOnly=true must appear in the emitted
        // cookie (this is the production configuration).
        ServerProperties properties = new ServerProperties();
        properties.getServlet().getSession().getCookie().setHttpOnly(true);

        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        new SecurityConfig().sessionCookieCustomizer(properties)
            .customize(serializer);

        MockHttpServletResponse response = new MockHttpServletResponse();
        serializer.writeCookieValue(new CookieSerializer.CookieValue(
            new MockHttpServletRequest(), response, "v"));

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
            .contains("HttpOnly");
    }

    /** Captures published application events for wiring assertions. */
    static class RecordingApplicationEventPublisher
            implements ApplicationEventPublisher {
        final List<Object> published = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            published.add(event);
        }
    }
}
