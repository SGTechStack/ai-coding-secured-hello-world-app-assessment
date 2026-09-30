package com.assessment.auth.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByUsername(String username);

  Optional<User> findByEmail(String email);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  List<User> findAllByOrderByCreatedAtAsc();

  /**
   * Existence check for the bootstrap runner (spec.md S9): does any user hold USER_MANAGER
   * <strong>in any state</strong>?
   *
   * <p>Deliberately not the recipe's {@code count == 0} — roles are seeded every boot and
   * self-registration makes the user count non-zero — and deliberately not "any <em>enabled</em>"
   * either, because a disabled sole administrator would cause a username collision on re-seed.
   */
  boolean existsByRole(String role);

  /**
   * Guards the last-administrator rule (story 1.17). Counts only <em>enabled</em> holders: a
   * disabled administrator cannot act, so leaving only disabled ones leaves the system unadminable.
   */
  @Query("select count(u) from User u where u.role = :role and u.enabled = true and u.id <> :excludedId")
  long countEnabledWithRoleExcluding(@Param("role") String role, @Param("excludedId") UUID excludedId);
}
