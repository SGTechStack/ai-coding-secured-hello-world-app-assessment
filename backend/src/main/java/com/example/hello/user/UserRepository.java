package com.example.hello.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByUsername(String username);

  Optional<User> findByEmailIgnoreCase(String email);

  boolean existsByUsername(String username);

  boolean existsByEmailIgnoreCase(String email);

  boolean existsByRole(Role role);

  List<User> findAllByOrderByCreatedAtAsc();
}
