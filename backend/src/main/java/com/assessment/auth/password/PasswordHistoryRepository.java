package com.assessment.auth.password;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, UUID> {

  /** Newest first. The caller takes the configured depth — current plus three previous. */
  List<PasswordHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, Limit limit);

  /** Newest first, unbounded. Used to derive {@code lastPasswordChangeAt} for the self-read. */
  List<PasswordHistory> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
