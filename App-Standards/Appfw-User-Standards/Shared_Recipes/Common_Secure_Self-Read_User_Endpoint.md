# Secure Self-Read User Endpoint

## 1. Introduction

A **Secure Self-Read User Endpoint** (e.g., `/profile` or `/me`) allows authenticated users to retrieve their own authorization record (roles, permissions, profile data) without administrative privileges. This endpoint is critical for frontend applications to determine UI rendering and for users to manage their own data.

**Key Design Principles:**
- **Principal Injection:** Uses `@AuthenticationPrincipal` to retrieve data based *only* on the authenticated identity, providing the primary defense against Insecure Direct Object Reference (IDOR).
- **Data Filtering (PII):** Leverages Projections or DTOs to ensure sensitive data (e.g., password hashes) never leaves the application layer.
- **In-Depth Authorization:** Combines method-level security (`@PostAuthorize`) with repository filtering (`@PostFilter`) as a defense-in-depth measure.
- **Zero-Trust Implementation:** Blocks all mutation attempts (POST/PATCH/DELETE) on the self-read endpoint by default.
- **Cache-Control (Default):** Relies on Spring Security's default headers (`Cache-Control: no-cache, no-store, max-age=0, must-revalidate`) to ensure sensitive profile data is never cached by browsers or intermediate proxies.
- **Audit Logging:** Logs successful profile retrievals and failed access attempts for security monitoring and compliance.

## 2. Prerequisites

- Spring Boot 4.x (Java 21/25)
- Spring Security 7.x
- Role-Based Access Control Configuration (see [Role-Based Access Control Configuration](Common_Role-Based_Access_Control_Configuration.md))

## 3. Implementation

### Step 1: Define a Secure User View

Create a projection or DTO to explicitly whitelist exposed fields, following the "Zero-Trust" principle for data exposure.

```java
@Projection(name = "userProfileView", types = {UserAccount.class})
public interface UserProfileView {
    String getUsername();
    String getFirstName();
    String getLastName();
    String getEmail();
    Set<Role> getRoles();
    // Sensitive fields like password and internal UUIDs are excluded
}
```

### Step 2: Implement the Secure Controller

To support both standalone (local `username`) and SSO modes (stable `uuid` or `ssoId` claim), we inject `Authentication` directly and look up the user using `authentication.getName()`. By relying entirely on the authenticated principal and ignoring any user-provided IDs, this pattern inherently prevents IDOR attacks. We do not need to manually set Cache-Control headers, as Spring Security's `HeaderWriterFilter` applies them automatically.

```java
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<UserProfileView> getMyProfile(Authentication authentication) {
        String principalName = authentication.getName();
        
        // Audit log the access
        log.atInfo()
           .setMessage("User profile read")
           .addKeyValue("event.action", "profile-read")
           .addKeyValue("event.outcome", "success")
           .log();

        // Look up record ONLY by the authenticated identity (supporting both username and SSO UUID/ID)
        UserProfileView profile = profileService.findProfile(principalName);
        
        return ResponseEntity.ok(profile);
    }
}
```

### Step 3: Secure the Repository (Defense-in-Depth)

Apply repository-level guards using standard `authentication.name` SpEL expressions. This ensures that regardless of whether the session principal is a standalone username or an SSO UUID/ID, the query is bounded to the authenticated principal.

#### Security Defaults
- **Principal Awareness:** `authentication.name` retrieves the principal identifier from the current Spring Security context.

#### Guard Implementation
```java
@Repository
@PreAuthorize("hasAuthority('SELF_READ')")
public interface UserProfileRepository extends CrudRepository<UserAccount, UUID> {

    @PostAuthorize("returnObject.isEmpty() or returnObject.get().username == authentication.name or returnObject.get().uuid == authentication.name or returnObject.get().ssoId == authentication.name")
    Optional<UserAccount> findById(UUID id);

    @PostFilter("filterObject.username == authentication.name or filterObject.uuid == authentication.name or filterObject.ssoId == authentication.name")
    Iterable<UserAccount> findAll();
}
```

### Step 4: Block Runtime Mutations

Ensure the self-read endpoint remains read-only at the repository level to prevent unauthorized modifications.

```java
@Component
@RepositoryEventHandler
public class UserProfileMutationHandler {
    @HandleBeforeCreate @HandleBeforeSave @HandleBeforeDelete
    public void blockMutation(UserAccount user) {
        throw new AccessDeniedException("Mutations are not allowed on the self-read endpoint.");
    }
}
```

### Step 5: Configure application.yml

Define access guards using generic property prefixes.

```yaml
app:
  security:
    url-guards:
      SELF_READ:
        - GET: /api/v1/profile
    whitelist:
      - /health
```

## 4. Verification

1. **Self-Access:** Call `GET /api/v1/profile`; verify your own profile is returned.
2. **IDOR Prevention:** Attempt to access another user's data; verify that the endpoint only returns the authenticated user's data (or blocks access if an ID is provided).
3. **Cache Check:** Inspect the response headers; verify Spring Security's default `Cache-Control: no-cache, no-store, max-age=0, must-revalidate` is present.
4. **Audit Audit:** Check the application logs; verify that the profile access was logged.
5. **Mutation Blocking:** Attempt a `PATCH` to the profile endpoint; verify the request is rejected.
6. **Leakage Audit:** Verify the JSON response contains no sensitive fields like `password` or `passwordHistory`.

## 5. Conclusion

This architecture provides a secure, auditable, and maintainable "Self-Read" capability. By combining `@AuthenticationPrincipal` with repository filtering and explicit data projections, the application minimizes the attack surface and prevents common data leakage vulnerabilities.

## 6. References

- [Spring Security: @AuthenticationPrincipal](https://docs.spring.io/spring-security/reference/servlet/integrations/annotation.html#authenticationprincipal)
- [Spring Security: Default Security Headers](https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html#headers-default)
- [Spring Data REST: Projections and Excerpts](https://docs.spring.io/spring-data/rest/reference/projections-excerpts.html)
- [OWASP: Insecure Direct Object Reference (IDOR) Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Insecure_Direct_Object_Reference_Prevention_Cheat_Sheet.html)
- [OWASP: API Security - Broken Object Level Authorization](https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/)
- [NIST: Digital Identity Guidelines](https://pages.nist.gov/800-63-3/)

