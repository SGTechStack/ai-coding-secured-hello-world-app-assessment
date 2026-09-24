package com.sgtechstack.helloworldauthapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@link RoleHierarchy} bean, built from {@code
 * app.security.role-hierarchy}, actually expresses ROLE_ADMIN inheriting
 * ROLE_USER's reachable authorities. {@link SecurityConfigTest} covers the
 * end-to-end effect of this at the HTTP layer (an admin session reaching a
 * USER-guarded route purely through inheritance).
 */
@SpringBootTest
@ActiveProfiles("dev")
class RoleHierarchyConfigTest {

    @Autowired
    private RoleHierarchy roleHierarchy;

    @Test
    void adminRoleReachesUserRoleThroughHierarchy() {
        List<GrantedAuthority> adminAuthority = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));

        Collection<? extends GrantedAuthority> reachable =
                roleHierarchy.getReachableGrantedAuthorities(adminAuthority);

        assertThat(reachable)
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void userRoleDoesNotReachAdminRoleThroughHierarchy() {
        List<GrantedAuthority> userAuthority = List.of(new SimpleGrantedAuthority("ROLE_USER"));

        Collection<? extends GrantedAuthority> reachable =
                roleHierarchy.getReachableGrantedAuthorities(userAuthority);

        assertThat(reachable)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }
}
