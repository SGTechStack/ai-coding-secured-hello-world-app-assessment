package local.builderday.auth.core.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import local.builderday.account.core.model.Role;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.http.HttpMethod;

/**
 * Configuration-owned role definitions and the default-deny authorization matrix. Loaded once at startup; a duplicate
 * or malformed definition fails binding and therefore stops the application. No API mutates roles.
 *
 * @param roles every role an account may hold
 * @param registrationRole the role self-registered accounts receive; must be a defined role
 * @param publicAccess method/path grants open to anyone, including Visitors
 * @param grants method/path grants requiring one of the listed roles
 */
@ConfigurationProperties("app.security")
public record AuthorizationProperties(
    List<String> roles, String registrationRole, @DefaultValue List<Rule> publicAccess,
    @DefaultValue List<Rule> grants) {

  private static final String ROLE_NAME = "^[A-Z][A-Z0-9_]{0,31}$";

  /**
   * @param method HTTP method, or null for every method
   * @param pattern Spring path pattern
   * @param roles roles granted access; ignored for public rules
   */
  public record Rule(String method, String pattern, @DefaultValue List<String> roles) {
    public Rule {
      if (pattern == null || !pattern.startsWith("/")) {
        throw new IllegalArgumentException("Authorization rule pattern must start with '/'.");
      }
      if (method != null && !Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS").contains(method)) {
        throw new IllegalArgumentException("Unsupported authorization rule method.");
      }
      roles = List.copyOf(roles);
    }

    public HttpMethod httpMethod() { return method == null ? null : HttpMethod.valueOf(method); }
  }

  public AuthorizationProperties {
    Set<String> defined = definedRoles(roles);
    requireExactlyTheRoleEnum(defined);
    requireRegistrationRole(registrationRole, defined);
    requireGrantRoles(grants, defined);
    roles = List.copyOf(roles);
    publicAccess = List.copyOf(publicAccess);
    grants = List.copyOf(grants);
  }

  /** At least one role, each well-formed and defined once. */
  private static Set<String> definedRoles(List<String> roles) {
    if (roles == null || roles.isEmpty()) {
      throw new IllegalArgumentException("app.security.roles must define at least one role.");
    }
    var defined = new HashSet<String>();
    for (String role : roles) {
      if (role == null || !role.matches(ROLE_NAME)) {
        throw new IllegalArgumentException("Malformed role definition: " + role);
      }
      if (!defined.add(role)) throw new IllegalArgumentException("Duplicate role definition: " + role);
    }
    return defined;
  }

  /**
   * The configured roles are exactly the {@link Role} constants, the one list of roles in code. The registration role
   * and every grant must name a configured role, so they are {@link Role} constants too.
   */
  private static void requireExactlyTheRoleEnum(Set<String> defined) {
    Set<String> declared = new HashSet<>();
    for (Role role : Role.values()) declared.add(role.name());
    for (String role : defined) {
      if (!declared.contains(role)) {
        throw new IllegalArgumentException("app.security.roles names a role the Role enum does not declare: " + role);
      }
    }
    for (Role role : Role.values()) {
      if (!defined.contains(role.name())) {
        throw new IllegalArgumentException("app.security.roles is missing the Role enum constant: " + role);
      }
    }
  }

  private static void requireRegistrationRole(String registrationRole, Set<String> defined) {
    if (!defined.contains(registrationRole)) {
      throw new IllegalArgumentException("app.security.registration-role must be a defined role.");
    }
  }

  /** Every grant lists at least one role, and only defined ones. */
  private static void requireGrantRoles(List<Rule> grants, Set<String> defined) {
    for (Rule grant : grants) {
      if (grant.roles().isEmpty()) {
        throw new IllegalArgumentException("Grant for " + grant.pattern() + " lists no roles.");
      }
      for (String role : grant.roles()) {
        if (!defined.contains(role)) throw new IllegalArgumentException("Grant references undefined role: " + role);
      }
    }
  }
}
