package org.eds.demo.common;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.eds.demo.user.domain.AppUserDetails;
import org.eds.demo.user.domain.UserId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class AuditUserListener {

  @PrePersist
  public void onPrePersist(BaseAuditableEntity entity) {
    entity.setCreatedByUserDisplayName(getCurrentDisplayName());
    entity.setUpdatedByUserDisplayName(getCurrentDisplayName());
    entity.setCreatedByUserId(getCurrentUserId());
    entity.setUpdatedByUserId(getCurrentUserId());
  }

  @PreUpdate
  public void onPreUpdate(BaseAuditableEntity entity) {
    entity.setUpdatedByUserDisplayName(getCurrentDisplayName());
    entity.setUpdatedByUserId(getCurrentUserId());
  }

  private String getCurrentDisplayName() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return "SYSTEM";
    }
    if (auth.getPrincipal() instanceof AppUserDetails details) {
      return details.getDisplayName();
    }
    return auth.getName();
  }

  private UserId getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return null;
    }
    if (auth.getPrincipal() instanceof AppUserDetails details) {
      return details.getUserId();
    }
    return null;
  }
}
