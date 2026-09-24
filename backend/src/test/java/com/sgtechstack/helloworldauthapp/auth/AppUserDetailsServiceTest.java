package com.sgtechstack.helloworldauthapp.auth;

import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link AppUserDetailsService} resolves each user's fine-grained
 * authorities from {@code app.security.role-mappings}, on top of the plain
 * {@code ROLE_<x>} authority. This is what makes {@code hasAuthority(...)}
 * guards (see {@link com.sgtechstack.helloworldauthapp.config.SecurityConfig})
 * actually work — without this, every authenticated user would carry only
 * their role authority and no guard matrix entry keyed by a fine-grained
 * authority (e.g. {@code HELLO_READ}) could ever match.
 */
@SpringBootTest
@ActiveProfiles("dev")
class AppUserDetailsServiceTest {

    private static final String USER_USERNAME = "mappingtestuser";
    private static final String ADMIN_USERNAME = "mappingtestadmin";

    @Autowired
    private AppUserDetailsService appUserDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.findByUsernameIgnoreCase(USER_USERNAME).ifPresent(userRepository::delete);
        userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).ifPresent(userRepository::delete);
        userRepository.save(new User(
                USER_USERNAME, "mappingtestuser@example.com", passwordEncoder.encode("irrelevant"), Role.USER, true));
        userRepository.save(new User(
                ADMIN_USERNAME, "mappingtestadmin@example.com", passwordEncoder.encode("irrelevant"), Role.ADMIN,
                true));
    }

    @Test
    void userRoleResolvesToRoleUserAndHelloReadOnly() {
        var authorities = appUserDetailsService.loadUserByUsername(USER_USERNAME).getAuthorities();

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "HELLO_READ", "ACCOUNT_SELF_MANAGE");
    }

    @Test
    void adminRoleResolvesToRoleAdminItsOwnAuthoritiesAndInheritedUserAuthorities() {
        // ADMIN_USER_* come from ADMIN's own role-mapping. HELLO_READ and
        // ACCOUNT_SELF_MANAGE are only mapped to USER, and reach ADMIN purely
        // because ROLE_ADMIN is senior to ROLE_USER in the configured
        // role-hierarchy — an admin is a data subject with the same rights over
        // their own account as anyone else.
        var authorities = appUserDetailsService.loadUserByUsername(ADMIN_USERNAME).getAuthorities();

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "ROLE_ADMIN",
                        "ADMIN_USER_READ",
                        "ADMIN_USER_EMAIL_READ",
                        "ADMIN_USER_WRITE",
                        "HELLO_READ",
                        "ACCOUNT_SELF_MANAGE");
    }
}
