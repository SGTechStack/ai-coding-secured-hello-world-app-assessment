package com.example.helloauth.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * {@code roleOf} must return the first {@code ROLE_*} authority, skipping the
 * {@code FACTOR_*} authorities Security 7 adds to authenticated tokens. The
 * HTTP seam can only produce the real authority ordering (ROLE_ first), so
 * this invokes the helper directly with a factor-first list — the ordering
 * the filter exists to defend against.
 */
class AuthControllerRoleOfTests {

    private static String roleOf(Authentication authentication) throws Exception {
        Method roleOf = AuthController.class
            .getDeclaredMethod("roleOf", Authentication.class);
        roleOf.setAccessible(true);
        return (String) roleOf.invoke(null, authentication);
    }

    private static Authentication authWith(String... authorities) {
        return UsernamePasswordAuthenticationToken.authenticated("alice", "n/a",
            java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList());
    }

    @Test
    void roleOfSkipsFactorAuthorities() throws Exception {
        assertThat(roleOf(authWith("FACTOR_PASSWORD", "ROLE_ADMIN")))
            .isEqualTo("ADMIN");
    }

    @Test
    void roleOfReturnsRoleWithoutPrefix() throws Exception {
        assertThat(roleOf(authWith("ROLE_USER", "FACTOR_PASSWORD")))
            .isEqualTo("USER");
    }

    @Test
    void roleOfFallsBackToUserWhenNoRoleAuthority() throws Exception {
        assertThat(roleOf(authWith("FACTOR_PASSWORD"))).isEqualTo("USER");
        assertThat(roleOf(authWith())).isEqualTo("USER");
    }
}
