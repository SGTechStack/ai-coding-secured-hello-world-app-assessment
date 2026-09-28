package com.assessment.securedhelloworld.lifecycle;

import com.assessment.securedhelloworld.user.Role;
import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the IM8 ac-4 periodic access review: accounts past their
 * declared expiry are disabled, and ADMIN accounts not on the declared
 * authorised-admin baseline are demoted back to USER.
 */
@SpringBootTest
@ActiveProfiles("dev")
class AccessReviewJobTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccessReviewJob job;

    private User createUser(String username, Role role) {
        User user = new User(username, username + "@example.com", passwordEncoder.encode("correct-horse-battery"));
        user.setRole(role);
        return userRepository.save(user);
    }

    @Test
    void disablesAccountPastItsDeclaredExpiry() {
        User expired = createUser("expired-user", Role.USER);
        expired.setAccountExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        userRepository.save(expired);

        job.reviewAccess();

        User reloaded = userRepository.findByUsername("expired-user").orElseThrow();
        assertThat(reloaded.isEnabled()).isFalse();
    }

    @Test
    void doesNotDisableAccountWithFutureExpiry() {
        User notYetExpired = createUser("not-yet-expired-user", Role.USER);
        notYetExpired.setAccountExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        userRepository.save(notYetExpired);

        job.reviewAccess();

        User reloaded = userRepository.findByUsername("not-yet-expired-user").orElseThrow();
        assertThat(reloaded.isEnabled()).isTrue();
    }

    @Test
    void doesNotDisableAccountWithNoExpiryDeclared() {
        createUser("no-expiry-user", Role.USER);

        job.reviewAccess();

        User reloaded = userRepository.findByUsername("no-expiry-user").orElseThrow();
        assertThat(reloaded.isEnabled()).isTrue();
    }

    @Test
    void revokesAdminRoleFromAccountNotOnTheAuthorisedBaseline() {
        // "admin" is on the default baseline (app.security.access-review.
        // authorised-admin-usernames in application.yml); this account is
        // not, so it represents privilege drift the review must catch.
        createUser("rogue-admin", Role.ADMIN);

        job.reviewAccess();

        User reloaded = userRepository.findByUsername("rogue-admin").orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void leavesBaselineAuthorisedAdminUntouched() {
        User bootstrapAdmin = userRepository.findByUsername("admin").orElseThrow();
        assertThat(bootstrapAdmin.getRole()).isEqualTo(Role.ADMIN);

        job.reviewAccess();

        User reloaded = userRepository.findByUsername("admin").orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(Role.ADMIN);
    }
}
