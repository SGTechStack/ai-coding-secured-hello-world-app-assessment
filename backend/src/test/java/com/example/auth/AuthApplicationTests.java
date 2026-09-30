package com.example.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.user.Role;
import com.example.auth.user.UserRepository;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration wiring checks for the application: the Spring context must
 * start, and the H2 database must be configured and reachable on startup.
 *
 * <p>{@code @ActiveProfiles("dev")} pins this test to the dev profile
 * regardless of {@code application.properties}' own default -- tests must
 * not require the env vars that application-prod.properties fails fast
 * without.
 */
@SpringBootTest
@ActiveProfiles("dev")
class AuthApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserRepository userRepository;

    @Test
    void contextLoads() {
        assertThat(dataSource).isNotNull();
    }

    @Test
    void h2DatabaseIsReachableOnStartup() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(2)).isTrue();
            assertThat(connection.getMetaData().getDatabaseProductName())
                    .isEqualToIgnoringCase("H2");
        }
    }

    /**
     * End-to-end check that indexed {@code app.local.users[n].*} binding plus
     * {@code @DefaultValue} actually works against the real
     * application-dev.properties -- the mocked {@code LocalUsersSeedRunnerTest}
     * cases can't catch a binding-prefix typo or Spring Boot behavior change.
     */
    @Test
    void localUsersFromDevPropertiesAreSeededOnStartup() {
        assertThat(userRepository.existsByUsername("alice")).isTrue();
        assertThat(userRepository.existsByUsername("bob")).isTrue();
    }

    /**
     * application-dev.properties seeds alice as an ADMIN too, so if
     * {@code LocalUsersSeedRunner} ran first, {@code AdminBootstrapRunner}'s
     * "any ADMIN exists" check would skip the bootstrap account entirely.
     * {@code @Order} on both runners pins bootstrap first; this guards it.
     */
    @Test
    void bootstrapAdminIsSeededEvenThoughALocalUserIsAlsoAdmin() {
        assertThat(userRepository.findByUsername("admin"))
                .hasValueSatisfying(user -> assertThat(user.getRole()).isEqualTo(Role.ADMIN));
    }
}
