# App Standard: SSO User Access Control — Implementation Questions

Answer these questions with your team or product owner before implementation to determine which SSO authentication and authorization requirements apply to your application.

---

## 1. Identity Provider Configuration

### Q1: What is your IdP configuration and required mix-up attack defense?

**Select your architecture and security controls:**

- [ ] **Single IdP registration** → No mix-up defenses needed
- [ ] **Multiple IdP registrations** with **distinct redirect URI per registration** (recommended)
- [ ] **Multiple IdP registrations** with **`iss` parameter validation** (RFC 9207)
- [ ] **Multiple IdP registrations** with **both methods** (defense-in-depth)

> **Why this matters:** Multiple IdP registrations (different providers OR multiple tenants/realms of same provider) introduce mix-up attack risk where authorization response from one IdP could be substituted into flow initiated with another IdP. At least one defense method is required when using multiple registrations.

**Required implementation (§2 Happy Path step 6, §3.5):**
- When multiple IdP registrations configured, at least one mix-up defense method is required
- Each registration must use distinct, non-overlapping identity claim to prevent account confusion

---

### Q2: What OAuth 2.0 / OIDC provider metadata is available for each IdP?

**Collect the following for each IdP:**

- Issuer URL (for `.well-known/openid-configuration` discovery): _________
- Token endpoint URL: _________
- Authorization endpoint URL: _________
- JWKS (JSON Web Key Set) endpoint URL: _________
- Supported signing algorithms: _________ (must be RS256 or ES256)
- Back-channel logout endpoint (if supported): _________

**Standard Requirements (§4):**
- Provider metadata must be fetchable from `.well-known/openid-configuration` at startup
- Application startup must fail if metadata fetch fails
- All IdP endpoints must use HTTPS (not HTTP)
- JWKS endpoint certificate must be trusted by application's trust store

> **Security validation checklist before integrating:**
> - [ ] Verify IdP issuer URL matches expected value (prevent typosquatting)
> - [ ] Confirm signing algorithms are RS256 or ES256 (reject HS256 or none)
> - [ ] Test `.well-known/openid-configuration` endpoint accessibility from application network
> - [ ] Validate JWKS endpoint returns valid JSON Web Key Set
> - [ ] Check IdP certificate chain is trusted (no self-signed certs in production)

---

### Q3: What client authentication method will be used for token exchange?

- [ ] **Client secret (symmetric key)** - IdP issues shared secret, sent in token request
- [ ] **Private key JWT (asymmetric)** - Application signs JWT assertion with private key, IdP validates with public key (RFC 7523)
- [ ] **Other**: _________

> **Recommendation:** Private key JWT avoids shared secret exposure. Client secret acceptable for trusted environments with secure storage.

---

### Q4: What scopes should be requested from the IdP?

**Required:**
- [ ] `openid` (required for OIDC)

**Optional (select all that apply):**
- [ ] `profile`
- [ ] `email`
- [ ] **MCC Common Services scopes** (if integrating with MCC platform):
  - [ ] `mcns` (notification services)
  - [ ] `ssft` (file scanning services)
  - [ ] `mpds` (data services)
  - [ ] Other MCC service scopes: _________
- [ ] **Custom scopes**: _________

> **Recommendation:** Request only necessary scopes. Extract only identity and role-derivation claims to minimize PII storage.

> **Note:** If integrating with MCC (Ministry Common Cloud) common services, include the appropriate service-specific scopes. Only request scopes your application actively uses.

---

### Q4a: What IdP hint parameter strategy will improve login UX?

> **Context:** IdP hints improve login UX by pre-filling email (`login_hint`), skipping tenant selection (`domain_hint` for Azure AD), or pre-selecting federated IdP (`kc_idp_hint` for Keycloak).

**Select your approach:**

- [ ] **No hints** - Users complete full login flow (simplest)
- [ ] **Include IdP hint** - Specify parameter: _________ (e.g., `login_hint`, `domain_hint`, `kc_idp_hint`)
  - Hint value or logic: _________ (e.g., static `contoso.com`, captured from form, URL parameter)

---

## 2. Infrastructure and Database

### Q5: What relational database will be used?

- [ ] **PostgreSQL** (recommended for production)
- [ ] **MySQL/MariaDB** (widely supported, good performance)
- [ ] **Oracle** (enterprise, requires commercial license)
- [ ] **SQL Server** (Microsoft environments, requires commercial license)
- [ ] **H2** (dev/test only)
- [ ] **Other**: _________

> **Recommendation:** PostgreSQL for production. MySQL/MariaDB acceptable alternatives. H2 only for development.

> **Why this matters:** Affects Spring Session schema, ShedLock configuration, and database connection pool tuning.

---

### Q6: What session persistence backend and datasource configuration will be used?

> **Why this matters:** Multi-node deployments must use distributed session store. This choice also determines where OAuth2 flow security state is stored:
> - PKCE code verifiers, nonces, OAuth state parameters (stored in session by Spring Security OAuth2 Client)
> - Session-based authorization code tracking to prevent replay

**Select your configuration:**

- [ ] **Spring Session JDBC with shared datasource** (recommended - reuse existing database)
- [ ] **Spring Session JDBC with separate datasource** (dedicated session database)
- [ ] **Spring Session Redis** with Redis cluster (higher performance, separate infrastructure)
- [ ] **In-memory** (single instance only, not for production multi-node)

---


### Q7: What batch job coordination strategy is needed?

**Context:** While the external IdP manages user authentication and lifecycle, the application maintains local authorization records (SSO ID → Role mappings). Batch jobs operate on these local records only.

> **Why this matters:** Batch jobs provide defense-in-depth by revoking local roles for inactive users, even when IdP deprovisioning is delayed. Also used for cleanup of expired OAuth state and audit logs.

**Select your strategy:**

- [ ] **No batch jobs** - No scheduled jobs planned (IdP handles all lifecycle management)
- [ ] **Batch jobs with JDBC locking** (recommended if using Spring Session JDBC)
- [ ] **Batch jobs with Redis locking** (recommended if using Spring Session Redis)

> **Recommendation:** Match session persistence backend for locking provider.

---

**If YES, what types of batch jobs are needed?**

**Select all that apply:**
- [ ] **Role synchronization** - Sync local role mappings when IdP claims change between logins (only if Q13 uses "populate on first login")
- [ ] **Role revocation** - Revoke elevated local roles from dormant SSO users (defense-in-depth when IdP deprovisioning delayed)
- [ ] **Authorization record disablement** - Disable local authorization records for inactive SSO users
- [ ] **Data cleanup** - Purge expired authorization codes, state parameters, old audit logs
- [ ] **Compliance reporting** - Generate access reports, inactive account lists
- [ ] **Other**: _________

---

**If role revocation or authorization record disablement selected, specify thresholds:**

**Inactivity threshold for authorization record disablement:**

- [ ] **90 days** since last SSO login (default, recommended)
- [ ] **60 days** (high-security environments)
- [ ] **180 days** (seasonal users, contractors with intermittent access)
- [ ] **Custom**: _________

**Inactivity threshold for elevated role revocation:**

- [ ] **180 days** since last SSO login (default, recommended)
- [ ] **90 days** (high-security, critical roles like ADMIN)
- [ ] **Custom**: _________

**Batch job schedule:**

- [ ] **Daily at off-peak hours** - Specify time: _________ (timezone: [ ] UTC [ ] Local)
- [ ] **Weekly** - Specify day and time: _________

---

## 3. Initial Setup and Identity Resolution

### Q8: Is this application migrating from a different IdP?

**Standard scenario:** Application integrates with existing corporate IdP. Skip if not migrating between IdPs.

- [ ] **Not applicable** - Standard integration with existing IdP
- [ ] **Hard cutover** - Switch all users to new IdP on go-live
- [ ] **Gradual migration** - Run both IdPs in parallel (requires account linking logic)

---

### Q9: How will the initial production administrator account be bootstrapped?

**Context:** Application needs at least one admin on first deployment to bootstrap authorization (chicken-and-egg problem).

**Select your bootstrap strategy:**

- [ ] **SSO with automated mapping** - Admin authenticates via SSO, roles assigned via authority-mapping policy
  - Mapping logic: _________ (e.g., IdP group 'Admins', MPDS department=IT, email @admin.example.com)
  - How determined: _________ (IdP admin console, MPDS query, HR system)

- [ ] **SSO with manual pre-assignment** - Admin authenticates via SSO, role pre-inserted via Liquibase
  - SSO ID: _________ (e.g., specific `sub` claim value)
  - How determined: _________ (IdP admin console, known user identifier)

- [ ] **Seed account in Liquibase changeset** (rare - for emergency admin only, not recommended)

> **Why this matters:** Must be known before deployment for role pre-mapping or attribute matching logic. For MCC+Singpass environments, MPDS attributes can identify admin users via organizational data.

---

### Q10: What claim strategy uniquely identifies users across IdP registrations?

**Standard Requirement (§2 Decision Logic):**
- Application must resolve SSO identity from configured issuer-specific claim
- Multiple IdP registrations require distinct, non-overlapping identity claims to prevent account confusion

**Select your claim strategy:**

- [ ] **Single IdP: `sub` claim** (recommended - standard OIDC stable identifier)
- [ ] **Single IdP: custom claim** - Specify: _________ (e.g., `employee_id`, `username`, `email`)
- [ ] **Multiple IdPs: distinct claims per registration** (required by standard)
  - Registration 1 claim: _________ Registration 2 claim: _________ (must not overlap)
- [ ] **Multiple IdPs: same claim across registrations** (⚠️ violates standard - risks account confusion)

---

### Q11: Should the application support account linking across different IdP registrations?

- [ ] **No - separate local authorization records per registration** (default, required by standard)
- [ ] **Yes - explicit user action or administrator intervention required** for linking
- [ ] **Automatic linking** based on email or other attribute (not recommended, security risks)

**Standard Requirement (§4 Multi-IdP Support):**
- When same user authenticates via different IdP registrations, application must create separate local user records
- Account linking requires explicit user action or administrator intervention

---

## 4. Authorization and Role Mapping

### Q12: What is the authority-mapping policy for first-time SSO users?

**Choose authority mapping approach:**

- [ ] **Default role only** - All new users get baseline `USER` role (manual role assignment workflow)
- [ ] **Claim-based mapping** - Extract IdP group/role claims and map to application roles (rapid onboarding)
- [ ] **Administrator-initiated** - New users have no roles until admin assigns (approval workflow)
- [ ] **Custom logic** - Email domain, organizational unit, or MPDS attributes

**When to use each:**
- **Default role**: Internal apps, high-security apps, application-specific roles that don't map to IdP
- **Claim-based**: Corporate IdP manages roles, need rapid onboarding, centralized governance
- **Admin-initiated**: Regulatory compliance (SOX, HIPAA), B2B apps with varying org structures
- **Custom logic**: Email domain-based access (e.g., @company.com vs @partner.com), MPDS attributes

**If claim-based or custom logic selected, which claims to extract?**

- [ ] Group memberships (`groups`, `roles` claims)
- [ ] Organizational attributes (`department`, `ou`)  
- [ ] Custom claims: _________

---

### Q13: How should local roles be synchronized with IdP claims after initial onboarding?

**Context:** This question only applies if Q12 selected **claim-based mapping** or **custom logic** that uses IdP claims. If Q12 selected "default role only" or "administrator-initiated", skip this question.

**Standard Requirement (§2 Decision Logic):**
- Authorization checks always use locally stored roles, never IdP token claims directly
- IdP claims may be consumed to populate/update local roles, but local storage is authoritative
- This question is about synchronization frequency, not where authorization happens

**Choose synchronization strategy:**

- [ ] **One-time mapping on first login** - Extract IdP claims once during first login, store locally, never automatically update from IdP again (recommended)
- [ ] **Refresh on every login** - Re-extract IdP claims and update local roles each time user authenticates
- [ ] **Manual/batch synchronization only** - Admins or batch jobs can trigger role sync from IdP, but no automatic updates

---

**When to use each approach:**

✅ **One-time mapping (first login only):**
- Want local autonomy to manually adjust roles after onboarding
- Performance priority - avoid claim extraction overhead on every login  
- IdP claims are initial assignment, but application owns role lifecycle after that
- **Trade-off:** Role changes in IdP won't propagate automatically - need batch job (Q7) or manual admin intervention

✅ **Refresh on every login:**
- IdP is single source of truth for roles (centralized governance)
- Need rapid role propagation (user changes departments in IdP → access updates immediately)
- Zero local admin overhead - all role management done in IdP/HR system
- **Trade-off:** Local role overrides not possible. If IdP claims are wrong or service is down, user access affected.

✅ **Manual/batch synchronization:**
- Need explicit approval or audit trail before propagating IdP role changes
- Periodic reconciliation via batch job (e.g., nightly sync from IdP)
- **Trade-off:** Most admin overhead - requires intervention or scheduled jobs

> **Why this matters:** Determines whether IdP role changes (promotions, department transfers) automatically propagate to application, or require manual intervention.

---

## 5. OAuth2/OIDC Security

### Q14: What rate limit and retry policy will protect the OAuth2 callback endpoint?

**Standard Requirement (§3.5):**
- Callback endpoints must be rate-limited to 10-60 successful authorizations per OAuth client registration per minute
- Respond with `429 Too Many Requests` and `Retry-After` header when breached
- Rate-limit counters reset on successful token validation

**Select your rate limit configuration:**

- [ ] **10-30 per minute, 60 second retry** (stricter, recommended for low-traffic/sensitive apps)
- [ ] **60 per minute, 60 second retry** (permissive, for high-volume legitimate traffic)
- [ ] **Custom rate limit**: _________ per minute, retry window: _________ seconds

---

### Q15: What clock skew tolerance is needed for ID token validation?

**Context:** Spring Security defaults to 60 seconds clock skew tolerance when validating ID token time claims (`exp`, `iat`). This handles normal NTP drift.

> **When to customize:** Multi-region deployments with known time sync issues, edge computing environments, or strict security requirements needing tighter tolerance (30 seconds)

**Select your tolerance:**

- [ ] **Use default (60 seconds)** - Recommended for most deployments
- [ ] **Custom tolerance**: _________ seconds (typical range: 30-120 seconds)

---

### Q16: What userinfo endpoint strategy is needed for identity resolution or role mapping?

> **Recommendation:** Use userinfo endpoint only if ID token lacks required claims.

**Standard Requirement (§3.5):**
- Fetch over HTTPS using access token after token validation, before session creation
- Validate userinfo response signature (if signed), HTTPS transport, and `sub` claim matches ID token
- On error, abort login and return generic error

**Select your strategy:**

- [ ] **Not needed** - ID token contains all necessary claims (most common)
- [ ] **Fetch after ID token validation, before session creation** - Every login
- [ ] **Fetch only on first login** - Not on subsequent logins

---

## 6. Session Management

### Q17: What are the session timeout and concurrency policies?

**Idle session timeout:**

- [ ] **15 minutes** (recommended) - adjust to 5-10 min (high-security) or 20-30 min (workflow-heavy)
- [ ] Custom: _________ minutes

**Absolute session timeout:**

- [ ] **8 hours** (business hours) - adjust to 4 hours (high-security) or 12-24 hours (24/7 ops)
- [ ] Custom: _________ hours

**Maximum concurrent sessions per user:**

- [ ] **1** (high-security, single device)
- [ ] **2-3** (multi-device: laptop + mobile)
- [ ] Unlimited (not recommended)

> On exceeding limit, oldest session is invalidated.

**Where should users be redirected after session expiry?**

- [ ] **Default invalid-session path**
- [ ] **Custom expired-session page** with "Log in again" button
- [ ] **Back to SSO entry point** (automatic re-authentication)

---

## 7. Logout and Session Termination

### Q18: When user logs out, should the application notify the IdP?

- [ ] **No - invalidate local session only** (simplest, recommended for most cases)
- [ ] **Yes - single logout (SLO) via front-channel** (redirect user to IdP logout endpoint)
- [ ] **Yes - back-channel logout** (IdP notifies application when user logs out elsewhere)

**Standard Guidance (§4):**
- On user-initiated logout, local session must be invalidated and cookies cleared
- Notifying IdP is optional

**If back-channel logout supported, validation required:**

**Standard Requirement (§5 Back-Channel Logout Tests):**
- Logout tokens must be validated for: signature (using IdP's JWKS), issuer (`iss`), audience (`aud`), expiration (`exp`)
- Logout tokens missing session ID (`sid`) or subject (`sub`) claim are rejected

**Standard Requirements:**
- Logout response must include `Clear-Site-Data: "cache", "cookies", "storage"` header
- CSRF token validation required on logout requests
- Logout on an expired/invalid session handled gracefully on the client: because logout keeps its session-bound CSRF protection, such a request returns 401/403 (not 200); the SPA treats this as an already-terminated session, clears local state, and redirects to login so the user never sees an error page

*(Base Standard §3.5, §2 Failure Paths step 15)*

---

## 8. HTTP Security and CORS

### Q19: What is the CSRF token endpoint path?

- [ ] `/csrf` or `/csrf-token` (common convention)
- [ ] **Custom path**: _________

> The CSRF token endpoint accepts authenticated requests and returns valid CSRF token in JSON format. *(Base Standard §3.1)*

---

### Q20: What are the allowed origins for CORS?

- [ ] **None** - Application accessed only from same origin (disable CORS)
- [ ] **Specific frontend domains** - List: _________
- [ ] **Internal network domains** - List: _________

**Standard Requirements (§3.5):**
- CORS policy must use explicit allowlist of origins. Wildcard (`*`) prohibited
- OAuth2 callback endpoint must not allow cross-origin requests
- All HTTP security headers must be enforced (HSTS, X-Content-Type-Options, X-Frame-Options, CSP)
- `Cache-Control: no-store` required on responses with sensitive authentication data

---

## 9. Audit and Logging

### Q21: What audit retention period and storage strategy is required?

**Standard Requirement (§3.3 & §3.4):**
- Audit events must be retained for minimum 90 days (12 months if centralized SIEM available)
- All logs must be aggregated to centralized logging or SIEM system for analysis, correlation, and incident response

**Select your configuration:**

- [ ] **90 days in application database only** (Base Standard minimum)
- [ ] **90 days in database + centralized SIEM** (recommended)
- [ ] **12 months in centralized SIEM** (ELK, Splunk, CloudWatch) + short-term database
- [ ] **Custom retention**: _________ days/months - Storage: _________

---

**Standard Requirements:**
- Do not log raw (unhashed) session identifiers; use one-way hashing (`session.hash`)
- Include session-scoped context: `session.hash` and `user.id` UUID (omit user identity if UUID not yet resolved)
- Log INFO: Successful SSO login, logout, session creation, successful authorization, MFA challenges
- Log WARN: Failed authentication, callback validation failures, rate limit rejections, CSRF failures, authorization denials, token validation failures, failed MFA
*(Base Standard §3.4)*

---

## 11. Role Management and Authorization

### Q22: What roles and endpoint access patterns does this application require?

> **Role design principle:** Start with minimal roles, expand as needed. Use hierarchical roles (`USER` < `MANAGER` < `ADMIN`) for simple permission models, flat roles (`DEVELOPER`, `ANALYST`, `AUDITOR`) for cross-functional teams. More than 10 roles suggests over-engineering.

**List application-specific roles beyond default `USER`:**

| Role Name | Purpose | Typical Users |
|-----------|---------|---------------|
| | | |

**For each role, define accessible endpoints:**
- HTTP methods (GET, POST, PATCH, DELETE)
- URL path patterns

**Endpoints accessible to all authenticated users (no specific role):**
- _________

**Public endpoints (no authentication):**
- _________ (e.g., `/health`, `/actuator/health`, OAuth callback `/login/oauth2/code/*`)

---

### Q22a: What self-read endpoint capability should users have?

> **Context:** Self-read endpoint allows authenticated users to retrieve their own SSO ID, assigned roles, and optional profile fields (e.g., `/currentUser` or `/users/me`) for UI personalization.

**Select your configuration:**

- [ ] **No self-read endpoint** - Users cannot programmatically view their own profile
- [ ] **Minimal self-read** at path: _________ - Returns SSO ID and assigned roles only (recommended)
- [ ] **Extended self-read** at path: _________ - Returns SSO ID, roles, plus: [ ] Email [ ] Display name [ ] Last login [ ] Other: _____

---

## 12. Token Handling (Optional)

### Q23: What refresh token handling strategy is needed?

> **Why this matters:** Refresh tokens used when application calls external APIs on behalf of user.

**Standard Requirements (§5):**
- Refresh tokens stored server-side only (HttpOnly cookies or database); never client-side storage
- Refresh tokens do not appear in logs, error messages, or URLs
- Failed refresh token requests cause immediate session invalidation and require re-authentication
- Refresh tokens do not extend local session lifetime beyond configured limits

**Select your strategy:**

- [ ] **Not issued** - Access tokens issued without refresh capability
- [ ] **Issued and used** - Obtain new access tokens for calling external resource servers (e.g., IdP's user management API)
- [ ] **Issued but discarded** - Not used, discarded after session creation

**Standard Requirements - Access Token Handling (§5):**
- Access tokens with lifetime exceeding 30 minutes are rejected
- Tokens stored server-side only (session store); never client-side storage (localStorage, sessionStorage)
- Tokens do not appear in URLs or HTTP headers visible to client-side JavaScript

---

## 13. Testing and Validation

### Q24: What testing strategy will be used for OIDC tokens and time-dependent behavior?

**Standard Recommendation (§6):**
- Generate test OIDC tokens with same structure and signature format as production tokens
- Use non-production realms, client IDs, and synthetic identities
- Use fixed clocks or controllable system time for consistent, reproducible tests

**Select your testing configuration:**

- [ ] **IdP test environment** - Real IdP sandbox, no fixed clocks needed
- [ ] **IdP test environment + fixed clocks** - Real IdP sandbox, controllable time for session/token expiry testing
- [ ] **Mock OIDC provider (Keycloak/Okta test tenant)** - No fixed clocks needed
- [ ] **Mock OIDC provider + fixed clocks** - Mock IdP with controllable time for session timeout, token expiry, authorization code/state TTL testing
- [ ] **Self-signed test tokens** - ⚠️ Not recommended for signature validation tests
