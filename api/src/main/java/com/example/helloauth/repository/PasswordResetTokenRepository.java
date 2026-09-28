package com.example.helloauth.repository;

import com.example.helloauth.domain.Account;
import com.example.helloauth.domain.PasswordResetToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /** Lookup by hash is why the hash is deterministic; see {@link PasswordResetToken}. */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findByAccountAndUsedAtIsNull(Account account);

    /** Deleting an account has to take its tokens with it; there is no cascade on the mapping. */
    void deleteByAccount(Account account);
}
