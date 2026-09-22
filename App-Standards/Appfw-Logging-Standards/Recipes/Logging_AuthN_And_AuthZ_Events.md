# Logging Authentication and Authorisation Events

## 1. Introduction
Authentication and authorisation events require accurate, consistent logging for audit trails, compliance, and incident investigation. User identifiers must never appear in raw form, failure messages must not reveal whether an account exists, and every event must capture who acted, what happened, and whether it succeeded.

This guide uses Spring Security's event model to centralise auth logging, `user.id` UUIDs to identify users without exposing PII, and MDC propagation to carry user context across all log entries in a request. It covers authentication, logout, session lifecycle, MFA, and authorisation events.

## 2. Prerequisites
- Spring Boot 4.0+
- Spring Security on the classpath
- Structured logging enabled so log fields appear in structured output. See [Structured Logging, Trace Correlation, and Context Propagation](Structured_Logging_Trace_Correlation_And_Context_Propagation.md) for setup instructions
- Familiarity with MDC filters. See [Enriching Logs with MDC](Enriching_Logs_With_MDC.md)
- Schema compliance: Use field values from [Log_Schema.md](../Log_Schema.md) for `event.category`, `event.type`, `event.action`, and `error_category` to ensure consistency across services

## 3. Resolve user identity for logging

Raw usernames and email addresses must never appear in logs. Credentials must never be logged. Always use the system-generated `user.id` UUID — it is non-PII, directly debuggable via a database lookup, and consistent across all services.

After a successful authentication, `Authentication.getPrincipal()` returns the application's `UserDetails` implementation (e.g., `UserAccount`), which carries the UUID. Define a reusable helper so the same extraction logic is applied consistently everywhere:

```java
// Resolve UUID from an authenticated principal; returns null if not available
private UUID resolveUserId(Authentication auth) {
    if (auth != null && auth.getPrincipal() instanceof UserAccount user) {
        return user.getId();
    }
    return null;
}
```

For pre-authentication events (e.g., authentication failures where the account may not exist), `user.id` is not available. Omit it entirely and rely on `trace.id` for correlation — do not fall back to logging a hashed username, as that would confirm account existence to anyone who can read logs.

## 4. Log authentication events

Spring Security publishes application events for authentication outcomes. Register a single event listener to centralise all authentication logging rather than scattering log statements across individual providers or filters.

Log authentication success at `INFO` with `user.id`, authentication method, and client IP from `WebAuthenticationDetails`. Log authentication failure at `WARN` and account lockout at `ERROR`, since lockout indicates an active attack pattern. Always include `source.ip` on every event as it is required for audit and incident investigation. Never log the submitted credential or distinguish account-not-found from bad-credential, as doing so enables user enumeration attacks. For failures, omit `user.id` — the account may not exist, and logging it would confirm account existence.

```java
# File: src/main/java/com/example/security/AuthenticationEventLogger.java
package com.example.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthenticationEventLogger {

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        log.atInfo()
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", List.of("process"))
            .addKeyValue("event.type", List.of("user"))
            .addKeyValue("event.action", "user-authentication")
            .addKeyValue("event.outcome", "success")
            .addKeyValue("event.severity", "low")
            .addKeyValue("auth.method", resolveAuthMethod(auth))
            .addKeyValue("user.id", resolveUserId(auth))
            .addKeyValue("source.ip", resolveClientIp(auth))
            .log("Authentication succeeded.");
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        Authentication auth = event.getAuthentication();
        if (event instanceof AuthenticationFailureLockedEvent) {
            log.atError()
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("process"))
                .addKeyValue("event.type", List.of("error"))
                .addKeyValue("event.action", "user-authentication")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("event.severity", "high")
                .addKeyValue("error_code", 423)
                .addKeyValue("error_category", "cert/auth")
                .addKeyValue("error_follow_up_action", true)
                .addKeyValue("source.ip", resolveClientIp(auth))
                .log("Account locked.");
        } else {
            log.atWarn()
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("process"))
                .addKeyValue("event.type", List.of("user"))
                .addKeyValue("event.action", "user-authentication")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("event.severity", "medium")
                .addKeyValue("error_code", 401)
                .addKeyValue("error_category", "cert/auth")
                .addKeyValue("error_follow_up_action", false)
                .addKeyValue("source.ip", resolveClientIp(auth))
                .log("Authentication failed.");
        }
    }

    private UUID resolveUserId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof UserAccount user) {
            return user.getId();
        }
        return null;
    }

    private String resolveAuthMethod(Authentication auth) {
        return switch (auth.getClass().getSimpleName()) {
            case "UsernamePasswordAuthenticationToken" -> "password";
            case "JwtAuthenticationToken"              -> "jwt";
            case "OAuth2AuthenticationToken"           -> "oauth2";
            case "RememberMeAuthenticationToken"       -> "remember-me";
            default -> "unknown";
        };
    }

    private String resolveClientIp(Authentication auth) {
        if (auth.getDetails() instanceof WebAuthenticationDetails details) {
            return details.getRemoteAddress();
        }
        return "unavailable";
    }
}
```

<note>

> **Note:** `AbstractAuthenticationFailureEvent` is the parent of all Spring Security authentication failure events. A single listener on the parent catches all failure subtypes. Use `instanceof` to differentiate lockout within the same listener rather than registering a separate `AuthenticationFailureLockedEvent` listener, which would produce duplicate log entries. For applications using JWT or OAuth2 tokens, log token validation failures at the point of validation. Never log the token value itself.

</note>

## 5. Log logout events

Log the logout event so the end of the session is traceable. Implement a `LogoutHandler` and register it in the security config. This is the recommended approach as it runs during the logout process with direct access to the request, requiring no additional event publisher registration.

```java
# File: src/main/java/com/example/security/LogoutAuditHandler.java
package com.example.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogoutAuditHandler implements LogoutHandler {

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication != null) {
            UUID userId = authentication.getPrincipal() instanceof UserAccount u ? u.getId() : null;
            log.atInfo()
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("process"))
                .addKeyValue("event.type", List.of("end"))
                .addKeyValue("event.action", "user-logout")
                .addKeyValue("event.outcome", "success")
                .addKeyValue("event.severity", "low")
                .addKeyValue("user.id", userId)
                .addKeyValue("source.ip", request.getRemoteAddr())
                .log("User logged out.");
        }
    }
}
```

Register it in `SecurityConfig`:

```java
http.logout(logout -> logout.addLogoutHandler(logoutAuditHandler));
```

## 6. Log session lifecycle events

A session represents an active authenticated state. Logging both session creation and destruction provides a complete audit trail of authenticated sessions, including who created them, when they ended, and whether they ended through explicit logout, timeout, or server-side invalidation.

Register `HttpSessionEventPublisher` as a bean so Spring Security publishes session lifecycle events:

```java
# File: src/main/java/com/example/security/SecurityConfig.java (addition)
@Bean
public HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
}
```

### 6.1 Log session creation

Listen for `HttpSessionCreatedEvent` to record when a new session is created:

```java
# File: src/main/java/com/example/security/SessionEventLogger.java
package com.example.security;

import jakarta.servlet.http.HttpSessionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.web.session.HttpSessionCreatedEvent;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SessionEventLogger {

    @EventListener
    public void onSessionCreated(HttpSessionCreatedEvent event) {
        HttpSessionEvent sessionEvent = (HttpSessionEvent) event.getSource();
        log.atInfo()
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", List.of("process"))
            .addKeyValue("event.type", List.of("start"))
            .addKeyValue("event.action", "session-start")
            .addKeyValue("event.outcome", "success")
            .addKeyValue("event.severity", "low")
            .addKeyValue("session.max_inactive_interval", sessionEvent.getSession().getMaxInactiveInterval())
            .log("Session created.");
    }
}
```

<note>

> **Note:** `HttpSessionCreatedEvent` fires when the HTTP session is created, not when the user authenticates. In stateful applications, the session is typically created before authentication. The session creation log does not include `user.id` because authentication has not yet occurred. Use authentication success logs (section 4) to correlate session activity with specific users.

</note>

### 6.2 Log session destruction

Add the session destruction handler to the same `SessionEventLogger` class:

```java
# File: src/main/java/com/example/security/SessionEventLogger.java (continued)

    @EventListener
    public void onSessionDestroyed(HttpSessionDestroyedEvent event) {
        event.getSecurityContexts().forEach(ctx -> {
            Authentication auth = ctx.getAuthentication();
            if (auth != null) {
                UUID userId = auth.getPrincipal() instanceof UserAccount u ? u.getId() : null;
                log.atInfo()
                    .addKeyValue("event.kind", "event")
                    .addKeyValue("event.category", List.of("process"))
                    .addKeyValue("event.type", List.of("end"))
                    .addKeyValue("event.action", "session-end")
                    .addKeyValue("event.outcome", "success")
                    .addKeyValue("event.severity", "low")
                    .addKeyValue("user.id", userId)
                    .log("Session expired or invalidated.");
            }
        });
    }
}
```

<note>

> **Note:** `HttpSessionDestroyedEvent` fires for all session destructions, including after explicit logout. When both `LogoutAuditHandler` and `SessionEventLogger` are active, an explicit logout produces two log entries: one for the user action and one for the session termination. This is expected behaviour.

</note>

## 7. Log MFA enrolment and removal

MFA enrolment and removal are security-relevant account changes that must be audited. Spring Security does not publish MFA events, so log them directly in the service method at the point where the operation is confirmed or fails.

The factor type is encoded in `event.action` using the pattern `{factor}-enrol` and `{factor}-remove`. Allowed values: `totp-enrol`, `totp-remove`, `sms-otp-enrol`, `sms-otp-remove`, `hardware-token-enrol`, `hardware-token-remove`, `backup-code-enrol`, `backup-code-remove`. Never log OTP codes, TOTP secrets, backup codes, or recovery phrases.

```java
# File: src/main/java/com/example/security/MfaService.java
package com.example.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MfaService {

    public void enrolMfaFactor(UUID userId, String factorType) {
        // factorType: "totp", "sms-otp", "hardware-token", "backup-code"
        try {
            // ... enrolment logic

            log.atInfo()
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("process"))
                .addKeyValue("event.type", List.of("change"))
                .addKeyValue("event.action", factorType + "-enrol")
                .addKeyValue("event.outcome", "success")
                .addKeyValue("event.severity", "low")
                .addKeyValue("user.id", userId)
                .log("MFA factor enrolled.");
        } catch (Exception e) {
            log.atWarn()
                .setCause(e)
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("process"))
                .addKeyValue("event.type", List.of("change"))
                .addKeyValue("event.action", factorType + "-enrol")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("event.severity", "medium")
                .addKeyValue("error_code", 500)
                .addKeyValue("error_category", "application")
                .addKeyValue("error_follow_up_action", true)
                .addKeyValue("user.id", userId)
                .log("MFA factor enrolment failed.");
        }
    }

    public void removeMfaFactor(UUID userId, String factorType) {
        // ... removal logic

        log.atInfo()
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", List.of("process"))
            .addKeyValue("event.type", List.of("change"))
            .addKeyValue("event.action", factorType + "-remove")
            .addKeyValue("event.outcome", "success")
            .addKeyValue("event.severity", "low")
            .addKeyValue("user.id", userId)
            .log("MFA factor removed.");
    }
}
```

## 8. Log authorisation events

### 8.1 Authorisation success (for sensitive operations)

Do not log every authorisation check. Log authorisation success only for operations that access or modify sensitive data, cross a privilege boundary, or are required for audit such as admin actions, data exports, and permission changes.

Use `@PreAuthorize` to enforce access control. Log the audit entry inside the method body, after Spring Security has confirmed access, to record who performed the operation and with what outcome.

```java
# File: src/main/java/com/example/admin/AdminService.java
package com.example.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
@Slf4j
@Service
public class AdminService {

    @PreAuthorize("hasRole('ADMIN')")
    public void exportUserData(String exportId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = auth != null && auth.getPrincipal() instanceof UserAccount u ? u.getId() : null;

        log.atInfo()
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", List.of("process"))
            .addKeyValue("event.type", List.of("allowed"))
            .addKeyValue("event.action", "data-export")
            .addKeyValue("event.outcome", "success")
            .addKeyValue("event.severity", "low")
            .addKeyValue("user.id", userId)
            .addKeyValue("export.id", exportId)
            .log("User data export started.");

        // ... export logic
    }
}
```

### 8.2 Authorisation failure

Log authorisation failures at `WARN` level with `user.id`. Never expose internal permission logic, role names, or resource paths that reveal infrastructure details.

To capture denials centrally, Spring Security publishes `AuthorizationDeniedEvent` for every access decision that fails. Register `SpringAuthorizationEventPublisher` in `SecurityConfig` alongside the `HttpSessionEventPublisher` introduced in section 6:

```java
# File: src/main/java/com/example/security/SecurityConfig.java
package com.example.security;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SpringAuthorizationEventPublisher authorizationEventPublisher(
            ApplicationEventPublisher applicationEventPublisher) {
        return new SpringAuthorizationEventPublisher(applicationEventPublisher);
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
```

<note>

> **Note:** `AuthorizationDeniedEvent` covers both web request and method-level denials (`@PreAuthorize`). Without `SpringAuthorizationEventPublisher` registered, access denials are not published and cannot be centrally logged.

</note>

Listen for the denial event:

```java
# File: src/main/java/com/example/security/AuthorisationEventLogger.java
package com.example.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthorisationEventLogger {

    @EventListener
    public void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {
        Authentication authentication = event.getAuthentication().get();
        UUID userId = authentication != null && authentication.getPrincipal() instanceof UserAccount u
            ? u.getId() : null;
        log.atWarn()
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", List.of("process"))
            .addKeyValue("event.type", List.of("denied"))
            .addKeyValue("event.action", "access-control")
            .addKeyValue("event.outcome", "failure")
            .addKeyValue("event.severity", "medium")
            .addKeyValue("error_code", 403)
            .addKeyValue("error_category", "cert/auth")
            .addKeyValue("error_follow_up_action", false)
            .addKeyValue("user.id", userId)
            .log("Authorisation denied.");
    }
}
```

## 9. Add user context to MDC

Register an `MdcUserFilter` after Spring Security's authentication filters to write `user.id` into MDC. Once set, the UUID appears automatically on all log entries within the request without needing to be added to each log call individually. See [Enriching Logs with MDC](Enriching_Logs_With_MDC.md), section 3.4 for the filter implementation and registration steps.

## 10. Verification

Trigger each scenario below and verify the corresponding log entry.

**Authentication**
- Authentication success: log includes `event.outcome: success`, `auth.method`, `user.id`, and `source.ip`. No raw username or credential appears
- Authentication failure: log includes `event.outcome: failure`, `error_category: cert/auth`, and `source.ip`. No `user.id` (account may not exist — omitting avoids confirming account existence). The failure reason does not distinguish account-not-found from bad-credential
- Account lockout: log is at `ERROR` level with the same fields as authentication failure

**Logout and session**
- Logout: log includes `event.outcome: success`, `user.id`, and `source.ip`
- Session creation: log includes `event.action: session-start` and `session.max_inactive_interval`. No `user.id` (session created before authentication)
- Session expiry: log includes `event.action: session-end` and `user.id`

**MFA**
- MFA enrolment success: log has `event.action` in the form `{factor}-enrol` (e.g. `totp-enrol`) and includes `user.id`. No TOTP secret, OTP code, or backup code appears
- MFA enrolment failure: log is at `WARN` level with the same `event.action` and `event.outcome: failure`
- MFA removal: log has `event.action` in the form `{factor}-remove` and includes `user.id`

**Authorisation**
- Sensitive operation: log includes `event.outcome: success`, `event.action`, and `user.id`
- Access denial: log includes `event.outcome: failure`, `error_category: cert/auth`, and `user.id`. No resource path or role name appears

**MDC**
- All log entries within an authenticated request carry `user.id` automatically

## 11. Conclusion
With Spring Security event listeners, UUID-based user identity, client IP capture, and MDC propagation in place, authentication and authorisation events are captured consistently without exposing sensitive identity data.

### Key Takeaways
- **Use `user.id` (UUID) for user identity**: Never log raw usernames, emails, or credentials. UUIDs are non-PII and directly debuggable — look up the user in the database by UUID when investigating
- **Omit `user.id` on failure events**: For authentication failures the account may not exist; logging a UUID would confirm account existence. Use `trace.id` for correlation instead
- **Include `source.ip` on authentication and logout events**: Use `WebAuthenticationDetails.getRemoteAddress()` for authentication events and `request.getRemoteAddr()` for logout. Both are required for audit and incident investigation
- **Use a generic failure message**: Authentication failures must not distinguish account-not-found from bad-credential to prevent user enumeration
- **Log account lockout at `ERROR`**: Lockout indicates an active attack pattern and warrants higher severity than a regular authentication failure
- **Centralise auth logging with event listeners**: `@EventListener` on Spring Security events logs all authentication outcomes in one place without modifying individual providers or filters
- **Cover both explicit logout and session expiry**: `LogoutAuditHandler` captures user-initiated logout. `HttpSessionDestroyedEvent` via `HttpSessionEventPublisher` captures expiry and server-side invalidation
- **Log MFA enrolment and removal at the point of confirmation**: Encode the factor type in `event.action` using the pattern `{factor}-enrol` and `{factor}-remove`. Log enrolment failures at `WARN`. Never log OTP codes, TOTP secrets, backup codes, or recovery phrases
- **Log authorisation success selectively**: Only log for sensitive operations, privileged actions, and data exports, not for every access check
- **Enable `SpringAuthorizationEventPublisher` for denial events**: Without this publisher, `AuthorizationDeniedEvent` is not published and access denials are not centrally logged
- **Add `user.id` to MDC via `MdcUserFilter`**: Once set after authentication, it appears automatically on all subsequent log entries within that request

## 12. References

Related guides:
- [Structured Logging, Trace Correlation, and Context Propagation](Structured_Logging_Trace_Correlation_And_Context_Propagation.md)
- [Enriching Logs with MDC](Enriching_Logs_With_MDC.md)
- [Sensitive Data Masking for Logs](Sensitive_Data_Masking_For_Logs.md)

Spring Security:
- [Spring Security Reference: Authentication Events](https://docs.spring.io/spring-security/reference/servlet/authentication/events.html)
- [Spring Security Reference: Logout](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html)
- [Spring Security Reference: Session Management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)
- [Spring Security Reference: Authorization Events](https://docs.spring.io/spring-security/reference/servlet/authorization/events.html)

Further reading:
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
- [OWASP ASVS v5.0 V16 — Security Logging and Error Handling](https://owasp.org/www-project-application-security-verification-standard/)
