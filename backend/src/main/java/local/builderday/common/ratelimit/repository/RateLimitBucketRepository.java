package local.builderday.common.ratelimit.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import local.builderday.common.ratelimit.repository.entity.RateLimitBucketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/**
 * The shared rate-limit store. The application checks row existence, pre-creates rows, and row-locks serialisation
 * rows; Bucket4j reads and writes bucket state itself.
 */
public interface RateLimitBucketRepository extends JpaRepository<RateLimitBucketEntity, String> {
  /** {@code SELECT ... FOR UPDATE}: blocks until no other transaction holds this row. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RateLimitBucketEntity> findLockedById(String id);
}
