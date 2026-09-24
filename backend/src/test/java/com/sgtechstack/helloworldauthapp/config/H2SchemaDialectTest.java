package com.sgtechstack.helloworldauthapp.config;

import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetTokenRepository;
import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the database-level constraints in the migration actually fire on H2.
 *
 * <h2>Why this class exists</h2>
 *
 * Standardising on one engine meant rewriting a migration that had used two
 * PostgreSQL features H2 does not have. Neither omission would have failed a
 * build, and one of them was a real control rather than a performance detail, so
 * the substitute needed a test rather than a comment.
 *
 * <ul>
 *   <li><strong>Case-insensitive uniqueness.</strong> PostgreSQL expressed it as a
 *       unique index on {@code lower(username)}. H2 rejects expression indexes
 *       outright, so the expression moved into a {@code GENERATED ALWAYS AS}
 *       column with an ordinary unique index on it. Same guarantee, one more
 *       column — but only if H2 really enforces it, which is what the first two
 *       tests check.</li>
 *   <li><strong>The partial index</strong> on enabled admins became a plain
 *       two-column index. That one is a performance nicety with no behaviour to
 *       assert, so it is noted here and not tested.</li>
 * </ul>
 *
 * <h2>Why it matters more than it looks</h2>
 *
 * Registration already calls {@code existsByUsernameIgnoreCase} before inserting,
 * so in ordinary use the database constraint never fires. It is the backstop for
 * the case the application check cannot cover: a read followed by a write is not
 * atomic, so two concurrent registrations for {@code Alice} and {@code alice} can
 * both pass the check and both insert. Without the constraint the result is two
 * accounts that answer the same case-insensitive login lookup, with which one wins
 * left to the query planner.
 */
@SpringBootTest
@ActiveProfiles("dev")
class H2SchemaDialectTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void twoUsernamesDifferingOnlyInCaseCannotBothExist() {
        userRepository.saveAndFlush(newUser("CaseTest", "casetest-one@example.com"));

        // saveAndFlush, not save: the violation is raised by the database, and a
        // plain save inside a test's transaction might not reach it before the
        // assertion runs.
        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("casetest", "casetest-two@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void twoEmailsDifferingOnlyInCaseCannotBothExist() {
        userRepository.saveAndFlush(newUser("emailcase-one", "Shared@Example.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("emailcase-two", "shared@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theGeneratedColumnsAreInvisibleToTheEntityModel() {
        // The generated columns exist in the schema and deliberately not on the
        // User entity. This is what makes that safe: Hibernate's schema validation
        // checks that every column the entities expect is present, and does not
        // object to columns it has never heard of.
        //
        // Asserted because the whole application boots with ddl-auto: validate
        // against this migration, so if the extra columns did upset the validator,
        // every @SpringBootTest in the suite would fail rather than just this one.
        User saved = userRepository.saveAndFlush(newUser("generated.col", "generated@example.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(userRepository.findByUsernameIgnoreCase("GENERATED.COL"))
                .as("the case-insensitive lookup the indexes exist to serve")
                .isPresent();
    }

    @Test
    void ordinaryDistinctAccountsAreStillAccepted() {
        // The control case. Both assertions above would also pass if the unique
        // indexes were far too broad and rejected everything.
        userRepository.saveAndFlush(newUser("distinct.one", "distinct-one@example.com"));
        userRepository.saveAndFlush(newUser("distinct.two", "distinct-two@example.com"));

        assertThat(userRepository.findAllByOrderByCreatedAtAsc()).hasSize(2);
    }

    private User newUser(String username, String email) {
        return new User(username, email, passwordEncoder.encode("dialect-test-password-1234"), Role.USER, true);
    }
}
