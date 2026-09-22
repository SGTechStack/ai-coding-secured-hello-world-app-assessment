# Standalone: Privileged User Administration and Password Reset

## 1. Introduction

This guide covers the administrative user-management contract for standalone applications: password policy enforcement, managed-user lifecycle (create, update, delete with tombstones), admin password reset with history enforcement and session invalidation, forced first-login password change, and batch reset limits.

Admin operations differ from self-service access — they require privileged controls, dedicated credential flows, and audited user administration trails.

## 2. Prerequisites

- Spring Boot 4.x with Spring Security 7.x
- Spring Data JPA
- A relational database for users, deleted-user tombstones, password history, and HTTP sessions
- `spring-session-jdbc` for server-side session management and post-reset session invalidation
- Lombok (for `@Slf4j` logging)
- Structured logging setup

## 3. Steps

### 1. Configure the Password Encoder

Define the mandatory `PasswordEncoder` bean. Argon2id is the OWASP-recommended algorithm and is available out of the box in Spring Security 7. We use a single encoder implementation for simplicity and strict enforcement.

```java
// File: src/main/java/com/example/security/SecurityConfig.java
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // saltLength=16, hashLength=32, parallelism=1, memory=19456 KiB (19 MiB), iterations=2
        return new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    }
}
```

### 2. Model the managed user fields

Persist the core account state needed for admin operations and lifecycle policies. The starter maps this to the `USERS` table via `InternalUser`.

```java
// File: src/main/java/com/example/user/UserAccount.java
@Entity
@Table(name = "USERS")
class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    // --- Shared identity fields (all authentication modes) ---
    @Column(updatable = false)
    String email;
    String fullName;
    Set<AuthenticationMode> authenticationModes;  // e.g. {SYSTEM_DEFAULT} for standalone, {SSO}, or {SYSTEM_DEFAULT, SSO}
    AuthenticationMode sourceMode;                // original account creation mode
    @ManyToMany(fetch = FetchType.EAGER)
    Set<Role> roles;

    // --- Shared account state (all authentication modes) ---
    Instant createdAt;
    Instant lastLoginAt;
    Instant disabledAt;
    Boolean enabled;
    Boolean accountNonLocked;
    Boolean accountNonExpired;
    Boolean credentialsNonExpired;

    // --- Standalone-only fields (null for SSO-only users) ---
    @Column(updatable = false)
    String username;
    String passwordHash;
    Boolean requirePasswordChange;
    Boolean firstLogin;
    Instant passwordResetAt;
    int failedLoginAttempts;
    @ElementCollection
    List<PasswordHistoryEntry> passwordHistory;

    // --- SSO-only fields (null for standalone-only users) ---
    String ssoId;   // stable external identifier (sub claim, or uuid for MCC SSO)
    String uuid;    // MCC SSO specific
}
```

> **Note:** `email` and `username` are `@Column(updatable = false)` — dedicated flows are required to change them.

### 3. Implement password validation and policy enforcement

Validate password strength against OWASP and NIST guidelines. The starter enforces this via a regex check in `UserUtil.passwordStrengthCheck`.

```java
// File: src/main/java/com/example/user/PasswordPolicy.java
@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 1024;   // prevent DoS via unbounded input

    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new PasswordPolicyViolation(
                "password must be at least " + MIN_LENGTH + " characters");
        }
        if (rawPassword.length() > MAX_LENGTH) {
            throw new PasswordPolicyViolation(
                "password must not exceed " + MAX_LENGTH + " characters");
        }
        // At least one digit, lower, upper, and special char (@#$%^&+=); no whitespace
        if (!rawPassword.matches("(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{12,}")) {
            throw new PasswordPolicyViolation("password does not meet complexity requirements");
        }
        // Recommended: screen against known-breached passwords via HIBP k-anonymity API
        // if (isBreached(rawPassword)) throw new PasswordPolicyViolation("password is commonly breached");
    }
}
```

> **Note:** NIST SP 800-63B (§5.1.1) discourages mandatory composition rules and recommends length plus breached-password screening instead. The regex above is a pragmatic balance; remove the composition check and raise `MIN_LENGTH` for strict NIST compliance.
>
> The starter's `PasswordUtil.generateRandomPassword()` produces a 12-character password guaranteeing at least one character from each class (lower, upper, digit, special), with positions shuffled for uniform randomness.

### 4. Enforce create-time business rules with audit logging

Reject conflicting identities, validate the submitted password, initialize first-login state, and audit the creation. In the starter this is handled by `UserManagementRepositoryHandler.handleUserCreation`, guarded by `@PreAuthorize("hasRole('USERS_CREATE')")`.

```java
// File: src/main/java/com/example/user/UserAdminService.java
@Slf4j
@Service
public class UserAdminService {

    private final UserRepository users;
    private final DeletedUserRepository deletedUsers;
    private final RoleRepository roleRepository;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;

    public UserAccount createUser(CreateUserRequest request) {
        // Validate uniqueness against live users and tombstones
        if (users.existsByUsername(request.username()) || deletedUsers.existsByUsername(request.username())) {
            log.atWarn()
               .setMessage("User creation rejected")
               .addKeyValue("reason", "duplicate_username")
               .addKeyValue("event.action", "user-administration")
               .addKeyValue("event.outcome", "failure")
               .log();
            throw new ConflictException("user exist");
        }
        if (users.existsByEmail(request.email())) {
            log.atWarn()
               .setMessage("User creation rejected")
               .addKeyValue("reason", "duplicate_email")
               .addKeyValue("event.action", "user-administration")
               .addKeyValue("event.outcome", "failure")
               .log();
            throw new ConflictException("email is already used");
        }

        passwordPolicy.validate(request.rawPassword());

        UserAccount user = new UserAccount();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.rawPassword()));
        user.setPasswordHistory(List.of(new PasswordHistoryEntry(user.getPasswordHash(), Instant.now())));
        user.setRequirePasswordChange(true);
        user.setFirstLogin(true);
        user.setCreatedAt(Instant.now());
        user.setAuthenticationModes(Set.of(AuthenticationMode.SYSTEM_DEFAULT));
        user.setSourceMode(AuthenticationMode.SYSTEM_DEFAULT);
        user.setEnabled(true);
        user.setAccountNonLocked(true);
        user.setAccountNonExpired(true);
        user.setCredentialsNonExpired(true);
        user.setFailedLoginAttempts(0);

        if (roleRepository.findByName("USER") != null) {
            user.setRoles(Set.of(roleRepository.findByName("USER")));
        }

        UserAccount saved = users.save(user);

        log.atInfo()
           .setMessage("User created")
           .addKeyValue("user.id", saved.getId())
           .addKeyValue("event.action", "user-administration")
           .addKeyValue("event.outcome", "success")
           .log();
        return saved;
    }
}
```

### 5. Restrict generic updates with validation

Admin updates must not change the username or perform implicit password changes. Self-role-change is blocked. In the starter these rules are enforced in `UserManagementRepositoryHandler.handleUserUpdate`, guarded by `@PreAuthorize("hasRole('USERS_UPDATE')")`.

```java
// File: src/main/java/com/example/user/UserAdminService.java
@Service
@Slf4j
public class UserAdminService {
    
    // ... existing dependencies
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public UserAccount updateUser(UUID id, UpdateUserRequest request) {
        UserAccount existing = users.findById(id).orElseThrow(NotFoundException::new);

        if (!existing.getUsername().equals(request.username())) {
            log.atWarn()
               .setMessage("User update rejected")
               .addKeyValue("user.id", existing.getId())
               .addKeyValue("reason", "username_change_attempted")
               .addKeyValue("event.action", "user-administration")
               .addKeyValue("event.outcome", "failure")
               .log();
            throw new BadRequestException("username change not allowed");
        }

        // Block implicit password changes — use the dedicated resetPassword endpoint
        if (request.passwordHash() != null && !request.passwordHash().equals(existing.getPasswordHash())) {
            log.atWarn()
               .setMessage("User update rejected")
               .addKeyValue("user.id", existing.getId())
               .addKeyValue("reason", "password_change_via_update")
               .addKeyValue("event.action", "user-administration")
               .addKeyValue("event.outcome", "failure")
               .log();
            throw new BadRequestException("password change not allowed");
        }

        // Block locking via the generic update path
        boolean isLocking = request.accountNonLocked() != null
            && !request.accountNonLocked() && existing.getAccountNonLocked();
        if (isLocking) {
            throw new BadRequestException("locking via generic update not allowed");
        }

        // Block self-role-change
        if (currentUserId().equals(existing.getId()) && rolesChanged(existing.getRoles(), request.roles())) {
            log.atWarn()
               .setMessage("User update rejected")
               .addKeyValue("user.id", existing.getId())
               .addKeyValue("reason", "self_role_change")
               .addKeyValue("event.action", "user-administration")
               .addKeyValue("event.outcome", "failure")
               .log();
            throw new BadRequestException("current user role change not allowed");
        }

        boolean privilegesChanged = rolesChanged(existing.getRoles(), request.roles()) || 
                                   (request.enabled() != null && !request.enabled().equals(existing.getEnabled()));

        existing.setRoles(request.roles());
        if (request.enabled() != null) {
            existing.setEnabled(request.enabled());
        }
        
        UserAccount saved = users.save(existing);

        if (privilegesChanged) {
            // OWASP Requirement: Actively terminate sessions when account state or privileges change
            sessionRepository.findByPrincipalName(saved.getUsername())
                .keySet()
                .forEach(sessionRepository::deleteById);
        }

        log.atInfo()
           .setMessage("User updated")
           .addKeyValue("user.id", existing.getId())
           .addKeyValue("event.action", "user-administration")
           .addKeyValue("event.outcome", "success")
           .log();
        return saved;
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserAccount user) {
            return user.getId();
        }
        throw new IllegalStateException("no authenticated user in security context");
    }

    private boolean rolesChanged(Set<Role> existing, Set<Role> requested) {
        if (existing == null && requested == null) return false;
        if (existing == null || requested == null) return true;
        return !existing.equals(requested);
    }
}
```

### 6. Preserve a deleted-user tombstone

Before deletion, copy the user into a retention table. The starter handles this in `UserManagementRepositoryHandler.handleUserDeletion` (`@PreAuthorize("hasRole('USERS_DELETE')")`); the tombstone must also record who performed the deletion for a complete audit trail.

```java
// File: src/main/java/com/example/user/DeletedUser.java
@Entity
@Table(name = "DELETED_USERS")
class DeletedUser {
    @Id
    UUID id;
    @Column(updatable = false) String username;
    @Column(updatable = false) String email;
    @Column(name = "DATETIME_ACCOUNT_DELETED") Instant deletedAt;
    @Column(name = "DELETED_BY_USER_ID") UUID deletedById;   // not yet in the starter — add it
    Boolean enabled;
    Boolean accountNonLocked;
    Boolean credentialsNonExpired;
    // ... other lifecycle columns
}

// File: src/main/java/com/example/user/UserAdminService.java
@Transactional   // tombstone save + user delete must be atomic
public void deleteUser(UUID id) {
    UserAccount existing = users.findById(id).orElseThrow(NotFoundException::new);
    UUID currentUserId = currentUserId();

    if (currentUserId.equals(existing.getId())) {
        log.atWarn()
           .setMessage("User deletion rejected")
           .addKeyValue("user.id", existing.getId())
           .addKeyValue("reason", "self_deletion")
           .addKeyValue("event.action", "user-administration")
           .addKeyValue("event.outcome", "failure")
           .log();
        throw new BadRequestException("current user deletion not allowed");
    }

    DeletedUser tombstone = new DeletedUser();
    tombstone.setId(existing.getId());
    tombstone.setUsername(existing.getUsername());
    tombstone.setEmail(existing.getEmail());
    tombstone.setDeletedAt(Instant.now());
    tombstone.setDeletedById(currentUserId);
    deletedUsers.save(tombstone);

    users.delete(existing);
    
    // OWASP Requirement: Actively terminate sessions when account is deleted
    sessionRepository.findByPrincipalName(existing.getUsername())
        .keySet()
        .forEach(sessionRepository::deleteById);

    log.atInfo()
       .setMessage("User deleted")
       .addKeyValue("user.id", existing.getId())
       .addKeyValue("event.action", "user-administration")
       .addKeyValue("event.outcome", "success")
       .log();
}
```

> **Implementation gap:** `deletedById` is not yet in the starter's `DeletedUser` entity. Add the column and populate it in `handleUserDeletion` — without it, the deleting user's identity is only recoverable from logs.

### 7. Implement a dedicated reset-password endpoint

Password reset is privileged and separate from generic updates. The starter uses `UserController` and `ResetPasswordCommand`: generate a history-checked random password, reset the account lock state, save the new credential, then invalidate all active sessions so the old password cannot be reused.

Session invalidation requires Spring Session. Add the JDBC backing store (swap for `spring-session-data-redis` if your environment uses Redis):

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-jdbc</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  session:
    store-type: jdbc
    jdbc:
      initialize-schema: always   # use 'never' and apply the schema manually in production
```

`FindByIndexNameSessionRepository` is auto-configured once Spring Session is on the classpath. Inject it into `ResetPasswordCommand` alongside the existing `userManagementRepository` and `passwordEncoder` dependencies.

```java
// File: src/main/java/com/example/user/UserController.java
@Slf4j
@RepositoryRestController("/users")
public class UserController {

    private final CommandExecutor commandExecutor;

    @PatchMapping("/{userId}/resetPassword")
    @PreAuthorize("hasRole('USERS_UPDATE')")
    public ResponseEntity<NewPasswordDTO> resetPassword(@PathVariable UUID userId) {
        log.debug("processing reset password for user");
        NewPasswordDTO response = commandExecutor.execute(new ResetPasswordCommand(userId));
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/batchResetPassword")
    @PreAuthorize("hasRole('USERS_UPDATE')")
    public ResponseEntity<List<NewPasswordForBatchDTO>> resetPasswordForBatch(
            @RequestBody BatchUpdateRequest request) {
        log.debug("processing batch reset password for users");
        List<NewPasswordForBatchDTO> response =
            commandExecutor.execute(new BatchResetPasswordCommand(request.getUserIds()));
        return ResponseEntity.ok(response);
    }
}
```

The `ResetPasswordCommand` performs the actual reset. Save before invalidating — if invalidation fails, the new credential is already committed and the user is not left unable to log in.

```java
// File: src/main/java/com/example/user/ResetPasswordCommand.java
@Slf4j
public class ResetPasswordCommand implements UserActionCommand<NewPasswordDTO> {

    private final UUID userId;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @Override
    public NewPasswordDTO execute() {
        validate();
        String newPassword = PasswordUtil.generateRandomPassword();

        // Avoid reusing a recently-used password
        while (isPasswordInPasswordHistory(user.getPasswordHistory(), newPassword,
                passwordEncoder, passwordProperties.getMaxPasswordHistoryLength())) {
            newPassword = PasswordUtil.generateRandomPassword();
        }

        user.setPassword(newPassword);
        handlePasswordChangeIfApplicable(passwordEncoder, user);   // encodes and sets passwordResetAt

        List<PasswordDetails> updatedHistory = insertToPasswordHistory(
            user.getPasswordHistory(), user.getPassword());
        user.setPasswordHistory(updatedHistory);
        user.setRequirePasswordChange(true);   // force change on next login

        // Reset lock state so the user can log in immediately with the new password
        user.setFailedLoginAttempts(0);
        user.setAccountNonLocked(true);

        userManagementRepository.save(user);

        // Invalidate sessions after saving — old sessions must not remain valid after a credential reset
        sessionRepository.findByPrincipalName(user.getUsername())
            .keySet()
            .forEach(sessionRepository::deleteById);

        log.atInfo()
           .setMessage("Password reset")
           .addKeyValue("user.id", user.getId())
           .addKeyValue("event.action", "password-reset")
           .addKeyValue("event.outcome", "success")
           .log();

        return NewPasswordDTO.builder()
                .userId(userId)
                .username(user.getUsername())
                .newPassword(newPassword)   // plaintext returned once — treat as sensitive, never log
                .build();
    }

    @Override
    public void validate() {
        user = userManagementRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if (isNull(user.getAuthenticationModes())
                || !user.getAuthenticationModes().contains(AuthenticationMode.SYSTEM_DEFAULT)) {
            log.atWarn()
               .setMessage("Password reset rejected because user does not have standalone authentication mode")
               .addKeyValue("user.id", user.getId())
               .addKeyValue("event.action", "password-reset")
               .addKeyValue("event.outcome", "failure")
               .log();
            throw new PasswordChangeNotAllowed();
        }
    }
}
```

### 8. Enforce a batch reset limit and validation

Batch administrative actions reject oversized payloads before any processing. The starter enforces this in `BatchUserActionCommand.validatePayload`, reading the limit from `UserManagementProperties` (`spring.user-management.batchPayloadMaxSize`, default 5000).

```java
// File: src/main/java/com/example/user/BatchUpdateRequest.java
@Data
public class BatchUpdateRequest {
    private List<UUID> userIds;
}

// File: src/main/java/com/example/user/BatchResetPasswordCommand.java
@Override
public void validate() {
    // validatePayload throws RequestTooLargeException when size > batchPayloadMaxSize
    this.validatePayload(userIds);

    for (UUID userId : userIds) {
        UserAccount user = userManagementRepository.findById(userId)
            .orElseThrow(UserNotFoundException::new);
        if (isNull(user.getAuthenticationModes())
                || !user.getAuthenticationModes().contains(AuthenticationMode.SYSTEM_DEFAULT)) {
            throw new PasswordChangeNotAllowed();
        }
        users.add(user);
    }
}
```

Configure the limit in `user-management.yml`:

```yaml
spring:
  user-management:
    batch-payload-max-size: 5000
```

Log the batch outcome with aggregate counts only — never log per-user data inside the loop:

```java
log.atInfo()
   .setMessage("Batch password reset")
   .addKeyValue("success_count", successCount)
   .addKeyValue("total_count", totalCount)
   .addKeyValue("event.action", "password-reset")
   .addKeyValue("event.outcome", "success")
   .log();
```

### 9. Enforce password change requirement with a security filter

The starter's `PasswordChangeFilter` intercepts requests from users with `requirePasswordChange=true` and blocks access to all endpoints except:

- `GET ${api.base-path}/csrf`
- `GET ${api.base-path}/currentUser`
- `PATCH ${api.base-path}/currentUser/changePassword`
- Whitelisted public paths (`/login`, `/.well-known/jwks.json`, Swagger)

```java
// File: src/main/java/com/example/security/PasswordChangeFilter.java
@Component
public class PasswordChangeFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final SecurityFilterProperties properties;
    private final DataRestAutoConfigurationProperties dataRestConfig;
    private List<String> pathsForWhitelisting;

    public PasswordChangeFilter(UserRepository userRepository, 
                                SecurityFilterProperties properties,
                                DataRestAutoConfigurationProperties dataRestConfig) {
        this.userRepository = userRepository;
        this.properties = properties;
        this.dataRestConfig = dataRestConfig;
    }

    @PostConstruct
    public void setup() {
        pathsForWhitelisting = properties.getPathsForWhitelisting();
        Collections.addAll(pathsForWhitelisting, WebSecurityConfiguration.DEFAULT_SWAGGER_WHITELIST);
        Collections.addAll(pathsForWhitelisting, WebSecurityConfiguration.DEFAULT_AUTH_WHITELIST);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {

            UserAccount account = userRepository.findById(userDetails.getId())
                    .orElseThrow(UserNotFoundException::new);

            if (Boolean.TRUE.equals(account.getRequirePasswordChange())) {
                String baseUrl = dataRestConfig.getApiBaseUrlPath();
                String csrfEndpoint = Paths.get(baseUrl, "/csrf").toString();
                String currentUserEndpoint = Paths.get(baseUrl, "/currentUser").toString();
                String changePasswordEndpoint = Paths.get(baseUrl, "/currentUser/changePassword").toString();

                if (isWhitelistedPath(request, baseUrl)
                        || matchesRequest(request, csrfEndpoint, HttpMethod.GET.name())
                        || matchesRequest(request, currentUserEndpoint, HttpMethod.GET.name())
                        || matchesRequest(request, changePasswordEndpoint, HttpMethod.PATCH.name())) {
                    filterChain.doFilter(request, response);
                    return;
                }

                log.atWarn()
                   .setMessage("Access blocked because password change is required")
                   .addKeyValue("user.id", account.getId())
                   .addKeyValue("url.path", request.getRequestURI())
                   .addKeyValue("http.request.method", request.getMethod())
                   .addKeyValue("event.action", "password-change-enforcement")
                   .addKeyValue("event.outcome", "failure")
                   .log();
                   
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Password must be changed on first login");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

}

// File: src/main/java/com/example/security/WebSecurityConfiguration.java
// The starter registers this filter automatically in WebSecurityConfiguration:
//   http.addFilterBefore(passwordChangeFilter, AuthorizationFilter.class);
```

## 4. Examples

### Create a new managed user

```bash
curl -X POST https://app.example.com${api.base-path}/users \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "test",
    "email": "tester@example.com",
    "password": "MySecurePass1234!"
  }'

# Response (201 Created):
# {
#   "id": "5c8d3d6c-9472-4f92-9f0c-b0de8a53603b",
#   "username": "test",
#   "email": "tester@example.com",
#   "requirePasswordChange": true,
#   "firstLogin": true
# }

# Audit log (structured JSON):
# {"message":"User created","user.id":"5c8d3d6c-9472-4f92-9f0c-b0de8a53603b","event.action":"user-administration","event.outcome":"success"}
```

### Reset a user's password

Returns the one-time plaintext password — never log it and transmit only over TLS.

```bash
curl -X PATCH https://app.example.com${api.base-path}/users/5c8d3d6c.../resetPassword \
  -H 'Authorization: Bearer <token>'

# Response (200 OK):
# {
#   "userId": "5c8d3d6c-9472-4f92-9f0c-b0de8a53603b",
#   "username": "test",
#   "newPassword": "Kj8@mL9#pQr2"   # DO NOT LOG THIS VALUE
# }

# Audit log (structured JSON):
# {"message":"Password reset","user.id":"5c8d3d6c-9472-4f92-9f0c-b0de8a53603b","event.action":"password-reset","event.outcome":"success"}
```

### Batch password reset

The caller must securely deliver each `newPassword` to the respective user.

```bash
curl -X PATCH https://app.example.com${api.base-path}/users/batchResetPassword \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{
    "userIds": [
      "5c8d3d6c-9472-4f92-9f0c-b0de8a53603b",
      "7d9e4e7d-0583-4a03-0e1d-c1ef9b64714c"
    ]
  }'

# Response (200 OK):
# [
#   { "userId": "5c8d3d6c...", "username": "test", "newPassword": "Kj8@mL9#pQr2" },
#   { "userId": "7d9e4e7d...", "username": "test2", "newPassword": "Zx3!nR7@qWp5" }
# ]
```

### Oversized batch rejection

```bash
curl -X PATCH https://app.example.com${api.base-path}/users/batchResetPassword \
  -H 'Authorization: Bearer <token>' \
  -d '{"userIds": [... 5001 user IDs ...]}'

# Response (413 Request Entity Too Large / 400 Bad Request):
# {
#   "error": "request body with list of more than 5000 entries is not allowed"
# }
```

### Password change enforcement

```bash
# Access any protected endpoint while requirePasswordChange=true
curl -X GET https://app.example.com${api.base-path}/users \
  -H 'Authorization: Bearer <session-cookie>'

# Response (403 Forbidden):
# "Password must be changed on first login"

# Change password to regain access
curl -X PATCH https://app.example.com${api.base-path}/currentUser/changePassword \
  -H 'Authorization: Bearer <session-cookie>' \
  -H 'Content-Type: application/json' \
  -d '{
    "currentPassword": "Kj8@mL9#pQr2",
    "newPassword": "MyNewSecurePass456!"
  }'

# Response (200 OK)
```

## 5. Verification

Confirm that:

- duplicate usernames and emails are rejected on creation;
- tombstone check rejects re-creation of a previously deleted username;
- username changes are rejected on update;
- generic update does not allow password changes (throws `PasswordChangeNotAllowed`);
- locking via the generic update path is rejected;
- self-delete is rejected;
- self-role-change is rejected;
- delete writes a tombstone record before the user row is removed;
- reset password returns a new plaintext password and sets `requirePasswordChange=true`;
- the returned plaintext password is NOT present in any log line;
- `failedLoginAttempts` is reset to 0 and `accountNonLocked` is set to `true` after admin password reset;
- all active sessions for the target user are invalidated after password reset;
- oversized batch reset requests fail before any passwords are reset;
- all user administration operations produce structured log lines with `user.id` and event outcome;
- users with `requirePasswordChange=true` are blocked from all endpoints except CSRF, current-user profile, and change-password.

## 6. Conclusion

This implementation separates ordinary user administration from sensitive credential handling, preserves deletion traceability via tombstones, and keeps password resets explicit, auditable, bounded, and secure. Plaintext passwords are generated with `SecureRandom`, checked against recent history, returned to the caller exactly once, and never logged. Session invalidation ensures that an admin credential reset takes immediate effect — no pre-existing session can continue with the old password.

## 7. References

Security Standards:
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) — Argon2id, BCrypt guidance
- [OWASP Forgot Password Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html) — password reset token handling and security
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html) — authentication best practices including password policies
- [NIST SP 800-63B](https://pages.nist.gov/800-63-4/sp800-63b.html) — memorized secret requirements (min length, no arbitrary complexity rules, breached password screening)

Spring Security:
- [Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html) — BCrypt, Argon2, and password encoding
- [Session Management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html) — session invalidation after credential change
- [Servlet Security: The Big Picture](https://docs.spring.io/spring-security/reference/servlet/architecture.html#servlet-filters-review) — security filter chain architecture and custom filters
