package org.eds.demo.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.UUID;
import org.eds.demo.user.domain.AppUserDetails;
import org.eds.demo.user.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuthTracingFilterTest {

  private final AuthTracingFilter filter = new AuthTracingFilter();

  @Mock private FilterChain filterChain;

  @AfterEach
  void clearSecurityContextAndMdc() {
    SecurityContextHolder.clearContext();
    MDC.clear();
  }

  @Test
  void setsUserIdAndUsernameWhenAuthenticated() throws Exception {
    var userId = new UserId(UUID.randomUUID());
    var principal = new AppUserDetails(userId, "alice", "Alice", "secret", true, List.of());
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          assertThat(MDC.get("user.id")).isEqualTo(userId.value().toString());
          assertThat(MDC.get("user.name")).isEqualTo("alice");
        });
  }

  @Test
  void setsAnonymousWhenNoAuthentication() throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          assertThat(MDC.get("user.id")).isEqualTo("anonymous");
          assertThat(MDC.get("user.name")).isEqualTo("anonymous");
        });
  }

  @Test
  void setsAnonymousWhenPrincipalIsNotAppUserDetails() throws Exception {
    var auth = new UsernamePasswordAuthenticationToken("plain-string-principal", null);
    SecurityContextHolder.getContext().setAuthentication(auth);

    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          assertThat(MDC.get("user.id")).isEqualTo("anonymous");
          assertThat(MDC.get("user.name")).isEqualTo("anonymous");
        });
  }

  @Test
  void removesMdcKeysAfterFilterChain() throws Exception {
    var userId = new UserId(UUID.randomUUID());
    var principal = new AppUserDetails(userId, "alice", "Alice", "secret", true, List.of());
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

    filter.doFilterInternal(
        new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);

    assertThat(MDC.get("user.id")).isNull();
    assertThat(MDC.get("user.name")).isNull();
  }

  @Test
  void doesNotClearUnrelatedMdcKeysAfterFilterChain() throws Exception {
    MDC.put("request.id", "some-request-id");

    filter.doFilterInternal(
        new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);

    assertThat(MDC.get("request.id")).isEqualTo("some-request-id");
  }

  @Test
  void proceedsWithFilterChainWhenAuthenticated() throws Exception {
    var principal =
        new AppUserDetails(new UserId(UUID.randomUUID()), "bob", "Bob", "secret", true, List.of());
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
  }
}
