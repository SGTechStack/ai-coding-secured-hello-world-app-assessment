# Scheduled Account Inactivity and Role Management Jobs

## 1. Introduction

This guide shows how to implement scheduled account inactivity tracking and role management for SSO applications. It covers inactivity-based account disablement, role revocation for long-inactive users, timezone-safe date comparisons, cron scheduling, comprehensive audit logging, error handling, and execution guards for multi-node deployments.

This matters because even in SSO environments where authentication is handled by an external identity provider, your application needs to maintain its own account hygiene based on application-specific activity. Users who haven't accessed your application for extended periods should have their local account disabled and roles revoked, even if they remain active in the IdP. By the end, the application will automatically disable inactive accounts, revoke roles from long-inactive users, and provide detailed audit trails and monitoring signals.

**Note:** First-login grace periods and credentials management are handled by the SSO identity provider and not covered in this guide.

## 2. Prerequisites

- Spring Boot 4.x with Spring Scheduling
- A persistent user store (typically populated via SSO auto-provisioning)
- **ShedLock**: For distributed scheduler locking across multi-instance deployments.
- `spring-session-jdbc` for active session invalidation upon privilege revocation.
- A controllable clock for testing (Java Clock API)
- Lombok (for @Slf4j logging)

## 3. Steps

### 1. Define account eligibility rules with timezone-safe date handling

Capture the policy thresholds in code using timezone-aware date comparisons so each job has an explicit predicate.

```java
// File: src/main/java/com/example/batch/AccountEligibilityRules.java
@Component
public class AccountEligibilityRules {

    private static final int INACTIVITY_THRESHOLD_DAYS = 90;
    private static final int ROLE_REVOCATION_THRESHOLD_DAYS = 180;

    private final Clock clock;

    AccountEligibilityRules(Clock clock) {
        this.clock = clock;
    }

    public boolean shouldDisableForInactivity(UserAccount user) {
        if (user.getLastLoginAt() == null) {
            return false;
        }

        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(INACTIVITY_THRESHOLD_DAYS));
        return user.getLastLoginAt().isBefore(cutoff);
    }

    public boolean shouldRemoveRoles(UserAccount user) {
        if (user.getLastLoginAt() == null || user.getRoles().isEmpty()) {
            return false;
        }

        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(ROLE_REVOCATION_THRESHOLD_DAYS));
        return user.getLastLoginAt().isBefore(cutoff);
    }
}
```

### 2. Implement state transitions with audit logging

Each processor applies one narrow change. To comply with OWASP Session Management guidelines, active server-side sessions must be destroyed when privileges change.

```java
// File: src/main/java/com/example/batch/AccountMutations.java
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountMutations {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public void disableAccount(UserAccount user, String reason) {
        user.setEnabled(false);
        user.setDisabledAt(Instant.now());
        
        invalidateUserSessions(user);
        
        // Use user.id (UUID) and event.action/outcome for ECS alignment
        log.atWarn()
           .setMessage("User account disabled due to inactivity")
           .addKeyValue("user.id", user.getId())
           .addKeyValue("reason", reason)
           .addKeyValue("event.action", "access-control")
           .addKeyValue("event.outcome", "success")
           .log();
    }

    public void revokeRoles(UserAccount user) {
        int roleCount = user.getRoles().size();
        user.setRoles(Set.of());
        
        invalidateUserSessions(user);
        
        log.atWarn()
           .setMessage("Roles revoked from inactive user")
           .addKeyValue("user.id", user.getId())
           .addKeyValue("revoked_count", roleCount)
           .addKeyValue("reason", "inactivity")
           .addKeyValue("event.action", "access-control")
           .addKeyValue("event.outcome", "success")
           .log();
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

### 3. Configure the job schedules and ShedLock

Keep the schedules explicit and externalized so operations can tune them without code changes. Use ShedLock to prevent duplicate runs across instances.

```xml
<!-- pom.xml -->
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
    <version>4.42.0</version>
</dependency>
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-jdbc</artifactId>
    <version>4.42.0</version>
</dependency>
```

```yaml
# File: src/main/resources/application.yml
app:
  batch:
    enabled: true  # Global enable/disable
    cron:
      disable-account: "0 0 1 * * *"  # 1 AM daily
      remove-roles: "0 0 2 * * *"  # 2 AM daily
    timezone: "UTC"  # Use UTC to avoid DST issues
    batch-size: 1000  # Process users in pages to avoid memory issues

spring:
  task:
    scheduling:
      pool:
        size: 2  # Number of parallel job threads
      thread-name-prefix: "batch-hygiene-"
```

```sql
-- DDL for ShedLock table (required for JdbcTemplateLockProvider)
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
```

```java
// File: src/main/java/com/example/batch/SchedulerConfig.java
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import javax.sql.DataSource;
import java.time.Clock;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "15m")
public class SchedulerConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime() // Keeps cluster locking sync'd to DB time
                .build()
        );
    }
}
```

### 4. Implement the repository and scheduler with database-filtered paging

Define the `UserRepository` interface with optimized query methods. This avoids performing slow and expensive full-table scans. Additionally, rewrite the scheduler jobs to retrieve only eligible records from the database using page-0 query chunking, which safely shifts modified items out of scope while preventing infinite loops.

```java
// File: src/main/java/com/example/batch/UserRepository.java
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {

    List<UserAccount> findByEnabledTrueAndLastLoginAtBeforeOrderByIdAsc(Instant cutoff, Pageable pageable);

    List<UserAccount> findByLastLoginAtBeforeAndRolesIsNotEmptyOrderByIdAsc(Instant cutoff, Pageable pageable);
}
```

```java
// File: src/main/java/com/example/batch/AccountHygieneScheduler.java
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountHygieneScheduler {

    private final UserRepository users;
    private final AccountEligibilityRules rules;
    private final UserHygieneService hygieneService;
    private final BatchJobAuditService auditService;
    private final Clock clock;

    @Value("${app.batch.enabled:true}")
    private boolean batchEnabled;

    @Value("${app.batch.batch-size:1000}")
    private int batchSize;

    @Scheduled(cron = "${app.batch.cron.disable-account}", zone = "${app.batch.timezone}")
    @SchedulerLock(name = "disableAccountJob", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void runDisableAccountJob() {
        if (!batchEnabled) return;

        log.atInfo()
           .setMessage("Batch job started")
           .addKeyValue("event.kind", "event")
           .addKeyValue("event.category", List.of("batch"))
           .addKeyValue("event.type", List.of("job-start"))
           .addKeyValue("batch.job.name", "disable-account")
           .log();
           
        Instant startTime = Instant.now();
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(90));
        int processedCount = 0;
        int errorCount = 0;
        int consecutiveErrors = 0;
        final int maxConsecutiveErrors = 50;

        while (true) {
            // Retrieve only the first page of active matching accounts.
            // As we disable accounts, they will fall out of this query's scope.
            List<UserAccount> chunk = users.findByEnabledTrueAndLastLoginAtBeforeOrderByIdAsc(cutoff, PageRequest.of(0, batchSize));
            if (chunk.isEmpty()) {
                break;
            }

            int processedInChunk = 0;
            for (UserAccount user : chunk) {
                try {
                    if (rules.shouldDisableForInactivity(user)) {
                        hygieneService.disableUser(user, "inactivity");
                        processedCount++;
                        processedInChunk++;
                        consecutiveErrors = 0;
                    }
                } catch (Exception e) {
                    log.atError()
                       .setMessage("Failed to process user account")
                       .setCause(e)
                       .addKeyValue("batch.job.name", "disable-account")
                       .addKeyValue("user.id", user.getId())
                       .addKeyValue("event.action", "access-control")
                       .addKeyValue("event.outcome", "failure")
                       .log();
                    errorCount++;
                    consecutiveErrors++;

                    if (consecutiveErrors >= maxConsecutiveErrors) {
                        log.atError()
                           .setMessage("Too many consecutive errors, aborting job")
                           .addKeyValue("batch.job.name", "disable-account")
                           .log();
                        break;
                    }
                }
            }

            // Safety mechanism: If we processed nothing in this chunk (e.g. all failed),
            // break to avoid getting stuck in an infinite loop processing the same failing items on page 0.
            if (processedInChunk == 0 || consecutiveErrors >= maxConsecutiveErrors) {
                log.atWarn()
                   .setMessage("Aborted job execution to prevent infinite loop or due to error threshold")
                   .addKeyValue("batch.job.name", "disable-account")
                   .log();
                break;
            }
        }

        Duration duration = Duration.between(startTime, Instant.now());
        auditService.logJobExecution("disable-account", processedCount, errorCount, duration);
    }

    @Scheduled(cron = "${app.batch.cron.remove-roles}", zone = "${app.batch.timezone}")
    @SchedulerLock(name = "removeRolesJob", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void runRemoveRolesJob() {
        if (!batchEnabled) return;

        log.atInfo()
           .setMessage("Batch job started")
           .addKeyValue("event.kind", "event")
           .addKeyValue("event.category", List.of("batch"))
           .addKeyValue("event.type", List.of("job-start"))
           .addKeyValue("batch.job.name", "remove-roles")
           .log();
           
        Instant startTime = Instant.now();
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(180));
        int processedCount = 0;
        int errorCount = 0;
        int consecutiveErrors = 0;
        final int maxConsecutiveErrors = 50;

        while (true) {
            // Retrieve only the first page of accounts with roles.
            // As roles are revoked, they will fall out of this query's scope.
            List<UserAccount> chunk = users.findByLastLoginAtBeforeAndRolesIsNotEmptyOrderByIdAsc(cutoff, PageRequest.of(0, batchSize));
            if (chunk.isEmpty()) {
                break;
            }

            int processedInChunk = 0;
            for (UserAccount user : chunk) {
                try {
                    if (rules.shouldRemoveRoles(user)) {
                        hygieneService.revokeRoles(user);
                        processedCount++;
                        processedInChunk++;
                        consecutiveErrors = 0;
                    }
                } catch (Exception e) {
                    log.atError()
                       .setMessage("Failed to revoke roles from user")
                       .setCause(e)
                       .addKeyValue("batch.job.name", "remove-roles")
                       .addKeyValue("user.id", user.getId())
                       .addKeyValue("event.action", "access-control")
                       .addKeyValue("event.outcome", "failure")
                       .log();
                    errorCount++;
                    consecutiveErrors++;

                    if (consecutiveErrors >= maxConsecutiveErrors) {
                        break;
                    }
                }
            }

            // Safety mechanism: If we processed nothing in this chunk (e.g. all failed),
            // break to avoid getting stuck in an infinite loop processing the same failing items on page 0.
            if (processedInChunk == 0 || consecutiveErrors >= maxConsecutiveErrors) {
                break;
            }
        }

        Duration duration = Duration.between(startTime, Instant.now());
        auditService.logJobExecution("remove-roles", processedCount, errorCount, duration);
    }
}

@Service
@RequiredArgsConstructor
class UserHygieneService {
    private final UserRepository users;
    private final AccountMutations mutations;

    // Execute in a new transaction so one failure doesn't crash the batch
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void disableUser(UserAccount user, String reason) {
        mutations.disableAccount(user, reason);
        users.save(user);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeRoles(UserAccount user) {
        mutations.revokeRoles(user);
        users.save(user);
    }
}
```

### 5. Add job audit service for compliance

Log all job executions with results and timings.

```java
// File: src/main/java/com/example/batch/BatchJobAuditService.java
@Slf4j
@Service
public class BatchJobAuditService {

    public void logJobExecution(String jobName, int processedCount, int errorCount, Duration duration) {
        log.atInfo()
           .setMessage("Batch job processing summary")
           .addKeyValue("event.kind", "event")
           .addKeyValue("event.category", List.of("batch"))
           .addKeyValue("event.type", List.of("job-end"))
           .addKeyValue("event.outcome", "success")
           .addKeyValue("batch.job.name", jobName)
           .addKeyValue("record.success", processedCount)
           .addKeyValue("error_count", errorCount)
           .addKeyValue("duration_ms", duration.toMillis())
           .log();
    }

    public void logJobFailure(String jobName, Throwable error) {
        log.atError()
           .setMessage("Batch job execution failed")
           .setCause(error)
           .addKeyValue("event.kind", "event")
           .addKeyValue("event.category", List.of("batch"))
           .addKeyValue("event.type", List.of("job-end"))
           .addKeyValue("event.outcome", "failure")
           .addKeyValue("batch.job.name", jobName)
           .log();
    }
}
```

## 4. Examples

### Disable an inactive account

This applies when the SSO user has not accessed your application for 90 days (even if they're still active in the IdP).

**Audit log output:**

**Human-Readable Console:**
`2026-03-02T01:15:42.123Z  WARN --- [batch-hygiene-1] c.e.b.AccountMutations : User account disabled due to inactivity`
**Structured Machine JSON:**
`{"@timestamp":"2026-03-02T01:15:42.123Z", "log.level":"WARN", "message":"User account disabled due to inactivity", "user.id":"5c8d3d6c-b3a2-4fcf-8451-b0e25d2b7042", "reason":"inactivity", "event.action":"access-control", "event.outcome":"success"}`

### Revoke roles after prolonged inactivity

This keeps the account record but removes application-specific roles after 180 days of inactivity.

**Audit log output:**

**Human-Readable Console:**
`2026-03-02T02:20:15.456Z  WARN --- [batch-hygiene-2] c.e.b.AccountMutations : Roles revoked from inactive user`
**Structured Machine JSON:**
`{"@timestamp":"2026-03-02T02:20:15.456Z", "log.level":"WARN", "message":"Roles revoked from inactive user", "user.id":"5c8d3d6c-b3a2-4fcf-8451-b0e25d2b7042", "revoked_count":3, "reason":"inactivity", "event.action":"access-control", "event.outcome":"success"}`

### Job execution summary

**Typical job completion logs:**

**Human-Readable Console:**
`2026-03-02T01:00:02.100Z  INFO --- [batch-hygiene-1] c.e.b.AccountHygieneScheduler : Batch job started`
**Structured Machine JSON:**
`{"@timestamp":"2026-03-02T01:00:02.100Z", "log.level":"INFO", "message":"Batch job started", "batch.job.name":"disable-account", "event.kind":"event", "event.category":["batch"], "event.type":["job-start"]}`

**Human-Readable Console:**
`2026-03-02T01:00:15.250Z  INFO --- [batch-hygiene-1] c.e.b.BatchJobAuditService : Batch job processing summary`
**Structured Machine JSON:**
`{"@timestamp":"2026-03-02T01:00:15.250Z", "log.level":"INFO", "message":"Batch job processing summary", "batch.job.name":"disable-account", "record.success":47, "error_count":0, "duration_ms":13150, "event.kind":"event", "event.category":["batch"], "event.type":["job-end"], "event.outcome":"success"}`


## 5. Verification

Confirm that:

- a user inactive for 90 days (based on application login, not IdP activity) becomes disabled in your application;
- a user inactive for 180 days loses all application-specific roles;
- concurrent nodes do not execute the same job at the same time;
- job executions are logged with start time, completion status, item counts, and duration using standard ECS keys;
- individual account state changes are logged with the user.id, reason, and timestamp;
- batch processing uses pagination to avoid out-of-memory errors;
- failed items are logged individually without stopping the entire job, including user.id;
- disabled accounts can be re-enabled when the user next logs in via SSO (if your auto-provisioning logic handles this).

## 6. Conclusion

This implementation gives SSO applications the account hygiene controls they need for managing application-specific user state: inactivity-based disablement, role revocation for long-inactive users, timezone-safe date handling, comprehensive audit trails, multi-node safe job execution with distributed locking, and detailed observability for operations monitoring.

The key difference from standalone applications is that credentials lifecycle management (first-login grace periods) is delegated to the SSO identity provider, allowing this guide to focus purely on application activity tracking and role management.

## 7. References

### Spring Framework
- [Spring Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html) - @Scheduled annotation and cron expressions

### Java Platform
- [Java Time API (Clock)](https://docs.oracle.com/javase/8/docs/api/java/time/Clock.html) - Testable time abstractions for date comparisons

### Libraries
- [ShedLock](https://github.com/lukas-krecan/ShedLock) - Distributed scheduler lock for multi-node deployments
