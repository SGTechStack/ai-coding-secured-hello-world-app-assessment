# Standalone: Self-Service Password and History Management

## 1. Introduction

This guide defines the standard for user-driven password updates and credential history validation in standalone mode. In standalone mode, where user credentials are managed directly inside the application rather than delegated to an external Identity Provider, users must be able to change their passwords securely. The application must enforce password history checks to prevent users from reusing recent passwords.

**Key Design Principles:**
- **Controlled Password Rotation**: Prevents users from rotating between a small set of passwords by checking new password hashes against a history of prior hashes.
- **Zero-Trust Access Redirection**: Automatically redirects or blocks users from performing any business operations while flagged with `requirePasswordChange=true`.
- **Hashed History Storage**: Never stores history in plaintext; all entries in the password history list are hashed using the default password encoder (**BCrypt**).
- **Graceful Error Feedback**: Rejects invalid updates with structured HTTP responses containing user-friendly errors, without leaking system stack traces.

## 2. Prerequisites

- Spring Boot 4.x with Spring Security 7.x
- **Relational Database**: To persist user records and password history.
- **Password Encoder**: Default configured to **BCrypt**.

## 3. Implementation

### Step 1: Configure Password History Properties

Define the maximum password history length in your application properties to enforce security controls on credential reuse.

```yaml
# application.yml
spring:
  password:
    sso:
      max-password-history-length: 3 # Tracks and rejects reuse of the last 3 passwords
```

### Step 2: Implement Self-Service Password Change Controller Endpoint

Expose a custom controller endpoint `/currentUser/changePassword` to accept self-service password modifications.

```java
package com.example.security.controller;

import com.example.security.command.ChangeCurrentUserPasswordCommand;
import com.example.security.dto.ChangePasswordRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/currentUser")
public class CurrentUserController {

    private final CommandExecutor commandExecutor;

    public CurrentUserController(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    @PatchMapping(path = "/changePassword")
    @PreAuthorize("hasRole('CURRENT_USER_UPDATE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody ChangePasswordRequest changePasswordRequest) {
        commandExecutor.execute(new ChangeCurrentUserPasswordCommand(changePasswordRequest.getNewPassword()));
    }
}
```

### Step 3: Implement Password History Checking and Rotation

Implement the password validation logic within the update command, ensuring the new credentials are not reused and are properly rotated inside the credential history list.

```java
package com.example.security.command;

import com.example.user.UserAccount;
import com.example.user.PasswordHistoryEntry;
import com.example.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;
import java.util.List;

public class ChangeCurrentUserPasswordCommand {

    private final String newPassword;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${spring.password.sso.max-password-history-length:3}")
    private int maxHistoryLength;

    public ChangeCurrentUserPasswordCommand(String newPassword) {
        this.newPassword = newPassword;
    }

    public void execute() {
        // 1. Resolve current user from authenticated security context
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        UserAccount user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User not found"));

        // 2. Validate password against history
        List<PasswordHistoryEntry> history = user.getPasswordHistory();
        for (PasswordHistoryEntry oldPass : history) {
            if (passwordEncoder.matches(newPassword, oldPass.getPasswordHash())) {
                throw new PasswordHistoryException("Cannot reuse any of the last " + maxHistoryLength + " passwords");
            }
        }

        // 3. Encrypt and apply new password
        String hashedNewPassword = passwordEncoder.encode(newPassword);
        user.setPasswordHash(hashedNewPassword);
        user.setPasswordResetAt(Instant.now());
        user.setRequirePasswordChange(false);

        // 4. Update and rotate history
        PasswordHistoryEntry entry = new PasswordHistoryEntry(hashedNewPassword, Instant.now());
        history.add(0, entry);
        if (history.size() > maxHistoryLength) {
            history.remove(history.size() - 1);
        }
        user.setPasswordHistory(history);

        userRepository.save(user);
    }
}
```

### Step 4: Password Change Enforcement Filter

If a user account is configured with `requirePasswordChange=true` (e.g. following admin bootstrap), intercept incoming requests and restrict endpoints until the password change is completed.

```java
package com.example.security.filter;

import com.example.security.model.CustomUserDetails;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class PasswordChangeFilter extends GenericFilter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) 
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CustomUserDetails user) {
            if (user.isRequirePasswordChange()) {
                String path = request.getRequestURI();
                String changePasswordUrl = request.getContextPath() + "/currentUser/changePassword";
                String logoutUrl = request.getContextPath() + "/logout";

                // Block access to all routes except the self-service change endpoint and logout
                if (!path.equals(changePasswordUrl) && !path.equals(logoutUrl)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, 
                        "Password must be changed on first login");
                    return;
                }
            }
        }
        chain.doFilter(req, res);
    }
}
```

---

## 4. Examples

### Update Password (Success)

**Request:**
```http
PATCH /api/v1/currentUser/changePassword HTTP/1.1
Host: app.example.com
Content-Type: application/json
X-CSRF-TOKEN: [Session-Bound-Token]

{
  "newPassword": "SecureNewPassword123!"
}
```

**Response (204 No Content):**
```http
HTTP/1.1 204 No Content
```

---

### Update Password (Fails History Validation)

**Request:**
```http
PATCH /api/v1/currentUser/changePassword HTTP/1.1
Host: app.example.com
Content-Type: application/json
X-CSRF-TOKEN: [Session-Bound-Token]

{
  "newPassword": "MyOldPasswordUsedYesterday"
}
```

**Response (400 Bad Request):**
```json
{
  "error": "Cannot reuse any of the last 3 passwords"
}
```

---

## 5. Security Best Practices & Hardening (OWASP & NIST SP 800-63B Alignment)

For production deployments, the self-service password modification flow should be hardened using the following industry best practices:

### 1. Require Re-authentication Before Password Change
To prevent session hijacking exploits (where an attacker with a hijacked session attempts to take over the account), always require the user to provide their **current password** in the request body. Validate it using the password encoder before allowing the change:

```java
// Ensure your DTO supports capturing the current password
public class ChangePasswordRequest {
    private String currentPassword;
    private String newPassword;
}

// Inside your password change service/command validation:
if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
    throw new BadCredentialsException("Invalid current password");
}
```

### 2. Enforce Session Revocation Upon Password Update
As recommended by the **OWASP Session Management Cheat Sheet**, changing user credentials must immediately invalidate all other active sessions for that user. In a Spring Session environment:

```java
// Inject the session repository
private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

public void revokeOtherSessions(String username) {
    // Find and terminate all active sessions for the user
    sessionRepository.findByPrincipalName(username).keySet()
        .forEach(sessionRepository::deleteById);
}
```

### 3. Use Argon2id for Adaptive Password Hashing
While BCrypt is widely supported, **Argon2id** is the current recommendation of both OWASP and NIST SP 800-63B for storing user credentials, due to its memory-hard construction that resists GPU/ASIC brute-force attacks.

### 4. Log Sanitization
Plaintext passwords must **never** be logged or included in system error traces. Always catch exceptions at the controller level and translate them into generic client-side messages (e.g., `400 Bad Request` with structured error details), while logging only metadata (e.g., `user.id` UUID, event outcome).

---

## 6. Verification

1. **Verify Password History Rotation**: 
   - Set the password to `TempPassword1!`.
   - Perform 3 subsequent password updates to new values.
   - Verify that resetting the password to `TempPassword1!` is accepted, but attempting to set it to any of the 3 active history values fails.
2. **Verify Mandatory Change Enforcement**: Flag a user with `requirePasswordChange=true` in the database, authenticate as that user, and verify that accessing any URL other than `/currentUser/changePassword` or `/logout` returns `403 Forbidden`.

## 7. References

- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [Spring Security: Default Security Filters](https://docs.spring.io/spring-security/reference/servlet/architecture.html#servlet-filters-review)
- [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- [NIST SP 800-63B: Digital Identity Guidelines](https://pages.nist.gov/800-63-3/sp800-63b.html)

