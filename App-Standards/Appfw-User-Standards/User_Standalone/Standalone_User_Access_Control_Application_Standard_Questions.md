# App Standard: Standalone User Access Control — Implementation Questions

Answer these questions with your team or product owner before implementation to determine which standalone (username/password) authentication and authorization requirements apply to your application.

---

## 1. Application Context

### Q1: What is the application deployment context and topology?

**Deployment context - select the context that best describes your application:**

- [ ] **Internal enterprise application** - Used by employees/staff within the organization, typically behind corporate network or VPN
- [ ] **Internal B2B application** - Used by partner organizations or contractors with manually-provisioned accounts
- [ ] **Legacy system replacement** - Replacing existing standalone authentication system

> **Why this matters:** 
> - **Internal applications** can use stricter account lockout policies (3-5 failed attempts), longer session timeouts (30 min idle), and simpler password reset flows (admin-generated tokens)
> - **B2B partner applications** need more lenient lockout policies (7-10 attempts) to avoid locking out legitimate external users who can't easily contact support
> - **Legacy replacements** need migration strategy for existing user accounts and password hashes

**Deployment topology:**

- [ ] **Single instance**
- [ ] **Multiple instances behind load balancer**
- [ ] **Auto-scaling group**

> Multi-instance deployments require Spring Session JDBC/Redis and ShedLock for batch jobs.

---

## 2. Migration and Initial Setup

### Q2: What is the user migration strategy for this application?

**Select your migration approach:**

- [ ] **Greenfield** - New application with no existing users (skip migration details below)

- [ ] **Migration: Force password reset + active pending change** - Users must set new password on first login (recommended)
  - Conflict resolution: [ ] Manual review [ ] Auto-suffix [ ] Reject duplicates
  - Timing: [ ] One-time bulk [ ] Gradual (duration: _________)

- [ ] **Migration: Hash migration + active immediately** - Migrate compatible password hashes (Argon2id/scrypt/BCrypt ≥12 only)
  - Also migrate password history: [ ] Yes [ ] No (start fresh)
  - Conflict resolution: [ ] Manual review [ ] Auto-suffix [ ] Reject duplicates
  - Timing: [ ] One-time bulk [ ] Gradual (duration: _________)

- [ ] **Migration: No passwords + disabled pending admin** - Accounts created without passwords, admin must reset
  - Conflict resolution: [ ] Manual review [ ] Auto-suffix [ ] Reject duplicates
  - Timing: [ ] One-time bulk [ ] Gradual (duration: _________)

- [ ] **Import from directory: Disabled pending activation** - Pre-populate accounts from HR/LDAP, users activate via email
  - Conflict resolution: [ ] Manual review [ ] Auto-suffix [ ] Reject duplicates
  - Timing: [ ] One-time bulk [ ] Gradual (duration: _________)

> **Why this matters:** Gradual migration requires account synchronization logic and dual authentication support. Hash migration only viable if old system uses strong algorithms. Password history migration only applicable with hash migration.

---

### Q3: What is the initial administrator account bootstrap strategy?

**Select your approach:**

- [ ] **Greenfield: New admin via Liquibase** - Create fresh admin account
  - Username: _________
  - Email: _________
  - Initial password: Randomly generated, secrets manager, mandatory change on first login
  - Implementation: Liquibase changeset (production), profile-gated seed (dev/local only)

- [ ] **Migration: Migrate existing admins only** - Preserve legacy admin privileges
  - Admin role verification: [ ] Map from legacy data [ ] Manual verification [ ] Pre-configured mapping table (recommended)

- [ ] **Migration: Create new admin only** - Legacy admins become regular users, fresh admin via Liquibase
  - Username: _________
  - Email: _________

- [ ] **Migration: Both** - Migrate existing admins + create emergency admin (recommended)
  - Admin role verification: [ ] Map from legacy data [ ] Manual verification [ ] Pre-configured mapping table (recommended)
  - Emergency admin username: _________
  - Emergency admin email: _________

> **Recommendation:** For migrations, preserve at least one known admin account plus create emergency admin via Liquibase as fallback.

---

## 3. User Identity and Account Structure

### Q4: What credentials do users provide during login?

**Select user account structure:**

- [ ] **Username-based** - Username is mandatory, email optional
  - Users login with: Username only
  - All users must have unique username
  
- [ ] **Email-based** - Email is mandatory, username optional  
  - Users login with: Email only
  - All users must have unique email
  
- [ ] **Flexible (username + email)** - Both username and email are mandatory (recommended)
  - Users login with: Either username or email (single login field accepts both)
  - All users must have unique username AND unique email
  
- [ ] **Employee number-based** - Employee number is mandatory
  - Users login with: Employee number only
  - All users must have unique employee number

> **Recommendation:** Flexible (username + email) provides best UX and supports both password reset workflows.

> **Why this matters:** Determines login form field label, `UserDetailsService` lookup logic, and required database fields/constraints.

**If username is used, specify constraints:**

- Allowed characters:
  - [ ] Alphanumeric only
  - [ ] Alphanumeric + underscore/hyphen (recommended)
  - [ ] Other: _________
- Min/max length: _________ / _________
- [ ] Case-insensitive lookup (recommended)

---

### Q5: What additional user account information is required?

> **Note:** Email/username requirements already determined in Q4. This question covers additional fields.

**For audit and compliance, which additional fields are mandatory?**

Common fields (select all that apply):
- [ ] Full name / Display name
- [ ] Department / Organizational unit
- [ ] Employee ID / Staff number
- [ ] Manager / Supervisor
- [ ] **Phone number** (must be mandatory if SMS-based password reset selected in Q14)
- [ ] Job title
- [ ] Location / Office
- [ ] Other: _________
- [ ] None - No additional mandatory fields

**Optional fields (collected but not required for account creation):**

List any fields to collect but not enforce: _________

> **Why this matters:** Mandatory fields block account creation if missing. Balance security/audit needs against workflow friction.

---

## 4. Infrastructure and Database

### Q6: What relational database will be used?

- [ ] **PostgreSQL** (recommended)
- [ ] **MySQL/MariaDB**
- [ ] **Oracle**
- [ ] **SQL Server**
- [ ] **H2** (dev/test only)
- [ ] **Other**: _________

> **Recommendation:** PostgreSQL for production.

> **Why this matters:** Affects Spring Session schema and ShedLock configuration.

---

### Q7: What session persistence backend and datasource configuration will be used?

> Multi-instance deployments require Spring Session JDBC or Redis. When using Spring Session JDBC, use `SpringSessionBackedSessionRegistry` instead of `SessionRegistryImpl` for cross-instance session invalidation.

**Select your configuration:**

- [ ] **Spring Session JDBC with shared datasource** (recommended - reuse existing database)
- [ ] **Spring Session JDBC with separate datasource** (dedicated session database for very high write loads)
- [ ] **Spring Session Redis** (higher performance, separate infrastructure)
- [ ] **In-memory** (single instance only, not for production multi-node)

---

### Q8: What batch job strategy is needed for account hygiene?

> **Why this matters:** Batch jobs enforce password hygiene and account lifecycle policies automatically.

**Select your batch job configuration:**

- [ ] **No batch jobs** - No scheduled hygiene jobs needed

- [ ] **Batch jobs with JDBC locking, daily at off-peak** - Time: _________ (e.g., 2:00 AM), Timezone: [ ] UTC [ ] HQ timezone [ ] Local
  - Jobs: [ ] Inactivity disablement [ ] Role revocation

- [ ] **Batch jobs with JDBC locking, multiple daily** - Frequency: _________ (e.g., every 6 hours), Timezone: [ ] UTC [ ] HQ timezone [ ] Local
  - Jobs: [ ] Inactivity disablement [ ] Role revocation

- [ ] **Batch jobs with JDBC locking, weekly** - Day and time: _________, Timezone: [ ] UTC [ ] HQ timezone [ ] Local
  - Jobs: [ ] Inactivity disablement [ ] Role revocation

- [ ] **Batch jobs with Redis locking, daily at off-peak** - Time: _________ (e.g., 2:00 AM), Timezone: [ ] UTC [ ] HQ timezone [ ] Local
  - Jobs: [ ] Inactivity disablement [ ] Role revocation

- [ ] **Batch jobs with Redis locking, multiple daily** - Frequency: _________ (e.g., every 6 hours), Timezone: [ ] UTC [ ] HQ timezone [ ] Local
  - Jobs: [ ] Inactivity disablement [ ] Role revocation

- [ ] **Batch jobs with Redis locking, weekly** - Day and time: _________, Timezone: [ ] UTC [ ] HQ timezone [ ] Local
  - Jobs: [ ] Inactivity disablement [ ] Role revocation

> **Recommendation:** UTC timezone for consistency. JDBC locking if using Spring Session JDBC, Redis if using Spring Session Redis.

---

## 5. Authentication Configuration

### Q9: What are the session timeout policies?

**Idle timeout:**
- [ ] **15 minutes** (recommended)
- [ ] **30 minutes** (context-switching workflows)
- [ ] **10 minutes** (financial/sensitive data)
- [ ] **Custom**: _________

**Absolute timeout:**
- [ ] **8 hours** (standard business hours)
- [ ] **12 hours** (24/7 operations)
- [ ] **Custom**: _________

**Concurrent sessions per user:**
- [ ] **1** (high-security, recommended)
- [ ] **2-3** (multi-device users)

> On exceeding limit, invalidate oldest session.

**Session expiry redirect:**
- [ ] Default login page
- [ ] Custom page with expiry message

> **Recommendation:** Internal apps with frequent context-switching may need 30-min idle timeout. Financial apps should use 10-15 min.

---

### Q10: What "Remember Me" functionality should be supported?

**What is "Remember Me"?**
- Keeps users logged in across browser sessions (close browser, reopen days later, still logged in)
- Without it: Users must login again after closing browser
- With it: Long-lived token (14-30 days) creates new session automatically when browser reopens

> **Security tradeoff:** Convenience (users stay logged in for weeks) vs risk (if device compromised, attacker has access for weeks instead of hours).

**Standard Requirements (if enabled):**
- Remember-me tokens stored in database with expiry
- Token invalidated on password change or explicit logout
- Separate cookie from session cookie

**Select your configuration:**

- [ ] **No Remember Me** (recommended for high-security applications)

- [ ] **Remember Me enabled** - Token duration: _________ days (recommend: 14-30 days)
  - [ ] Require re-authentication for sensitive operations (password change, profile update, role changes)

---

### Q11: What are the login and logout redirect behaviors?

**After successful login, where should users be redirected?**

- [ ] **Application home page or dashboard** (safest - prevents open redirect attacks)
- [ ] **Last-accessed page before session expiry** (better UX but requires URL validation)
- [ ] **Role-specific landing page** (recommended for role-based workflows)

> **Recommendation:** Application home page is safest. Role-specific landing page provides good UX for role-based workflows.

**After logout, where should users be redirected?**

- [ ] **Public landing page** (recommended - clear visual confirmation of logout)
- [ ] **Login page with logout confirmation** (good UX, confirms action completed)
- [ ] **Custom logout success page** (if branding/messaging required)

> **Note:** A logout invoked on an already-expired session returns 401/403 (logout keeps its session-bound CSRF token); the SPA handles this gracefully by redirecting to login.

---

## 6. Password Policy

### Q12: What are the minimum password strength requirements?

**Standard Requirement (§3.5):**
- Passwords must be stored using a slow, adaptive hash algorithm:
  - **Argon2id** (recommended - best security)
  - **scrypt** (acceptable alternative)
  - **BCrypt with cost factor ≥12** (acceptable for existing systems)

> This is not a decision point - adaptive password hashing is mandatory for security.

**Password strength policy:**

- Minimum length: _________ characters (recommend: 12; 15+ for high-security)
- Maximum length: _________ characters (recommend: 128 to prevent DoS)
- Character complexity requirements:
  - [ ] Uppercase letter required
  - [ ] Lowercase letter required
  - [ ] Digit required
  - [ ] Special character required
  - [ ] No mandatory character types (NIST recommendation)

> **Recommendation:** NIST SP 800-63B: minimum 12 characters, 15+ for high-security. Verifiers should NOT impose character complexity composition rules (e.g. mandatory mix of uppercase/lowercase/numbers/specials) as they lead to predictable patterns and reduce password entropy in practice.

**Allowed special characters:**

- [ ] **All printable ASCII characters** (recommended for passphrases)
- [ ] **Restricted set**: `!@#$%^&*()-_=+[]{}|;:,.<>?`
- [ ] **Unicode characters allowed**

**Banned passwords:**

- [ ] Check against common passwords list (recommended)
- [ ] Check against breach databases (e.g., Have I Been Pwned)
- [ ] Block organization name and variants
- [ ] No banned password checking

---

### Q13: What password history and rotation policy is needed?

**Standard defaults (§3.5):**
- Password history: Retain 3 previous passwords to prevent reuse

**Select your policy:**

- [ ] **Use standard defaults** (3 history)

- [ ] **Custom policy** - Specify configuration:
  - Password history count: _________ (recommend: 3-5, or 5-10 for highly regulated)

---

### Q14: What password reset workflow and token delivery strategy is needed?

> **Why this matters:** Determines whether `/password-reset/request` and `/password-reset/confirm` endpoints are implemented, and whether email/SMS infrastructure integration is required.

**Standard Requirements:**
- Tokens expire after 30 minutes and are single-use
- For admin-initiated reset, administrator receives plaintext token and communicates through trusted channel

**Select your password reset configuration:**

- [ ] **Admin-generated tokens only, display once in UI** (most secure - admin copies immediately, cannot retrieve later)
  - Token format: [ ] 32-char alphanumeric (recommended) [ ] 64-char hex [ ] 128-bit UUID [ ] Custom: _________

- [ ] **Admin-generated tokens only, retrievable in admin panel** (convenient but risk if admin session compromised)
  - Token format: [ ] 32-char alphanumeric (recommended) [ ] 64-char hex [ ] 128-bit UUID [ ] Custom: _________

- [ ] **Admin-generated tokens only, application auto-sends** (application emails/SMS directly to user)
  - Token format: [ ] 32-char alphanumeric (recommended) [ ] 64-char hex [ ] 128-bit UUID [ ] Custom: _________

- [ ] **Self-service email-based** (requires SMTP integration)
  - Token format: [ ] 32-char alphanumeric (recommended) [ ] 64-char hex [ ] 128-bit UUID [ ] Custom: _________

- [ ] **Self-service SMS-based** (requires SMS gateway integration)
  - Token format: [ ] 32-char alphanumeric (recommended) [ ] 64-char hex [ ] 128-bit UUID [ ] Custom: _________

- [ ] **Both admin-generated and self-service** (recommended for flexibility)
  - Admin delivery: [ ] Display once [ ] Retrievable in panel [ ] Auto-send
  - Self-service: [ ] Email-based [ ] SMS-based [ ] Both
  - Token format: [ ] 32-char alphanumeric (recommended) [ ] 64-char hex [ ] 128-bit UUID [ ] Custom: _________

---

## 7. Account Security and Lockout

### Q15: What account lockout policy and admin unlock capability is needed?

**Standard defaults (§3.5, Decision Logic):**
- Account lockout: 5 consecutive failed login attempts
- Lockout duration: 20 minutes (automatic lift)
- Failed-login counter: Resets only on successful login
- Per-account rate limiting: 10 login attempts per account per minute
- Password reset endpoints: Must also enforce rate limiting

> Only customize if driven by regulatory requirements or application context from Q1.

**Select your lockout configuration:**

- [ ] **Accept standard defaults, auto-unlock only** (no admin manual unlock - simpler)

- [ ] **Accept standard defaults, admin manual unlock** (flexible for legitimate lockouts)

- [ ] **Accept standard defaults, admin manual unlock with mandatory reason** (audit justification required - accountable but more friction)

- [ ] **Custom lockout policy** - Driven by: [ ] Regulatory compliance [ ] B2B partner needs [ ] High-value target [ ] Internal help desk [ ] 24/7 operations
  - Failed attempts: _________ (range: 3-10)
  - Lockout duration: _________ minutes (10-60), or [ ] Permanent (admin unlock required)
  - Per-account rate limit: _________ attempts/minute (5-20)
  - Admin unlock: [ ] Auto-unlock only [ ] Manual unlock [ ] Manual unlock with mandatory reason

> **Trade-off:** Automatic-only prevents admin override abuse. Manual unlock helps legitimate users but requires proper audit controls. Mandatory reason improves accountability but adds workflow friction.

---

### Q16: What IP-based rate limiting strategy is needed?

**Already required:** Per-account rate limiting (10 attempts/account/minute)

**When IP-based limiting makes sense:**
✅ B2B partner application with many external users (credential stuffing attacks)
✅ Many user accounts (attacker tries different accounts from same IP)
✅ No shared corporate proxies/NAT (IP-based limiting won't block legitimate users)

❌ Do NOT implement if: Internal behind corporate proxy, VPN/NAT gateway, cloud shared egress IPs, small user base

> Base decision on Q1 context. Internal apps behind corporate network should avoid IP limiting (blocks entire office).

**Select your strategy:**

- [ ] **No IP-based limiting** - Per-account limiting is sufficient (recommended for internal applications)

- [ ] **IP-based limiting enabled** - Rate limit: _________ login attempts per IP per minute (recommend: 50-100 attempts/IP/min)

---

## 8. Role-Based Access Control

### Q17: What roles does this application require beyond the default `USER_MANAGER` and `USER` roles?

**List all application-specific roles:**

| Role Name | Purpose and Responsibilities |
|-----------|------------------------------|
| | |
| | |

> **Standard Requirement:** Role definition configuration is the source of truth. Roles cannot be created or modified through API endpoints.

---

### Q17a: Can users have multiple roles simultaneously?

- [ ] **No** - Each user has exactly one role (simpler authorization logic, easier to reason about)
- [ ] **Yes** - Users can have multiple roles, permissions are union of all assigned roles (more flexible, supports complex organizational structures)

> **Why this matters:** Affects data model (one-to-one vs one-to-many relationship), authorization checks (single role check vs iterate through roles), and role management UI complexity.

> **Trade-off:** Single role is simpler to implement and understand. Multiple roles provide flexibility for users who need permissions from different roles (e.g., a manager who also needs staff-level access).

---

### Q17b: What default role assignment and modification strategy is needed?

> **Why this matters:** Determines whether users can have zero roles, affects user creation API validation, and impacts security posture (default-deny vs default-permit).

**Select your role assignment strategy:**

- [ ] **No default role, modifiable anytime after creation** - Admins explicitly assign roles (most secure, principle of least privilege, recommended flexibility)

- [ ] **No default role, modifiable only during creation** - Role set at creation, immutable afterward (strict but inflexible)

- [ ] **Default to USER role, modifiable anytime after creation** - All new accounts get baseline USER role (consistent access, flexible)

- [ ] **Default to USER role, modifiable only during creation** - Baseline USER role, immutable afterward

- [ ] **Admin selects during creation (required field), modifiable anytime after** - No automatic default, admin chooses at creation, can change later (recommended)

- [ ] **Admin selects during creation (required field), immutable afterward** - Admin chooses once at creation, cannot change

- [ ] **Role assigned only after activation, modifiable after** - User activates first, then admin assigns role

---

### Q18: What privileges (fine-grained permissions) should be defined?

**What are privileges vs roles?**
- **Privileges** are fine-grained permissions (e.g., `USER_READ`, `USER_WRITE`, `REPORT_EXPORT`)
- **Roles** group related privileges together (e.g., `USER_MANAGER` has `USER_READ`, `USER_WRITE`, `USER_DELETE`)
- **Authorization** is enforced by roles, not individual privileges (URL path authorization checks roles)
- **Role definition** maps roles to their associated privileges (configuration-driven)

**List all privilege identifiers and role mappings:**

| Privilege Identifier | Mapped to Roles | Description |
|---------------------|-----------------|-------------|
| | | |
| | | |

> **Note:** Privileges provide semantic grouping in role definitions. URL path authorization uses roles (see Q19). If your application doesn't need fine-grained privilege abstraction, you can define roles without explicit privileges.

---

### Q19: For each role, which API endpoints should be accessible?

**Define authorization matrix mapping roles to endpoints:**

For each role, specify accessible:
- HTTP methods (GET, POST, PATCH, DELETE)
- URL path patterns

**Example authorization rules:**
- `USER_MANAGER` role:
  - `GET /users` - List users
  - `POST /users` - Create user
  - `GET /users/{id}` - View user details
  - `PATCH /users/{id}` - Update user
  - `DELETE /users/{id}` - Delete user
  
- `STAFF_ACCOUNT_CREATION` role:
  - `GET /users` - List users
  - `POST /users` - Create user only

> **Note:** Implementation may use configuration files (YAML/properties) or code-based configuration.

---

### Q20: Are there endpoints that require authentication but no specific role?

**List paths accessible to all authenticated users:**

- _________
- _________

> Configure in `pathsForAuthenticatedUsers` property.

---

### Q21: Are there endpoints that should be publicly accessible (no authentication)?

**List whitelisted paths:**

- _________ (e.g., `/health`, `/actuator/health`)
- _________ (e.g., `/public/**`)
- _________

> Configure in `pathsForWhitelisting` property. Examples: health check endpoints, public documentation, login/logout pages.

---

## 9. User Administration

### Q22: What account activation workflow is needed when administrators create users?

**Standard Requirement:**
- Only administrators can create user accounts via admin endpoints
- Self-registration is not part of this standard

**Activation token security requirements (if using pending activation):**
- Tokens must be generated using `SecureRandom` (cryptographically random)
- Store SHA-256 hash of token in database (not plaintext)
- Exclude activation token values from application logs
- One-time use: Token is invalidated after successful activation
- Issuing new activation token immediately invalidates any prior unused token for that account

> **Note:** Activation tokens follow same security practices as password reset tokens (see Q14).

**Select your activation workflow:**

- [ ] **Immediately active** - User can login with admin-provided password (no activation token needed)

- [ ] **Pending user activation, 24-hour token** - User receives activation email, must set password via activation link

- [ ] **Pending user activation, 7-day token** (recommended) - User receives activation email, must set password via activation link

- [ ] **Pending user activation, 30-day token** - User receives activation email, must set password via activation link

- [ ] **Pending user activation, custom token validity**: _________ days/hours

- [ ] **Pending email verification** - User can login but must verify email within X days

**Standard Requirements for Admin-Created Accounts (§3.5, §2 Happy Path step 7):**
- Initial password: 12 characters minimum with lowercase, uppercase, digit, special character
- User must change password on first login
- Grace period: 30 days (default) - accounts auto-disabled if not changed

---

### Q23: What self-service profile update capability should users have?

> **Why this matters:** Determines endpoint permissions and validation logic. Email/phone changes are security-sensitive and require verification. Username is immutable and password changes use dedicated endpoints.

**Select your profile update policy:**

- [ ] **No self-service updates** - All profile updates must go through administrators

- [ ] **Self-service: Basic fields only** - Users can update: [ ] Display name [ ] Department [ ] Location [ ] Job title [ ] Other: _________

- [ ] **Self-service: Email with verification** - Users can update display name and email (send confirmation link, change only after verification)
  - Also allow: [ ] Display name [ ] Department [ ] Location [ ] Job title [ ] Other: _________

- [ ] **Self-service: Email without verification** (⚠️ not recommended if email used for password reset)
  - Also allow: [ ] Display name [ ] Department [ ] Location [ ] Job title [ ] Other: _________

- [ ] **Self-service: Phone with verification** - Users can update phone (send SMS code, change only after verification)
  - Also allow: [ ] Display name [ ] Department [ ] Location [ ] Job title [ ] Other: _________

- [ ] **Self-service: Phone without verification**
  - Also allow: [ ] Display name [ ] Department [ ] Location [ ] Job title [ ] Other: _________

- [ ] **Self-service: Email (verified) + Phone (verified)** - Users can update both with verification
  - Also allow: [ ] Display name [ ] Department [ ] Location [ ] Job title [ ] Other: _________

- [ ] **Self-service: Email (verified) + Phone (unverified)**
  - Also allow: [ ] Display name [ ] Department [ ] Location [ ] Job title [ ] Other: _________

---

### Q23a: What account status visibility should users have via `/currentUser` endpoint?

> **Why this matters:** More visibility helps users understand their account state and take corrective action. However, exposing lockout timing or failed attempt counts can help attackers optimize brute force attacks.

> **Trade-off:** Minimal visibility improves security but increases support calls. Detailed visibility empowers users but exposes information useful to attackers. Moderate strikes a balance.

**Select visibility level:**

- [ ] **Minimal** - Basic profile only (username, email, name, phone) - prevents information disclosure to attackers

- [ ] **Moderate** (recommended) - Profile + operational info, helps users self-manage
  - Include: [ ] Current roles [ ] Last login [ ] Account creation date [ ] Last password change date [ ] Pending mandatory password change flag

- [ ] **Detailed** - Full transparency, empowers users but exposes info useful to attackers
  - Include all Moderate fields plus: [ ] Account locked status and unlock time [ ] Failed login attempt count

---

### Q24: Should the application support bulk user operations?

**Which bulk operations are needed? (select all that apply)**

- [ ] **Bulk user creation** - Create multiple users from CSV/file upload
- [ ] **Bulk role assignment** - Assign/revoke roles for multiple users at once
- [ ] **Bulk account disable/enable** - Disable or enable multiple accounts in one operation
- [ ] **Bulk password reset** - Generate reset tokens for multiple users
- [ ] **None** - All operations are single-user only

> **Why this matters:** Bulk operations require batch endpoints and transaction handling for partial failure scenarios.

---

## 10. HTTP Security Configuration

### Q25: What type of frontend does the application serve?

- [ ] **Traditional server-rendered HTML** (CSRF tokens in forms)
- [ ] **Single-page application (SPA)** - React, Angular, Vue (CSRF tokens in headers)
- [ ] **Mobile app or third-party API clients** (bearer tokens, CSRF not applicable)

> **Why this matters:** SPAs retrieve CSRF token from dedicated endpoint and include in `X-CSRF-TOKEN` header.

**CSRF token endpoint path:**

- [ ] `/csrf` (default)
- [ ] Custom path: _________


---

### Q26: Does the application load resources from external domains?

**Standard Requirements (§3.5):**
- `Strict-Transport-Security` (HSTS): `max-age=31536000; includeSubDomains`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY` (or `SAMEORIGIN` if using iframes)
- `Content-Security-Policy: default-src 'self'`

**List external domains for CSP whitelist:**

- _________ (e.g., Google Fonts, jQuery CDN)
- _________ (e.g., analytics scripts)
- _________

---

### Q27: What CORS origin allowlist and credential policy is needed?

**Standard Requirements (§3.5):**
- CORS origins must be explicit allowlist. Wildcard (`*`) prohibited
- `allowCredentials=true` only for explicitly whitelisted origins, never with wildcards
- All session and CSRF cookies must use `HttpOnly=true`, `Secure=true`, `SameSite=Lax`
- Login must use TLS; session cookies must have `Secure=true`

> **Important:** Never use wildcard (`*`) or `localhost` origins in production. Use environment-specific configuration to ensure dev-only origins are not deployed to production.

**Select your CORS configuration:**

- [ ] **CORS disabled** - Application accessed only from same origin

- [ ] **CORS enabled, credentials allowed** - Required for cookie-based session auth from different origin
  - Development origins: _________ (e.g., `http://localhost:3000`, `http://localhost:4200`)
  - Production origins: _________ (e.g., `https://app.company.com`)

- [ ] **CORS enabled, credentials not allowed** - No cookie-based auth needed
  - Development origins: _________
  - Production origins: _________

---

## 11. Audit, Logging, and Notifications

### Q28: What is the audit event retention period?

- [ ] **90 days minimum** (Base Standard default)
- [ ] **180 days**
- [ ] **1 year**
- [ ] **Custom**: _________ (specify compliance requirements)

> **Why this matters:** Compliance frameworks (SOC2, PCI-DSS, HIPAA) often mandate minimum audit retention periods.

---

### Q29: How long should deleted user records (tombstones) be retained in the database?

**Context:** Deleted users are soft-deleted to preserve audit history and prevent username reuse.

- [ ] **Indefinitely** (recommended - complete audit trail, minimal storage cost)
- [ ] **Match audit retention period** (90 days minimum, aligns with Q28)
- [ ] **1 year**
- [ ] **Custom**: _________ (specify compliance requirements)

---

### Q30: What notification strategy should be used for security events?

> **Why this matters:** Account owners should be notified when password is changed, account is locked, or password reset is completed.

**Select your notification configuration:**

- [ ] **No notification** (not recommended)

- [ ] **Email, synchronous** - Send immediately during HTTP request (simpler, slower response, requires SMTP)

- [ ] **Email, asynchronous** - Queue message, send via background worker (better UX, requires SMTP + message queue)

- [ ] **SMS, synchronous** - Send immediately during HTTP request (requires SMS gateway)

- [ ] **SMS, asynchronous** - Queue message, send via background worker (requires SMS gateway + message queue)

- [ ] **In-app notification** - Requires notification system implementation

- [ ] **Email (async) + SMS (async)** - Both channels with background workers (requires SMTP + SMS gateway + message queue)

- [ ] **Email (async) + In-app** - Email and in-app notifications (requires SMTP + message queue + notification system)

---

## 12. API Structure and Endpoints

### Q31: What is the API base path and version?

- Base path: _________ (e.g., `/api/v1`, `/rest/v1`)
- Version: _________

> **Why this matters:** Determines URL structure for all user management, role management, and authentication endpoints.

---

### Q32: What login endpoint request format is needed?

> **Why this matters:** JSON support requires custom `AuthenticationFilter` implementation.

**Select your login endpoint format:**

- [ ] **Form-urlencoded only** (default, recommended for server-rendered pages)

- [ ] **Form-urlencoded + JSON support** - Accept both `application/x-www-form-urlencoded` and `application/json` for SPA/API clients

- [ ] **JSON only** - SPA-first architecture, accept only `application/json`
