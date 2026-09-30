package local.builderday.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

/** What HTTP cannot reach: a principal from before this change, and a chain that throws. */
class RequestUserFilterTest {
  private final RequestUserFilter filter = new RequestUserFilter();

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    MDC.clear();
  }

  @Test
  void should_publishNothing_when_thePrincipalIsNotAnAuthenticatedUser() throws Exception {
    // A Session stored before this change deserializes to Spring's plain User.
    authenticate(new User("testuser123", "", AuthorityUtils.createAuthorityList("ROLE_USER")));
    var request = new MockHttpServletRequest();
    var mdcSeen = new AtomicReference<String>("unset");

    filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> mdcSeen.set(MDC.get("user.id")));

    assertThat(mdcSeen.get()).isNull();
    assertThat(request.getAttribute(RequestLogFilter.USER_ID_ATTRIBUTE)).isNull();
  }

  @Test
  void should_clearMdcUserId_when_theChainThrows() {
    var userId = UUID.randomUUID();
    authenticate((AuthenticatedUser) () -> userId);
    var request = new MockHttpServletRequest();
    var mdcSeen = new AtomicReference<String>();

    assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
      mdcSeen.set(MDC.get("user.id"));
      throw new IllegalStateException("boom");
    })).isInstanceOf(IllegalStateException.class);

    assertThat(mdcSeen.get()).isEqualTo(userId.toString());
    assertThat(MDC.get("user.id")).isNull();
    assertThat(request.getAttribute(RequestLogFilter.USER_ID_ATTRIBUTE)).isEqualTo(userId.toString());
  }

  private static void authenticate(Object principal) {
    SecurityContextHolder.getContext()
        .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }
}
