package com.example.securedhello.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.securedhello.HttpIntegrationTest;
import com.example.securedhello.entity.Role;
import com.example.securedhello.entity.User;
import com.example.securedhello.repository.UserRepository;

/**
 * Integration tests for Admin bootstrap (issue 09): when no ADMIN exists, one
 * is seeded from configuration with a BCrypt-hashed password; seeding is
 * idempotent when an ADMIN already exists.
 */
class AdminBootstrapIntegrationTest extends HttpIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminBootstrapService adminBootstrapService;

    @BeforeEach
    void setUp() {
        resetClock();
        userRepository.deleteAll();
    }

    @Test
    void seedsAdminFromConfigurationWhenNoAdminExists() {
        adminBootstrapService.seedIfMissing();

        User admin = userRepository.findByUsername("admin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
        // Password is stored as a BCrypt hash, hashed identically to any account.
        assertThat(admin.getPasswordHash()).startsWith("$2");
        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    void seedingIsIdempotentWhenAnAdminAlreadyExists() {
        adminBootstrapService.seedIfMissing();
        adminBootstrapService.seedIfMissing();

        long adminCount = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .count();
        assertThat(adminCount).isEqualTo(1);
    }
}
