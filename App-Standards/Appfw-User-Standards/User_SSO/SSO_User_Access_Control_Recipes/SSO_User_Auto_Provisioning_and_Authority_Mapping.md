# SSO User Auto Provisioning and Authority Mapping

## 1. Introduction

This guide shows how to create a local application user automatically when an external SSO identity logs in for the first time, then attach local roles that drive authorization inside the application. It covers stable SSO ID resolution, authority-mapping extension points, auto-provisioning with audit logging, and role reuse for returning users.

This matters because the SSO starter separates authentication from authorization. By the end, the application will resolve a stable SSO ID from token claims, load or create a local user with comprehensive audit trails, and map application roles from a controlled authority-mapping policy.

## 2. Prerequisites

- Spring Boot 4.x with Spring Security 7.x

- Spring Data JPA
- A local user table and role table
- OIDC ID token claim access
- Lombok (for @Slf4j logging)

## 3. Steps

### 1. Resolve a stable SSO identifier from claims

The local user link must use a stable issuer-aware identifier extracted safely from token claims.

```java
// File: src/main/java/com/example/sso/SsoIdentityResolver.java
@Component
@Slf4j
public class SsoIdentityResolver {

    public String resolveSsoId(Map<String, Object> claims, boolean isMccSso) {
        if (claims == null || claims.isEmpty()) {
            throw new IllegalArgumentException("Claims cannot be null or empty");
        }

        String ssoId;
        if (isMccSso) {
            Object afcasObj = claims.get("afcas");
            if (afcasObj instanceof Map<?, ?> afcasMap && afcasMap.get("uuid") instanceof String uuid) {
                ssoId = uuid;
            } else if (claims.get("afcas.uuid") instanceof String flatUuid) {
                ssoId = flatUuid;
            } else {
                ssoId = null;
            }
            if (ssoId == null) {
                log.atWarn()
                   .setMessage("afcas.uuid claim missing for MCC SSO")
                   .addKeyValue("claims_keys", claims.keySet())
                   .log();
                throw new IllegalArgumentException("afcas.uuid claim missing for MCC SSO");
            }
        } else {
            ssoId = (String) claims.get("sub");
            if (ssoId == null) {
                log.atWarn()
                   .setMessage("Sub claim missing")
                   .addKeyValue("claims_keys", claims.keySet())
                   .log();
                throw new IllegalArgumentException("sub claim missing");
            }
        }

        return ssoId;
    }

    public String extractEmail(Map<String, Object> claims) {
        return (String) claims.getOrDefault("email", null);
    }

    public String extractFullName(Map<String, Object> claims) {
        return (String) claims.getOrDefault("name", (String) claims.getOrDefault("given_name", null));
    }
}
```

### 2. Define an authority-mapping extension point

Keep role derivation isolated so issuer-specific or organization-specific mapping can change without rewriting login flow code.

```java
// File: src/main/java/com/example/sso/UserAuthoritiesConfiguration.java
public abstract class UserAuthoritiesConfiguration<T> {

    protected final UserRepository userRepository;
    protected final RoleRepository roleRepository;
    protected static final Logger logger = LoggerFactory.getLogger(UserAuthoritiesConfiguration.class);

    protected UserAuthoritiesConfiguration(UserRepository userRepository,
                                          RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    /**
     * Map external provider attributes to local roles.
     * Implement issuer-specific logic here (e.g., LDAP group → role mapping).
     */
    protected abstract Set<Role> mapRoles(T attributes);

    /**
     * Fetch issuer-specific attributes (e.g., from MPDS for MCC SSO).
     */
    protected T fromProvider(String ssoId, boolean isMccSso) {
        return null;  // Default: no external enrichment
    }

    protected Set<GrantedAuthority> toApplicationAuthorities(Set<Role> roles) {
        return roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
            .collect(Collectors.toSet());
    }
}
```

### 3. Auto-create the local SSO user with audit logging

A first-seen SSO identity should become a local user with SSO-specific metadata and no local password.

```java
// File: src/main/java/com/example/sso/SsoUserProvisioningService.java
@Service
@Slf4j
public class SsoUserProvisioningService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SsoAuditService auditService;


    SsoUserProvisioningService(UserRepository userRepository, RoleRepository roleRepository,
                              SsoAuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditService = auditService;
    }

    public UserAccount createSsoUser(String ssoId,
                                     String uuid,
                                     String fullName,
                                     String email,
                                     Set<Role> roles) {
        try {
            UserAccount user = new UserAccount();
            user.setUsername(null);  // SSO users have no local username
            user.setPasswordHash(null);  // SSO users have no local password
            user.setSsoId(ssoId);
            user.setUuid(uuid);  // For MCC SSO tracking
            user.setFullName(fullName);
            user.setEmail(email);
            user.setRoles(roles);
            user.setAccountNonLocked(true);
            user.setAccountNonExpired(true);
            user.setCredentialsNonExpired(true);
            user.setEnabled(true);
            user.setAuthenticationModes(Set.of(AuthenticationMode.SSO));
            user.setSourceMode(AuthenticationMode.SSO);

            UserAccount saved = userRepository.save(user);

            // Log user.id UUID (non-PII) via auditService — never log raw usernames or emails
            List<String> roleNames = roles.stream().map(Role::getName).collect(Collectors.toList());
            
            auditService.logSsoUserCreated(saved.getId(), roleNames);

            log.atInfo()
               .setMessage("SSO user created")
               .addKeyValue("user.id", saved.getId())
               .addKeyValue("roles", roleNames)
               .addKeyValue("event.action", "user-provisioning")
               .addKeyValue("event.outcome", "success")
               .log();

            return saved;
        } catch (Exception e) {
            log.atError()
               .setMessage("SSO user creation failed")
               .setCause(e)
               .addKeyValue("event.action", "user-provisioning")
               .addKeyValue("event.outcome", "failure")
               .log();
            auditService.logSsoUserCreationFailed(e);
            throw e;
        }
    }
}

// File: src/main/java/com/example/sso/SsoAuditService.java
@Service
@Slf4j
public class SsoAuditService {

    public void logSsoUserCreated(UUID userId, List<String> roles) {
        log.atInfo()
           .setMessage("SSO user provisioned")
           .addKeyValue("user.id", userId)
           .addKeyValue("roles", roles)
           .addKeyValue("event.action", "user-provisioning")
           .addKeyValue("event.outcome", "success")
           .log();
    }

    public void logSsoUserCreationFailed(Throwable error) {
        log.atError()
           .setMessage("SSO user provisioning failed")
           .setCause(error)
           .addKeyValue("event.action", "user-provisioning")
           .addKeyValue("event.outcome", "failure")
           .log();
    }

    public void logAuthoritiesMapped(UUID userId, Set<String> authorities) {
        log.atInfo()
           .setMessage("SSO authorities mapped")
           .addKeyValue("user.id", userId)
           .addKeyValue("authorities", authorities)
           .addKeyValue("event.action", "user-authentication")
           .addKeyValue("event.outcome", "success")
           .log();
    }
}
```

### 4. Implement the authority mapper with role reuse

If the user already exists locally, use the persisted roles. If new, map roles and provision the user.

```java
// File: src/main/java/com/example/sso/UserAuthoritiesMapper.java
@Component
@Slf4j
public class UserAuthoritiesMapper implements GrantedAuthoritiesMapper {

    private final UserRepository userRepository;
    private final UserAuthoritiesConfiguration<?> authConfig;
    private final SsoUserProvisioningService provisioningService;
    private final SsoIdentityResolver idResolver;
    private final SsoAuditService auditService;


    UserAuthoritiesMapper(UserRepository userRepository,
                         UserAuthoritiesConfiguration<?> authConfig,
                         SsoUserProvisioningService provisioningService,
                         SsoIdentityResolver idResolver,
                         SsoAuditService auditService) {
        this.userRepository = userRepository;
        this.authConfig = authConfig;
        this.provisioningService = provisioningService;
        this.idResolver = idResolver;
        this.auditService = auditService;
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(
            Collection<? extends GrantedAuthority> sourceAuthorities) {
        // Called after OAuth2 token is obtained
        // sourceAuthorities contain OIDC claims context
        return sourceAuthorities;
    }

    public Collection<? extends GrantedAuthority> mapAuthoritiesForSso(
            Map<String, Object> claims,
            boolean isMccSso) {

        String ssoId = idResolver.resolveSsoId(claims, isMccSso);
        String email = idResolver.extractEmail(claims);
        String fullName = idResolver.extractFullName(claims);

        try {
            UserAccount user = userRepository.findBySsoId(ssoId).orElse(null);
            Set<Role> roles;

            if (user == null) {
                // First-time SSO login: auto-provision
                log.atInfo()
                   .setMessage("First-time SSO login detected")
                   .addKeyValue("event.action", "user-authentication")
                   .log();

                roles = authConfig.mapRoles(authConfig.fromProvider(ssoId, isMccSso));
                if (roles == null || roles.isEmpty()) {
                    log.atWarn()
                       .setMessage("Role mapping returned empty set")
                       .addKeyValue("event.action", "user-authentication")
                       .log();
                    roles = Set.of();
                }

                user = provisioningService.createSsoUser(
                    ssoId,
                    isMccSso ? ssoId : null,
                    fullName,
                    email,
                    roles
                );
            } else {
                // Returning SSO login: reuse persisted roles
                log.atInfo()
                   .setMessage("Returning SSO login detected")
                   .addKeyValue("user.id", user.getId())
                   .addKeyValue("event.action", "user-authentication")
                   .log();
                roles = user.getRoles();
            }

            Set<GrantedAuthority> authorities = new HashSet<>();
            for (Role role : roles) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
            }

            auditService.logAuthoritiesMapped(user.getId(), authorities.stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));

            return authorities;
        } catch (Exception e) {
            log.atError()
               .setMessage("SSO authority mapping failed")
               .setCause(e)
               .addKeyValue("event.action", "user-authentication")
               .addKeyValue("event.outcome", "failure")
               .log();
            throw new AuthenticationServiceException("Authority mapping failed for SSO login", e);
        }
    }
}
```

### 5. Provide a safe default role mapping

When no custom mapping is supplied, assign a baseline local role instead of granting broad access.

```java
// File: src/main/java/com/example/sso/DefaultUserAuthoritiesConfiguration.java
@Configuration
@Slf4j
public class DefaultUserAuthoritiesConfiguration extends UserAuthoritiesConfiguration<ProviderAttributes> {

    private final CommandExecutor commandExecutor;
    private final MpdsConfigurationProperties mpdsProperties;

    public DefaultUserAuthoritiesConfiguration(UserRepository userRepository,
                                             RoleRepository roleRepository,
                                             CommandExecutor commandExecutor,
                                             MpdsConfigurationProperties mpdsProperties) {
        super(userRepository, roleRepository);
        this.commandExecutor = commandExecutor;
        this.mpdsProperties = mpdsProperties;
    }

    @Override
    protected Set<Role> mapRoles(ProviderAttributes attributes) {
        log.atInfo()
           .setMessage("Applying default role mapping")
           .addKeyValue("event.action", "user-authentication")
           .log();

        Role fallbackRole = roleRepository.findByName("USER").orElse(null);
        if (fallbackRole == null) {
            log.atWarn()
               .setMessage("Default USER role not found in repository")
               .addKeyValue("event.action", "user-authentication")
               .log();
            return Set.of();
        }

        return Set.of(fallbackRole);
    }

    @Override
    protected ProviderAttributes fromProvider(String uuid, boolean isMccSso) {
        if (isMccSso) {
            log.atInfo()
               .setMessage("Retrieving user info from Profile Data Service based on UUID")
               .addKeyValue("uuid", uuid)
               .log();

            ProfileRequestCommand command = new ProfileRequestCommand(uuid, mpdsProperties.getTemplateId());
            ProfileResponse response = commandExecutor.execute(command);

            if (response == null) {
                log.atWarn()
                   .setMessage("Failed to retrieve user info from Profile Data Service")
                   .addKeyValue("uuid", uuid)
                   .log();
                return null;
            }

            return new ProviderAttributes(
                uuid,
                response.getEmail(),
                response.getFullName(),
                response.getGroups()
            );
        }
        return null;
    }

    // Model for provider attributes (e.g., from Profile Data Service / MPDS for MCC SSO)
    public record ProviderAttributes(
        String ssoId,
        String email,
        String fullName,
        List<String> groupsOrDepartments
    ) {}
}

// File: src/main/java/com/example/sso/MpdsConfigurationProperties.java
@Data
@ConfigurationProperties(prefix = "spring.security.sso.oauth2.mpds")
public class MpdsConfigurationProperties {
    private String templateId;
}

```

## 4. Examples

### First SSO login creates a local user

A previously unseen SSO identity gets a local user row with audit trail and the baseline application role.

**Audit log output:**

**Human-Readable Console:**
`2026-03-02T10:15:42.123Z  INFO --- [main] c.e.s.SsoService : First-time SSO login detected`
**Structured Machine JSON:**
`{"@timestamp":"2026-03-02T10:15:42.123Z", "log.level":"INFO", "message":"First-time SSO login detected", "event.action":"user-authentication"}`

**Human-Readable Console:**
`2026-03-02T10:15:42.456Z  INFO --- [main] c.e.s.SsoService : SSO user created`
**Structured Machine JSON:**
`{"@timestamp":"2026-03-02T10:15:42.456Z", "log.level":"INFO", "message":"SSO user created", "user.id":"5c8d3d6c-b3a2-4fcf-8451-b0e25d2b7042", "roles":["USER"], "event.action":"user-provisioning", "event.outcome":"success"}`

**Human-Readable Console:**
`2026-03-02T10:15:42.789Z  INFO --- [main] c.e.s.SsoService : SSO authorities mapped`
**Structured Machine JSON:**
`{"@timestamp":"2026-03-02T10:15:42.789Z", "log.level":"INFO", "message":"SSO authorities mapped", "user.id":"5c8d3d6c-b3a2-4fcf-8451-b0e25d2b7042", "authorities":["ROLE_USER"], "event.action":"user-authentication", "event.outcome":"success"}`

### Returning SSO login reuses persisted roles

The same SSO ID loads the existing local row and its stored roles.

**Audit log output:**

**Human-Readable Console:**
`2026-03-02T11:20:15.100Z  INFO --- [main] c.e.s.SsoService : Returning SSO login detected`
**Structured Machine JSON:**
`{"@timestamp":"2026-03-02T11:20:15.100Z", "log.level":"INFO", "message":"Returning SSO login detected", "user.id":"5c8d3d6c-b3a2-4fcf-8451-b0e25d2b7042", "event.action":"user-authentication"}`

**Human-Readable Console:**
`2026-03-02T11:20:15.250Z  INFO --- [main] c.e.s.SsoService : SSO authorities mapped`
**Structured Machine JSON:**
`{"@timestamp":"2026-03-02T11:20:15.250Z", "log.level":"INFO", "message":"SSO authorities mapped", "user.id":"5c8d3d6c-b3a2-4fcf-8451-b0e25d2b7042", "authorities":["ROLE_USER"], "event.action":"user-authentication", "event.outcome":"success"}`

## 5. Verification

Confirm that:

- MCC SSO users resolve by `afcas.uuid`;
- non-MCC SSO users resolve by `sub`;
- first-seen users are created with `AuthenticationMode.SSO`;
- auto-provisioned users have no local password;
- auto-provisioned users have no local username;
- returning users reuse their stored local roles;
- the default mapper grants only the baseline `USER` role unless overridden;
- SSO user provisioning is audited with user.id and assigned roles;
- returning logins are logged with user.id;
- role mapping failures are logged and prevent authentication.

## 6. Conclusion

This implementation preserves the SSO starter's core separation of concerns: the external provider proves identity, while the application owns the user record and the authorization role set. Auto-provisioning is auditable, and returning users benefit from locally-managed role assignments.

## 7. References

- [Spring Security GrantedAuthoritiesMapper](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/core/authority/mapping/GrantedAuthoritiesMapper.html)
- [Spring Security OAuth2 Login User Mapping](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html)
- [Spring Data JPA Queries](https://docs.spring.io/spring-data/jpa/reference/repositories/query-keywords-reference.html)
