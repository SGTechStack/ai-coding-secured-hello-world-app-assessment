# Role-Based Access Control Configuration

## 1. Introduction

This guide implements **Configuration-Owned RBAC**, a "Security as Code" approach where authorization rules are defined in configuration files (e.g., `application.yml`). This ensures that security policies are version-controlled, auditable, and decoupled from the application's business logic.

**Key Design Principles:**
- **Authority-Based Access Control:** Code checks for fine-grained *Authorities* (privileges), while configuration maps *Roles* to those authorities.
- **Role Hierarchies:** Simplifies management by allowing senior roles to inherit permissions from junior ones.
- **Deny-by-Default:** A zero-trust posture where every endpoint is blocked unless explicitly whitelisted or guarded.
- **Immutable Security State:** Prevents runtime mutations of roles via APIs to ensure configuration remains the single source of truth.

## 2. Prerequisites

- Spring Boot 4.x (Java 21/25 recommended)
- Spring Security 7.x
- Standard JPA/Lombok stack

## 3. Implementation

### Step 1: Model the RBAC Structure in YAML

Use a structured configuration to define your security matrix. This separates *who* (Roles) from *what* (Authorities/Privileges).

```yaml
app:
  security:
    # 1. Role-to-Authority Mapping
    role-mappings:
      ADMIN: [USER_READ, USER_WRITE, ROLE_ADMIN]
      MANAGER: [USER_READ, USER_WRITE]
      USER: [USER_READ]
    
    # 2. Role Hierarchy (Best Practice: ADMIN > MANAGER > USER)
    role-hierarchy: "ROLE_ADMIN > ROLE_MANAGER \n ROLE_MANAGER > ROLE_USER"
    
    # 3. URL Guard Matrix (Guarded by Authorities)
    url-guards:
      USER_READ:
        - GET: /api/v1/users/**
      USER_WRITE:
        - POST: /api/v1/users
        - PATCH: /api/v1/users/*
    
    # 4. Global Whitelist
    whitelist:
      - /health
      - /v3/api-docs/**
```

### Step 2: Configure the Role Hierarchy

A `RoleHierarchy` bean reduces redundancy in your configuration and simplifies `@PreAuthorize` expressions.

```java
@Bean
public RoleHierarchy roleHierarchy(SecurityProperties props) {
    // Converts the string hierarchy from YAML into a functional bean
    return RoleHierarchyImpl.fromHierarchy(props.getRoleHierarchy());
}
```

### Step 3: Implement the Security Filter Chain

Leverage the functional `authorizeHttpRequests` DSL. Modern Spring Security provides several "secure by default" behaviors that do not require explicit configuration.

#### 1. Security Defaults
- **Deny-by-Default:** While not a strict framework default, the pattern of using `anyRequest().denyAll()` at the end of the chain is the industry standard for a zero-trust posture.
- **CSRF Protection:** Enabled by default for all state-changing requests.
- **Session Fixation:** Protected by default through session rotation upon login.

#### 2. Filter Chain Implementation
Focus on mapping your custom configuration to the security filter chain.

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties props) throws Exception {
    http
        .authorizeHttpRequests(auth -> {
            // 1. Apply Dynamic Guards from Configuration
            props.getUrlGuards().forEach((authority, paths) -> {
                paths.forEach(guard -> 
                    auth.requestMatchers(guard.getMethod(), guard.getPath()).hasAuthority(authority));
            });
            
            // 2. Apply Whitelist
            auth.requestMatchers(props.getWhitelist().toArray(String[]::new)).permitAll();
            
            // 3. Zero-Trust Final Guard
            auth.anyRequest().denyAll();
        });

    return http.build();
}
```

### Step 4: Enforce Immutable Security

To maintain the "Configuration as Source of Truth" principle, block any attempts to mutate Roles or Authorities via runtime APIs (e.g., REST controllers or Data REST handlers).

```java
@Component
@RepositoryEventHandler
public class ImmutableSecurityHandler {
    @HandleBeforeCreate @HandleBeforeSave @HandleBeforeDelete
    public void block(SecurityResource resource) {
        throw new AccessDeniedException("Security configuration is immutable at runtime. Update application.yml to change roles.");
    }
}
```

## 4. Verification

1. **Hierarchy Validation:** Verify an `ADMIN` can access `USER_READ` endpoints even if not explicitly assigned the role.
2. **Authority Decoupling:** Change a Role's mapping in YAML; verify access is updated without a code rebuild.
3. **Zero-Trust Check:** Ensure new, unconfigured endpoints return `403 Forbidden` by default.

## 5. Conclusion

This pattern transforms authorization from a static code concern into a dynamic configuration concern. It aligns with modern DevOps practices by allowing security teams to audit and update access control policies through standard PR workflows rather than code changes.

## 6. References

- [Spring Security: Authorization Architecture](https://docs.spring.io/spring-security/reference/servlet/authorization/architecture.html)
- [Spring Security: Role Hierarchy Support](https://docs.spring.io/spring-security/reference/servlet/authorization/architecture.html#authz-role-hierarchy)
- [OWASP: RBAC vs ABAC Best Practices](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)
- [NIST: Guide to Role-Based Access Control](https://csrc.nist.gov/projects/role-based-access-control)
- [Spring Security: Method Security Expressions](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)
