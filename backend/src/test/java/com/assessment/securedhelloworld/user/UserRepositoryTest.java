package com.assessment.securedhelloworld.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("dev")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsUserByUsername() {
        User user = new User("alice", "alice@example.com", "hashed-password");
        userRepository.save(user);

        assertThat(userRepository.findByUsername("alice")).isPresent();
        assertThat(userRepository.existsByEmail("alice@example.com")).isTrue();
        assertThat(userRepository.existsByUsername("bob")).isFalse();
    }

    @Test
    void countsUsersByRole() {
        userRepository.save(new User("alice", "alice@example.com", "hash1"));
        User admin = new User("root", "root@example.com", "hash2");
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        assertThat(userRepository.countByRole(Role.ADMIN)).isEqualTo(1);
        assertThat(userRepository.countByRole(Role.USER)).isEqualTo(1);
    }
}
