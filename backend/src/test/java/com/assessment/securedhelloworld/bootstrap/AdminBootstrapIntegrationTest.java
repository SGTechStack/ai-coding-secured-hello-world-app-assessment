package com.assessment.securedhelloworld.bootstrap;

import com.assessment.securedhelloworld.user.Role;
import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class AdminBootstrapIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminBootstrapProperties adminBootstrapProperties;

    @Test
    void seedsAdminAccountOnFreshDatabase() {
        User admin = userRepository.findByUsername(adminBootstrapProperties.getUsername()).orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.isForcePasswordChange()).isTrue();
        assertThat(passwordEncoder.matches(adminBootstrapProperties.getPassword(), admin.getPasswordHash())).isTrue();
    }

    @Test
    void doesNotSeedASecondAdminWhenOneAlreadyExists() {
        long adminCountBefore = userRepository.countByRole(Role.ADMIN);

        new AdminBootstrapRunner(userRepository, passwordEncoder, adminBootstrapProperties).run();

        long adminCountAfter = userRepository.countByRole(Role.ADMIN);
        assertThat(adminCountAfter).isEqualTo(adminCountBefore);
    }
}
