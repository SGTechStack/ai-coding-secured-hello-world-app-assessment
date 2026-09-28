package com.assessment.securedhelloworld.lifecycle;

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
 * Verifies the IM8 ac-3 dormancy sweep: accounts idle beyond the
 * configured threshold are disabled, accounts within the threshold (or
 * newly created with no login history yet) are left alone.
 */
@SpringBootTest
@ActiveProfiles("dev")
class DormantAccountDisablingJobTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DormantAccountDisablingJob job;

    private User createUser(String username) {
        return new User(username, username + "@example.com", passwordEncoder.encode("correct-horse-battery"));
    }

    @Test
    void disablesAccountWithStaleLastLogin() {
        User dormant = createUser("dormant-user");
        dormant.setLastLoginAt(Instant.now().minus(120, ChronoUnit.DAYS));
        userRepository.save(dormant);

        job.disableDormantAccounts();

        User reloaded = userRepository.findByUsername("dormant-user").orElseThrow();
        assertThat(reloaded.isEnabled()).isFalse();
    }

    @Test
    void doesNotDisableAccountWithRecentLastLogin() {
        User active = createUser("active-user");
        active.setLastLoginAt(Instant.now().minus(5, ChronoUnit.DAYS));
        userRepository.save(active);

        job.disableDormantAccounts();

        User reloaded = userRepository.findByUsername("active-user").orElseThrow();
        assertThat(reloaded.isEnabled()).isTrue();
    }

    @Test
    void doesNotDisableFreshlyCreatedAccountThatHasNeverLoggedIn() {
        // createdAt defaults to "now" at construction, so a freshly
        // created account (lastLoginAt still null) is never eligible
        // for the dormancy sweep's "never logged in" branch until it
        // has existed for the full dormancy window.
        userRepository.save(createUser("fresh-user"));

        job.disableDormantAccounts();

        User reloaded = userRepository.findByUsername("fresh-user").orElseThrow();
        assertThat(reloaded.isEnabled()).isTrue();
    }

    @Test
    void alreadyDisabledAccountIsNotReProcessed() {
        User alreadyDisabled = createUser("already-disabled-user");
        alreadyDisabled.setLastLoginAt(Instant.now().minus(200, ChronoUnit.DAYS));
        alreadyDisabled.setEnabled(false);
        userRepository.save(alreadyDisabled);

        // Should not throw and should simply skip it (query filters on enabled = true).
        job.disableDormantAccounts();

        User reloaded = userRepository.findByUsername("already-disabled-user").orElseThrow();
        assertThat(reloaded.isEnabled()).isFalse();
    }
}
