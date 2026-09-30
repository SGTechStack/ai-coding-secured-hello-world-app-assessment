package org.eds.demo.user.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.eds.demo.user.domain.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

  @EntityGraph(attributePaths = "userRoles")
  Optional<AppUser> findByUsername(String username);

  @EntityGraph(attributePaths = "userRoles")
  Optional<AppUser> findByAasUuid(UUID aasUuid);

  @EntityGraph(attributePaths = "userRoles")
  Optional<AppUser> findByEmail(String email);

  boolean existsByUsername(String username);
}
