package com.assessment.auth.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A role definition (spec.md S2, ticket 03).
 *
 * <p>Read-only and seeded from {@code app.roles} at startup. Std:407 / :415 make <em>persisting</em>
 * role definitions an enforced constraint, which is why this table exists at all — nothing reads a
 * role's authority set from it, because each user holds exactly one role and the hierarchy is
 * configured on the chain.
 *
 * <p>No endpoint creates, modifies or deletes a row here (story 1.17).
 */
@Entity
@Table(name = "roles")
public class Role {

  /** The two role names. Held as constants because the authorization matrix names them as strings. */
  public static final String USER = "USER";

  public static final String USER_MANAGER = "USER_MANAGER";

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "name", nullable = false, unique = true, length = 50)
  private String name;

  protected Role() {}

  public Role(UUID id, String name) {
    this.id = id;
    this.name = name;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
