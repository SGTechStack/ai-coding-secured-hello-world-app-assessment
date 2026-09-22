# Standalone: Scheduled Account Hygiene Jobs

## 1. Introduction

This guide shows how to implement scheduled account lifecycle maintenance for standalone applications with application-managed users. It covers inactivity-based disablement, first-login grace periods, role revocation, timezone-safe date comparisons, cron scheduling, comprehensive audit logging, error handling, and execution guards for multi-node deployments.

This matters because standalone identity systems need periodic controls for account hygiene even when users are not actively logging in. By the end, the application will automatically disable inactive accounts, enforce first-login password changes, revoke roles from long-inactive users, and provide detailed audit trails and monitoring signals.

> **Security Best Practice Note (NIST SP 800-63B):**
> Modern identity guidelines from NIST explicitly recommend **against** arbitrary periodic password rotation (e.g., forcing users to change passwords every 90 or 365 days). Passwords should only be forced to change if there is evidence of compromise. Therefore, this standard focuses exclusively on account inactivity, first-login grace periods, and role revocation, omitting automated password expiration.

## 2. Prerequisites

- Spring Boot 4.x with Spring Scheduling or Spring Batch
- A relational database (for user storage and ShedLock)
- A scheduler lock mechanism for multi-instance deployments
- A controllable clock for testing (Java Clock API)

## 3. Implementation

The following implementation demonstrates how to build robust, multi-node-safe batch processing jobs using Spring's native scheduling capabilities (`@Scheduled`) combined with **ShedLock** to prevent concurrent executions across different instances.

### Step 1: Define Account Eligibility Rules

Capture the policy thresholds in code using timezone-aware date comparisons.

```java
@Component
public class AccountEligibilityRules {

    private static final int INACTIVITY_THRESHOLD_DAYS = 90;
    private static final int FIRST_LOGIN_GRACE_DAYS = 30;
    private static final int ROLE_REVOCATION_THRESHOLD_DAYS = 180;
    private final Clock clock;

    AccountEligibilityRules(Clock clock) {
        this.clock = clock;
    }

    public boolean shouldDisableForInactivity(UserAccount user) {
        if (user.getLastLoginAt() == null) return false;
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(INACTIVITY_THRESHOLD_DAYS));
        return user.getLastLoginAt().isBefore(cutoff);
    }

    public boolean shouldDisableForFirstLoginGrace(UserAccount user) {
        if (!user.isRequirePasswordChange() || user.getCreatedAt() == null) return false;
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(FIRST_LOGIN_GRACE_DAYS));
        return user.getCreatedAt().isBefore(cutoff);
    }

    public boolean shouldRemoveRoles(UserAccount user) {
        if (user.getLastLoginAt() == null || user.getRoles().isEmpty()) return false;
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(ROLE_REVOCATION_THRESHOLD_DAYS));
        return user.getLastLoginAt().isBefore(cutoff);
    }
}
```

### Step 2: Implement State Transitions with Audit Logging

Ensure all administrative changes emit structured logs containing key-value pairs (e.g., `username={}`) to comply with the project's Logging Contract.

```java
@Service
@Slf4j
public class AccountMutations {

    public void disableAccount(UserAccount user, String reason) {
        user.setEnabled(false);
        user.setDisabledAt(Instant.now());
        // Use user.id (UUID) and event.action/outcome for ECS alignment
        log.atWarn()
           .setMessage("User account disabled")
           .addKeyValue("user.id", user.getId())
           .addKeyValue("reason", reason)
           .addKeyValue("event.action", "access-control")
           .addKeyValue("event.outcome", "success")
           .log();
    }

    public void revokeRoles(UserAccount user) {
        int roleCount = user.getRoles().size();
        user.setRoles(Set.of());
        log.atWarn()
           .setMessage("Roles revoked due to inactivity")
           .addKeyValue("user.id", user.getId())
           .addKeyValue("revoked_count", roleCount)
           .addKeyValue("event.action", "access-control")
           .addKeyValue("event.outcome", "success")
           .log();
    }
}
```

### Step 3: Configure the Job Schedules and ShedLock

Keep schedules explicit and use UTC to avoid Daylight Saving Time issues.

```yaml
app:
  batch:
    cron:
      disable-account: "0 0 1 * * *"  # 1 AM daily
      remove-roles: "0 0 4 * * *"     # 4 AM daily
    timezone: "UTC"
```

### Step 4: Implement the Scheduler

Run each policy on its own trigger with distributed locking for multi-node safety. It is critical to manage transactions properly so that a single failure doesn't roll back the entire batch.

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountHygieneScheduler {

    private final UserRepository users;
    private final AccountEligibilityRules rules;
    private final AccountMutations mutations;
    
    // Self-injection or separate service to ensure @Transactional boundary per item
    private final UserHygieneService hygieneService; 

    @Scheduled(cron = "${app.batch.cron.disable-account}", zone = "${app.batch.timezone}")
    @SchedulerLock(name = "disableAccountJob", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void runDisableAccountJob() {
        log.atInfo()
           .setMessage("Scheduled job started")
           .addKeyValue("event.kind", "event")
           .addKeyValue("event.category", List.of("batch"))
           .addKeyValue("event.type", List.of("job-start"))
           .addKeyValue("batch.job.name", "disable-account")
           .log();
           
        int processedCount = 0;

        // Use pagination for memory safety
        for (int page = 0; ; page++) {
            Page<UserAccount> pageOfUsers = users.findAll(PageRequest.of(page, 1000));
            
            for (UserAccount user : pageOfUsers.getContent()) {
                if (rules.shouldDisableForInactivity(user)) {
                    hygieneService.disableUser(user, "inactivity");
                    processedCount++;
                } else if (rules.shouldDisableForFirstLoginGrace(user)) {
                    hygieneService.disableUser(user, "first_login_grace_exceeded");
                    processedCount++;
                }
            }
            if (!pageOfUsers.hasNext()) break;
        }
        
        log.atInfo()
           .setMessage("Scheduled job completed")
           .addKeyValue("event.kind", "event")
           .addKeyValue("event.category", List.of("batch"))
           .addKeyValue("event.type", List.of("job-end"))
           .addKeyValue("event.outcome", "success")
           .addKeyValue("batch.job.name", "disable-account")
           .addKeyValue("record.success", processedCount)
           .log();
    }

    @Scheduled(cron = "${app.batch.cron.remove-roles}", zone = "${app.batch.timezone}")
    @SchedulerLock(name = "removeRolesJob", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void runRemoveRolesJob() {
        log.atInfo()
           .setMessage("Scheduled job started")
           .addKeyValue("event.kind", "event")
           .addKeyValue("event.category", List.of("batch"))
           .addKeyValue("event.type", List.of("job-start"))
           .addKeyValue("batch.job.name", "remove-roles")
           .log();
           
        int processedCount = 0;

        for (int page = 0; ; page++) {
            Page<UserAccount> pageOfUsers = users.findAll(PageRequest.of(page, 1000));
            
            for (UserAccount user : pageOfUsers.getContent()) {
                if (rules.shouldRemoveRoles(user)) {
                    hygieneService.revokeRoles(user);
                    processedCount++;
                }
            }
            if (!pageOfUsers.hasNext()) break;
        }
        
        log.atInfo()
           .setMessage("Scheduled job completed")
           .addKeyValue("event.kind", "event")
           .addKeyValue("event.category", List.of("batch"))
           .addKeyValue("event.type", List.of("job-end"))
           .addKeyValue("event.outcome", "success")
           .addKeyValue("batch.job.name", "remove-roles")
           .addKeyValue("record.success", processedCount)
           .log();
    }
}

@Service
@RequiredArgsConstructor
class UserHygieneService {
    private final UserRepository users;
    private final AccountMutations mutations;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    // Execute in a new transaction so one failure doesn't crash the batch
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void disableUser(UserAccount user, String reason) {
        mutations.disableAccount(user, reason);
        users.save(user);
        invalidateUserSessions(user);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeRoles(UserAccount user) {
        mutations.revokeRoles(user);
        users.save(user);
        invalidateUserSessions(user);
    }

    private void invalidateUserSessions(UserAccount user) {
        // OWASP Requirement: Actively terminate sessions when account state or privileges change
        Set<String> principalNames = new HashSet<>();
        if (user.getUsername() != null) {
            principalNames.add(user.getUsername());
        }
        if (user.getUuid() != null) {
            principalNames.add(user.getUuid());
        }
        if (user.getSsoId() != null) {
            principalNames.add(user.getSsoId());
        }
        for (String principalName : principalNames) {
            sessionRepository.findByPrincipalName(principalName)
                .keySet()
                .forEach(sessionRepository::deleteById);
        }
    }
}
```

## 4. Examples

### Disable an inactive account

This applies when the user has not accessed the application for 90 days.

**Audit log output:**

**Human-Readable Console:**
`2026-03-02T01:15:42.123Z  WARN --- [main] c.e.b.AccountMutations : User account disabled`
**Structured Machine JSON:**
`{"@timestamp":"2026-03-02T01:15:42.123Z", "log.level":"WARN", "message":"User account disabled", "user.id":"5c8d3d6c-b3a2-4fcf-8451-b0e25d2b7042", "reason":"inactivity", "event.action":"access-control", "event.outcome":"success"}`

### Revoke roles after prolonged inactivity

This removes application roles after 180 days of inactivity.

**Audit log output:**

**Human-Readable Console:**
`2026-03-02T04:20:15.456Z  WARN --- [main] c.e.b.AccountMutations : Roles revoked due to inactivity`
**Structured Machine JSON:**
`{"@timestamp":"2026-03-02T04:20:15.456Z", "log.level":"WARN", "message":"Roles revoked due to inactivity", "user.id":"5c8d3d6c-b3a2-4fcf-8451-b0e25d2b7042", "revoked_count":3, "event.action":"access-control", "event.outcome":"success"}`

## 5. Verification

Confirm that:
- a user who has not changed a temporary first-login password within 30 days becomes disabled.
- a user inactive for 90 days becomes disabled.
- a user inactive for 180 days loses all roles.
- concurrent nodes do not execute the same job at the same time (ShedLock verification).
- batch processing uses pagination to avoid out-of-memory errors.

## 5. Conclusion

By implementing these standardized lifecycle jobs, the application gains robust, recurring controls for account hygiene. This approach relies entirely on generic framework features (Spring Scheduling) and community-standard locking (ShedLock) without tightly coupling to internal library implementations.

## 6. References

- [Spring Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [ShedLock](https://github.com/lukas-krecan/ShedLock) - Distributed scheduler lock for multi-node deployments
- [NIST SP 800-63B: Digital Identity Guidelines](https://pages.nist.gov/800-63-3/)