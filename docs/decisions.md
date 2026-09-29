# Implementation decisions

The PRD is the source of truth. Work is on `jingshun`; the original README
assessment instructions and PRD remain intact.

1. **Session authentication.** JDBC Spring Session shares the database with JPA.
   Explicit context persistence and session ID rotation implement JSON login.
   Sessions are indexed by username for revocation. A monotonically increasing
   account security version additionally rejects stale sessions after password,
   enabled-status, or role changes, including sessions saved by concurrent requests.
2. **Two separate login protections.** Five consecutive account failures inside
   fifteen minutes cause a fifteen-minute account lock. An IP can make only three
   failures before being blocked until fifteen minutes after its last failure.
   Successful authentication does not erase IP failure history. This prevents one
   source from reaching the account threshold, including near window boundaries.
   Expired entries are reclaimed; at 10,000 tracked addresses, new addresses fail
   closed. The limiter is deliberately single-instance and reads the TCP peer IP.
3. **Password policy.** BCrypt cost 12; minimum 12 Unicode code points, maximum
   72 UTF-8 bytes. Longer inputs are rejected to avoid BCrypt truncation. No forced
   composition rules. Identifiers are stripped/lowercased, usernames are restricted
   to 3–50 ASCII letters, numbers or underscores; passwords are never normalized.
4. **Atomic reset.** A cryptographically random 256-bit token is sent only through
   the EmailService boundary, with its SHA-256 hash stored for twenty minutes.
   Confirmation locks the user before loading and locking the token. Selecting
   only its owner id beforehand avoids stale JPA entity caching during concurrent
   redemption. Successful confirmation consumes every outstanding token and
   revokes every indexed session for that user.
5. **Browser contract.** The SPA fetches a fresh session-bound CSRF token for every
   mutation. Tokens and credentials never enter localStorage/sessionStorage.
   Reset links use a URL fragment, which the SPA removes from history after reading
   into memory; fragments are not transmitted as HTTP request targets. Fonts are
   served locally with the frontend bundle.
6. **Database evolution.** Flyway owns schema creation; Hibernate validates it.
   The initial migration targets H2. JPA entities avoid H2-specific behavior, but
   a future Postgres/MySQL adoption needs vendor migrations and JDBC drivers,
   particularly for UUIDs, timestamps and Spring Session binary attributes.
7. **Boundaries.** Controllers validate transport input, services own transactions
   and policy, repositories own persistence, and public DTOs expose only safe fields.
   The API denies admin access server-side and rejects every admin self mutation.
8. **Scope.** No JWT, MFA, SMTP, containers, CI/CD or hosting setup, per the PRD.
   The log-based email adapter is an explicit assessment stub.

## References

- [Spring Security authentication persistence](https://docs.spring.io/spring-security/reference/servlet/authentication/persistence.html)
- [Spring Boot JDBC sessions](https://docs.spring.io/spring-boot/reference/web/spring-session.html)
- [Spring Session principal indexing](https://docs.spring.io/spring-session/reference/api.html)
