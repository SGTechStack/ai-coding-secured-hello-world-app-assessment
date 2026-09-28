package com.assessment.securedhelloworld.passwordreset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link PasswordResetToken}. {@code selector}
 * is looked up directly (it is an indexed, non-secret key); only the
 * {@code verifier} half of the presented token is ever compared against
 * {@code tokenHash}, and only for the single row {@code selector}
 * matched.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findBySelector(String selector);
}
