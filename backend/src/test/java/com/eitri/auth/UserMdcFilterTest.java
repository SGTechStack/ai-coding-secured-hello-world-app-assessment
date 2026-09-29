package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class UserMdcFilterTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UserMdcFilter filter = new UserMdcFilter();

    @AfterEach
    void clearThreadContext() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void exposesAuthenticatedAccountIdDuringTheRequestAndRemovesItAfterwards() throws Exception {
        authenticateAccount();
        AtomicReference<String> valueInsideChain = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> valueInsideChain.set(MDC.get(UserMdcFilter.USER_ID_MDC_KEY)));

        assertThat(valueInsideChain).hasValue(ACCOUNT_ID.toString());
        assertThat(MDC.get(UserMdcFilter.USER_ID_MDC_KEY)).isNull();
    }

    @Test
    void removesAccountIdWhenTheDownstreamRequestFails() {
        authenticateAccount();

        assertThatThrownBy(() -> filter.doFilter(
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse(),
                        (request, response) -> {
                            assertThat(MDC.get(UserMdcFilter.USER_ID_MDC_KEY)).isEqualTo(ACCOUNT_ID.toString());
                            throw new ServletException("downstream failure");
                        }))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get(UserMdcFilter.USER_ID_MDC_KEY)).isNull();
    }

    @Test
    void doesNotTreatSpringSecuritysAnonymousAuthenticationAsAUser() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "anonymous-key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        AtomicReference<String> valueInsideChain = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> valueInsideChain.set(MDC.get(UserMdcFilter.USER_ID_MDC_KEY)));

        assertThat(valueInsideChain).hasValue(null);
        assertThat(MDC.get(UserMdcFilter.USER_ID_MDC_KEY)).isNull();
    }

    private void authenticateAccount() {
        AccountPrincipal principal =
                new AccountPrincipal(ACCOUNT_ID, "johndoe", "password-hash", Role.USER);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, principal.getPassword(), principal.getAuthorities()));
    }
}
