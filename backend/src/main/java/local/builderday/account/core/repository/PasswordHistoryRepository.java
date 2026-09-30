package local.builderday.account.core.repository;

import java.util.List;
import java.util.UUID;
import local.builderday.account.core.repository.entity.PasswordHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistoryEntity, UUID> {
  /** Newest first. */
  List<PasswordHistoryEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
