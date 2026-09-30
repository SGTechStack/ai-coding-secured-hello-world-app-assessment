package local.builderday.account.administration.controller.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import local.builderday.account.core.model.Role;

/**
 * The admin Role change body: {@code {"role": "<ROLE>"}}. The role is a string equal to a {@link Role} constant;
 * absent, {@code null}, blank or undeclared is a 400 {@code INVALID_REQUEST} via the constraints below, and a
 * non-string value fails JSON binding, also a 400. Bound as a string rather than the enum so a number is never read
 * as an enum ordinal.
 */
public record RoleChangeRequest(@NotNull String role) {

  @AssertTrue
  public boolean isDeclaredRole() {
    return role == null || Arrays.stream(Role.values()).anyMatch(declared -> declared.name().equals(role));
  }

  /** The requested role; call only after validation. */
  public Role requestedRole() { return Role.valueOf(role); }
}
