package com.assessment.auth.security;

import com.assessment.auth.common.ApiErrorCode;
import com.assessment.auth.common.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Rejects an administrator acting on their own account (spec.md S3, ticket 10).
 *
 * <p>Called by matrix rows 11, 12, 13 and 15 — status, role, unlock and delete. An administrator
 * who could disable, demote or delete themselves can lock the system out of its own administration
 * with a single request, and Std:474 and :266 make the rejection explicit rather than advisory.
 *
 * <p>Returns <strong>403 with code {@code SELF_ACTION_NOT_ALLOWED}</strong>, which the SPA's axios
 * interceptor must surface in-app rather than treating as a session death (spec.md S10).
 *
 * <p>This is <em>not</em> a self-read path. A plain USER calling {@code GET /users/{ownId}} gets
 * 403 from the matrix, because Std:429 is explicit that user-management endpoints reject
 * non-administrators including on their own account.
 */
@Component
public class SelfActionGuard {

  public void reject(UUID actorId, UUID targetId) {
    if (actorId != null && actorId.equals(targetId)) {
      throw new ApiException(
          ApiErrorCode.SELF_ACTION_NOT_ALLOWED,
          "An administrator cannot perform this action on their own account.");
    }
  }
}
