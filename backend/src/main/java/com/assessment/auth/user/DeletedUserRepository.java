package com.assessment.auth.user;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeletedUserRepository extends JpaRepository<DeletedUser, UUID> {

  /**
   * Both of these are checked on every registration and admin-create path. The recipe checks only
   * username (Priv:149), which is a bug — Std:96 states email uniqueness unqualified (spec.md S8).
   */
  boolean existsByUsername(String username);

  boolean existsByEmail(String email);
}
