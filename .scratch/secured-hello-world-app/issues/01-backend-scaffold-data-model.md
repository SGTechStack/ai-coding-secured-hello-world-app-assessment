# 01: Backend project scaffold + data model

**What to build:** A Spring Boot backend project (Maven) that boots
successfully with the H2 dev profile wired up, and has the persistent data
model in place for users and password reset tokens — no auth behavior yet,
just the foundation every later slice builds on.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Maven project under `backend/` with dependencies: Spring Web, Spring
      Security, Spring Data JPA, H2, Spring Session, Validation
      (Jakarta/Bean Validation)
- [ ] `User` entity: `id` (Long, auto-increment), `username` (unique),
      `email` (unique), `passwordHash`, `role` (enum `USER`/`ADMIN`),
      `enabled` (boolean), `failedLoginAttempts` (int), `lockedUntil`
      (nullable timestamp), `createdAt` (timestamp)
- [ ] `PasswordResetToken` entity: `id` (Long, auto-increment), `userId`
      (FK -> users), `tokenHash` (string), `expiresAt` (timestamp),
      `usedAt` (nullable timestamp)
- [ ] Spring Data repositories for both entities (at minimum: find user by
      username, find user by email, find token by token hash)
- [ ] H2 dev-profile configuration; schema created via JPA
      (`ddl-auto`/Flyway/Liquibase — implementer's choice) using only
      portable JPA types (no H2-specific column types), so the schema stays
      portable per the spec
- [ ] App boots successfully with no security rules configured yet (or a
      permissive placeholder), verified by a smoke test or manual run
