# Automatic Database Role Synchronization and Deleted Role Backups

## 1. Introduction

This guide implements **Database Role Synchronization and Backup Management**, a design pattern that makes role-to-authority configuration files the single source of truth while protecting against data corruption or orphaned mappings. When roles are updated or removed in the configuration, the application automatically synchronizes the database representation on startup. Crucially, any roles removed from the configuration are backed up into a dedicated archive table alongside the IDs of all users who held that role, preventing administrative data loss.

**Key Design Principles:**
- **Configuration as Source of Truth:** Database roles and privileges are dynamically loaded from YAML configuration files on application startup.
- **Dynamic Synchronization:** Active configuration changes (added or deleted roles) are synchronized to the database schema.
- **Data Protection via Role Archival:** Removed roles are archived (via a `DeletedRole` record) with their associated user memberships captured before the role is deleted from the active registry.
- **Developer/Testing User Seeding:** Provides clean environment seeding and database user synchronization configurations for rapid testing resets.

## 2. Prerequisites

- Spring Boot 4.x (Java 21/25)
- Spring Security 7.x
- Spring Data JPA
- Lombok (for @Data and @SuperBuilder classes)
- A relational database (e.g., PostgreSQL, MySQL, or H2)

## 3. Implementation

### Step 1: Model the Predefined Roles and Properties in YAML

Specify configurations to control role loading, user initialization, and DB synchronization flags.

```yaml
# File: src/main/resources/application.yml
spring:
  security:
    sso:
      # Startup Flags
      init-users: true       # Enables seeding users from properties on first run
      sync-db-roles: true    # Syncs YAML role mappings to database on startup
      sync-db-users: false   # Deletes and re-seeds all users (useful for dev resets)

      # 1. Role-to-Authority Mapping
      predefined-roles-and-privileges:
        ADMIN: [USER_READ, USER_WRITE, ROLE_ADMIN]
        MANAGER: [USER_READ, USER_WRITE]
        USER: [USER_READ]

      # 2. Seeding Developer Accounts
      users:
        - username: developer
          first-name: Dev
          last-name: User
          full-name: Developer User
          roles: [USER, MANAGER]
          require-password-change: true
```

### Step 2: Define the Active and Archived Role Entities

Create standard role entities alongside a dedicated deleted roles backup entity.

```java
// File: src/main/java/com/example/sso/Role.java
@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String name;
}

// File: src/main/java/com/example/sso/DeletedRole.java
@Entity
@Table(name = "deleted_roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeletedRole {
    @Id
    private UUID id; // Reuses original role ID

    @Column(nullable = false)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "deleted_role_users", joinColumns = @JoinColumn(name = "deleted_role_id"))
    @Column(name = "user_id")
    private Set<UUID> userIdsWithDeletedRole;

    @Column(name = "datetime_last_updated", nullable = false)
    private Timestamp datetimeLastUpdated;
}
```

### Step 3: Set up JPA Repositories

```java
// File: src/main/java/com/example/sso/RoleRepository.java
public interface RoleRepository extends ListCrudRepository<Role, UUID> {
    Optional<Role> findByName(String name);
    List<Role> findByNameIn(Collection<String> names);
}

// File: src/main/java/com/example/sso/DeletedRoleRepository.java
public interface DeletedRoleRepository extends ListCrudRepository<DeletedRole, UUID> {
}
```

### Step 4: Implement Startup Synchronizer and Backup Logic

Implement the startup initialization and synchronization configurations, handling backups of deleted roles and user references dynamically.

```java
// File: src/main/java/com/example/sso/SsoUserStartupConfiguration.java
@Slf4j
@RequiredArgsConstructor
@Transactional
public class SsoUserStartupConfiguration {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DeletedRoleRepository deletedRoleRepository;
    private final SsoSecurityProperties securityProperties;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (isFirstRun() && securityProperties.isInitUsers()) {
            initPredefinedRoles();
            initialiseUsers();
        } else {
            if (securityProperties.isSyncDbRoles()) {
                syncDBRolesBasedOnPredefinedRoles();
            }
            if (securityProperties.isSyncDbUsers()) {
                syncDBUsersBasedOnDefinedUsers();
            }
        }
    }

    protected boolean isFirstRun() {
        return userRepository.count() == 0 && roleRepository.count() == 0;
    }

    private void initPredefinedRoles() {
        log.atInfo()
           .setMessage("Initialising default roles from configuration")
           .addKeyValue("event.action", "application-startup")
           .log();
        Set<Role> definedRoles = securityProperties.getPredefinedRolesAndPrivileges().keySet().stream()
                .map(roleName -> Role.builder().name(roleName).build())
                .collect(Collectors.toSet());
        roleRepository.saveAll(definedRoles);
    }

    private void syncDBRolesBasedOnPredefinedRoles() {
        log.atWarn()
           .setMessage("Syncing configuration roles with database")
           .addKeyValue("event.action", "user-administration")
           .log();
        Map<String, List<String>> predefinedRoles = securityProperties.getPredefinedRolesAndPrivileges();
        List<Role> databaseRoles = roleRepository.findAll();

        deleteRolesRemovedFromConfig(predefinedRoles, databaseRoles);
        createRolesAddedToConfig(predefinedRoles, databaseRoles);
    }

    private void deleteRolesRemovedFromConfig(Map<String, List<String>> predefinedRoles, List<Role> databaseRoles) {
        List<Role> rolesToDelete = databaseRoles.stream()
                .filter(role -> predefinedRoles.get(role.getName()) == null)
                .collect(Collectors.toList());

        if (rolesToDelete.isEmpty()) {
            return;
        }

        log.atWarn()
           .setMessage("Found removed roles in configuration. Proceeding with database cleanup and backups.")
           .addKeyValue("event.action", "user-administration")
           .log();

        for (Role role : rolesToDelete) {
            // Backup the deleted role and all associated user IDs
            backupDeletedRole(role);

            // Disassociate users from this role before deleting
            List<UserAccount> users = userRepository.findByRolesId(role.getId());
            users.forEach(user -> user.getRoles().remove(role));
            userRepository.saveAll(users);
        }

        roleRepository.deleteAll(rolesToDelete);
    }

    private void backupDeletedRole(Role role) {
        log.atWarn()
           .setMessage("Backing up deleted role")
           .addKeyValue("role.name", role.getName())
           .addKeyValue("event.action", "user-administration")
           .log();
        List<UserAccount> usersWithRole = userRepository.findByRolesId(role.getId());
        Set<UUID> userIds = usersWithRole.stream().map(UserAccount::getId).collect(Collectors.toSet());

        DeletedRole backup = DeletedRole.builder()
                .id(role.getId())
                .name(role.getName())
                .userIdsWithDeletedRole(userIds)
                .datetimeLastUpdated(new Timestamp(System.currentTimeMillis()))
                .build();

        deletedRoleRepository.save(backup);
    }

    private void createRolesAddedToConfig(Map<String, List<String>> predefinedRoles, List<Role> databaseRoles) {
        Set<String> existingRoleNames = databaseRoles.stream().map(Role::getName).collect(Collectors.toSet());
        List<Role> rolesToCreate = predefinedRoles.keySet().stream()
                .filter(roleName -> !existingRoleNames.contains(roleName))
                .map(roleName -> Role.builder().name(roleName).build())
                .collect(Collectors.toList());

        if (!rolesToCreate.isEmpty()) {
            log.atInfo()
               .setMessage("Creating new roles added to configuration")
               .addKeyValue("roles", rolesToCreate.stream().map(Role::getName).collect(Collectors.toList()))
               .addKeyValue("event.action", "user-administration")
               .log();
            roleRepository.saveAll(rolesToCreate);
        }
    }

    private void initialiseUsers() {
        log.atInfo()
           .setMessage("Initialising default users on first startup")
           .addKeyValue("event.action", "user-provisioning")
           .log();
        List<UserAccount> users = securityProperties.getUsers().stream()
                .map(this::createUserEntity)
                .collect(Collectors.toList());
        userRepository.saveAll(users);
    }

    private void syncDBUsersBasedOnDefinedUsers() {
        log.atWarn()
           .setMessage("Force-syncing users. Resetting user table and re-seeding from configuration.")
           .addKeyValue("event.action", "user-provisioning")
           .log();
        userRepository.deleteAll();
        initialiseUsers();
    }

    private UserAccount createUserEntity(SsoSecurityProperties.UserProperties userProp) {
        List<Role> roles = roleRepository.findByNameIn(userProp.getRoles());
        return UserAccount.builder()
                .username(userProp.getUsername())
                .password(passwordEncoder.encode("TemporaryStartPassword123!"))
                .firstName(userProp.getFirstName())
                .lastName(userProp.getLastName())
                .fullName(userProp.getFullName())
                .roles(new HashSet<>(roles))
                .requirePasswordChange(userProp.isRequirePasswordChange())
                .enabled(true)
                .build();
    }
}
```

## 4. Security and Architectural Best Practices

When implementing automatic database role synchronization and startup seeding, adhere to the following production-grade best practices:

### 1. Database Schema vs. Seed Data Management
- **Use Flyway or Liquibase for Schema (DDL):** Do not rely on Hibernate's `ddl-auto` in production. Always manage the creation of the `roles` and `deleted_roles` tables through versioned database migration scripts.
- **Limit Startup Runners to Seed Data (DML):** Use startup event listeners (`ApplicationReadyEvent` or `CommandLineRunner`) strictly for configuration-bound reference data or bootstrap user seeding.

### 2. Secure Password Management
- **Never Hardcode Default Passwords:** The temporary password in the code snippet is for illustration. In production, default user passwords should be loaded from secure environment variables or a secrets manager, and always hashed using a strong encoder such as `BCryptPasswordEncoder` or `Argon2`.
- **Enforce Password Reset:** Always flag newly seeded users with `requirePasswordChange: true` to force users to configure their own secure credentials upon first login.

### 3. Mitigation of Stale Persistent Sessions
- **Session Invalidation for Modified Roles:** If role mappings are deleted, any user with active persistent sessions (e.g., stored in Redis or database-backed `HttpSession`) will hold stale permissions. In production, query the session repository and programmatically invalidate the sessions of all affected user IDs recorded during the `DeletedRole` archival step.

### 4. Audit Trail Compliance
- **Audit Log Integrity:** Maintain the `deleted_roles` archive table indefinitely as part of the security audit trail. Ensure deletion commands do not cascade to this table.

## 5. Verification

1. **Role Loading**: Start the application on an empty database. Verify that all predefined roles are populated automatically in the `roles` table.
2. **Synchronize Additions**: Add a new role `OPERATOR` to the YAML config and restart. Verify that `OPERATOR` is added to the database.
3. **Synchronize Deletions & Backups**: Remove `MANAGER` from the YAML configurations. Ensure at least one user held the `MANAGER` role before restart. Start the application and verify:
   - `MANAGER` is deleted from the `roles` table.
   - A backup entry for `MANAGER` is created in the `deleted_roles` table.
   - The backup lists the ID of the user(s) who held the `MANAGER` role.
   - The user(s) no longer have the `MANAGER` role mapped to their active credentials.
4. **Environment Reset Sync**: Toggle `sync-db-users: true` in development, modify some user parameters in configuration, restart, and confirm database user records are re-seeded correctly.

## 6. Conclusion

This role-synchronization pattern maintains configuration integrity while guarding against data loss. By combining automated database sync with historical backups (`DeletedRole`), the starter guarantees that security policy changes can be safely rolled out through configuration updates without risking orphaned user mappings.

## 7. References

- [Spring Boot: Customizing Application Startup](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.spring-application.startup-tracking)
- [Spring Data JPA: Saving Entities](https://docs.spring.io/spring-data/jpa/reference/repositories/core-concepts.html)
- [OWASP: Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [Spring Security: Persistent Session Management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)

