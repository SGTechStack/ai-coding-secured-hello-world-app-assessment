package org.eds.demo.common;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.eds.demo.user.domain.UserId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass
@EntityListeners({AuditingEntityListener.class, AuditUserListener.class})
public abstract class BaseAuditableEntity {

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Setter
  @Convert(converter = org.eds.demo.user.domain.UserIdConverter.class)
  @Column(name = "created_by_user_id", updatable = false)
  private UserId createdByUserId;

  @Setter
  @Convert(converter = org.eds.demo.user.domain.UserIdConverter.class)
  @Column(name = "updated_by_user_id")
  private UserId updatedByUserId;

  @Setter
  @Column(name = "created_by_user_display_name", updatable = false)
  private String createdByUserDisplayName;

  @Setter
  @Column(name = "updated_by_user_display_name")
  private String updatedByUserDisplayName;
}
