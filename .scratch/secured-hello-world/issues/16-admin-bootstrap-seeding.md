# 16: Admin bootstrap seeding with externalised credential

**What to build:** On first startup with no admin in the database, the app seeds one from
configuration, so there is a way into the admin module without hand-editing the database.
Restarting with an admin already present must not create a duplicate.

The PRD stops there; IM8's privileged-account and secrets requirements do not. **The seed
password must never be a default, a literal in the source, or a committed config value.** A
demo app that ships with a known admin password is the single most reliably exploited flaw in
this entire category, so this ticket treats the credential's provenance as part of the feature
rather than a deployment detail. "Externalised" is not satisfied by a value sitting in a
committed `application.properties`: `as-8` requires the credential arrive from an **environment
variable**, or from an externally-mounted config file that is gitignored, and from a managed
secret store (AWS Secrets Manager, HashiCorp Vault) in any real deployment.

`ac-6` goes further than provenance. A seeded credential that is merely *expected* to be rotated
is still a standing admin password of known origin, and a documented expectation is exactly the
kind of control `ac-6` rejects. So this ticket creates the seeded admin already flagged for a
forced password change, and **ticket 22 owns enforcing that gate** at authentication time. This
ticket's job is to set the flag correctly and fail closed without a supplied credential; ticket
22's job is to make the flag mean something.

Covers PRD Story 12, plus IM8 privileged-account controls.

**Blocked by:** 04.

**Status:** ready-for-agent

**IM8 controls:** `as-8` Secrets Management; `ac-6` Default Credentials; `as-6` Password Salting
and Hashing; `as-5` Password Requirements; `lm-4` Audit Logging. *ASVS: V2.3 Authenticator
Lifecycle, V14 Configuration, V6 Stored Cryptography, V7 Logging.*

- [ ] On startup with no `ADMIN` user present, one is seeded from configured credentials
- [ ] The seeded password is hashed with the **same** BCrypt encoder as any other account — no
      separate path, no weaker hashing for the seed
- [ ] On restart with an `ADMIN` already present, no duplicate seed account is created; the
      check is on the existence of an admin, not on the configured username
- [ ] The seed password is supplied **externally** — an environment variable, or an
      externally-mounted config file that is listed in `.gitignore` — and there is no default
      value, no placeholder, and no committed value anywhere in the repository. A value written
      into a tracked `application.properties` or `application-dev.properties` does **not** count
      as externalised
- [ ] No secret value appears in any file tracked by git, for any profile: the property is
      declared without a value, or read from the environment, and nothing else
- [ ] A repository check proves it — a grep or test over tracked files asserting that the
      seed-password property carries no committed value — so a future contributor pasting one in
      fails a check rather than passing review
- [ ] The deployment requirement is named, not left as "externally supplied": the credential comes
      from a managed secret store (AWS Secrets Manager, HashiCorp Vault, or the platform
      equivalent) injected at runtime, with the environment variable being the dev-only form
- [ ] If the seed password is absent when seeding is required, the application **fails fast with
      a clear message** rather than starting with a guessable or blank admin credential
- [ ] The seed password is not logged, not echoed at startup, and not included in any
      diagnostic or actuator output
- [ ] A minimum strength is enforced on the supplied seed password — the same policy as any
      other account, so the most privileged credential in the system is not the weakest
- [ ] The seeded admin is created with `force_password_change = true`, so the credential is
      flagged for rotation as a data fact rather than as a documented expectation — **enforcing**
      the gate at login is ticket 22's job, and this ticket must not ship the flag defaulted to
      false for the seed
- [ ] An audit event records that a bootstrap admin was seeded
- [ ] Test: starting with an empty database and a supplied credential creates exactly one enabled
      `ADMIN` whose hash verifies against the supplied password
- [ ] Test: the seeded admin row has `force_password_change` set to true
- [ ] Test: starting again does not create a second admin
- [ ] Test: starting with no admin present and no credential supplied fails startup rather than
      seeding a weak account
- [ ] Test: no startup log output contains the seed password
