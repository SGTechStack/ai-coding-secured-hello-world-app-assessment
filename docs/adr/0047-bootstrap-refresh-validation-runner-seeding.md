---
status: accepted
---

# ADR-047: Bootstrap: refresh-phase validation and runner seeding, one admin, conditional on a second enrolled admin

The first administrator is created at startup from two environment-supplied values, `APP_ADMIN_USERNAME` and
`APP_ADMIN_PASSWORD`. They are **validated during context refresh**, so a bad value stops the application before the
web server accepts a connection. The **seeding itself runs in an `ApplicationRunner`**. Exactly one admin is seeded,
and that decision holds only because **a second admin is invited and enrolled before go-live**. That condition is a
handover obligation, not a startup gate. Seeding through a Flyway migration is the obvious simplification, and it is
rejected below on checksum immutability.

## Context

- PRD Story 12 requires an initial admin, seeded only when no `ADMIN` exists. It suggests `app.admin.username` and
  `app.admin.password` properties.
- Spring Boot starts the embedded web server during context refresh. It publishes `ApplicationStartedEvent` after
  refresh and before any runner is called, and `ApplicationReadyEvent` after the runners. So a check inside a runner
  means "bind the port, accept traffic, then die". Readiness does flip to `ACCEPTING_TRAFFIC` only after the runners,
  but readiness is consumed by orchestrator probes, and none is in scope.
- Flyway runs versioned migrations once and records their checksums. Spring Boot's Flyway auto-configuration also
  registers any `JavaMigration` bean, so a Java migration **can** reach the application's `PasswordEncoder`.
- Admin recovery without shell access needs another admin who can authenticate (ADR-048). With a sole admin, a
  forgotten password or a lost phone can be recovered only by the offline recovery runner, a planned outage
  (ADR-072).
- NIST SP 800-63B-4 §4.1.2.1 requires binding an additional authenticator at the lower of the account's maximum
  available AAL and the AAL at which the new authenticator will be used. For a first enrolment that is
  password-only, so password-only TOTP enrolment by the seeded admin is compliant.

## Decision

- **Validation at refresh.** A `@Validated @ConfigurationProperties` bean binds both values and validates the password
  through the same `PasswordService` every other path uses: the 15-character floor, the breach blocklist and the
  strength gate (ADR-002, ADR-005). There is no second validator and no exemption for the seed. The same bean runs the
  role checks (ADR-042). Any failure aborts refresh before the port binds (T-ADM-023).
- **Credentials from the environment only.** Absent from every committed file, including `dev`. No default and no
  generate-and-log fallback.
- **A reserved-name denylist, one set with two readers:** this validator and the self-registration username validator.
  Reserving names only at bootstrap would leave `administrator` free for the next self-registrant (T-ADM-022). It is
  an availability control against targeting a predictable admin username. It is **not** an anti-enumeration measure,
  since the registration response already makes any name confirmable (ADR-032), and it is not an ASVS 6.3.2 control.
- **Seeding in a runner**, idempotent:
  - no `ADMIN` row → seed one (T-ADM-024 covers the opposite case);
  - an `ADMIN` row exists but is **disabled** → do not seed. Minting a fresh way in around a deliberate disable is the
    hole, not the fix (T-ADM-025);
  - the configured username exists as a **tombstone** → fail fast with a clear message, because the tombstone
    legitimately blocks it (T-ADM-026).
- **The seed is a forced-change credential.** It sets `force_password_change` and stamps `credential_issued_at`, so the
  lazy 30-day expiry applies to it (ADR-046).
- **Not profile-gated.** Story 12 makes this a production necessity. What is gated is the secret, not the mechanism.
- **One admin, conditional on a second.** Before go-live, the seeded admin invites a second admin through the
  activation-token flow (ADR-006). The invitee sets their own password and enrols their own TOTP. A pending invite does
  not count. The condition is a handover item with its proof (R-ADM-017), deliberately not a gate (see ADR-048).
- **Fresh-deploy sequence:** seed → login → 403 `PASSWORD_CHANGE_REQUIRED` outside the forced-change allowlist →
  change password → enrol TOTP → factor granted → `/api/admin/**` opens. Until the first enrolment the admin surface is
  closed, not merely password-protected, because nobody holds the factor.

## Considered options

- **Seed in a Flyway migration.** Rejected on checksum immutability. A BCrypt hash inside a versioned migration is
  frozen by its checksum, so the cost factor could never be re-tuned without a repair. Committing any hash of a real
  credential to version control is also the exposure IM8 as-8 is about. The argument that a migration cannot reach
  `PasswordEncoder` is **false** (see Context) and is not relied on. The tool substitution is registered (R-DATA-001).
- **Validate in the runner.** Rejected: it cannot fail before the port binds.
- **Seed two admins.** Rejected. It needs two environment-supplied standing credentials and two enrolments, and the
  second is as likely to be shared or forgotten. It would not close the targeted-cap path either: an attacker who can
  confirm usernames caps two admins in the same time as one, and a cap on every admin ends at the runner at any admin
  count.
- **A standing emergency account not assigned to a person.** Rejected. It is a shared credential, and it would undo
  the per-invocation attribution the recovery runner records.
- **Make "two enrolled admins" a startup gate.** Rejected. A gate closes the admin surface on the survivor exactly when
  one of two admins is capped or has lost their phone, which makes the second admin useless when needed.
- **Refuse to serve `/api/admin/**` until an admin is enrolled.** Not needed: the factor check already denies it. Seeding
  a disabled admin and activating it later is not implementable, because enabling a user needs an enrolled admin.

## Consequences

- ASVS 6.3.2 (L1) is satisfied, more strongly than it asks. It names default accounts with default credentials, and
  this account has an operator-supplied username and no default credential. Refusing to re-seed around a disabled
  admin goes further than 6.3.2 reaches.
- The first-enroller race is a recorded residual, not a deviation: whoever reaches provisioning with the seed password
  first binds their authenticator. The operator enrols before the deployment is reachable by anyone else (R-ADM-016).
- Nothing detects "nobody invited the second admin" out of the box. A startup check would fire on every first boot. The
  authenticable-admins gauge sees it only if the deployer enables metrics export (R-OBS-007), and the recovery
  rehearsal record is the proof (R-ADM-017).
- Until the second admin is enrolled, everyday recovery (forgotten password, lost phone) is a planned outage.

## Sources

- PRD Story 12.
- Standalone User Access Control Application Standard §2 Happy Path steps 1–3 and 7; Questions Q3 (initial
  administrator bootstrap).
- Spring Boot 4.1 reference, "SpringApplication", Application Events and Listeners; "Database Initialization", Flyway
  (`JavaMigration` beans).
- NIST SP 800-63B-4 §4.1.2.1.
- OWASP ASVS 5.0: 6.3.2 (L1).
- IM8 as-8.
