package local.builderday.common.ratelimit.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

/**
 * A row of the shared rate-limit store. Bucket rows belong to Bucket4j, which owns the {@code state} blob
 * (deliberately unmapped) and writes {@code expires_at}. Serialisation rows are application-owned and expired by the
 * application itself.
 */
@Entity
@Table(name = RateLimitBucketEntity.TABLE)
public class RateLimitBucketEntity implements Persistable<String> {
  public static final String TABLE = "rate_limit_buckets";

  @Id
  @Column(length = 128)
  private String id;

  /** Epoch millis after which cleanup may delete the row; null until Bucket4j first writes a bucket row. */
  @Column(name = "expires_at")
  private Long expiresAt;

  protected RateLimitBucketEntity() {}

  public RateLimitBucketEntity(String id) { this.id = id; }

  public RateLimitBucketEntity(String id, long expiresAt) {
    this.id = id;
    this.expiresAt = expiresAt;
  }

  @Override public String getId() { return id; }

  /** Pushes back when cleanup may delete this row. Only used for application-owned serialisation rows. */
  public void setExpiresAt(long epochMillis) { this.expiresAt = epochMillis; }

  /** Rows are only ever inserted by the application, so {@code save} persists directly instead of merging. */
  @Override public boolean isNew() { return true; }
}
