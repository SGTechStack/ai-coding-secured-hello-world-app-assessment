# Secured Hello World: backend

## Running locally

Every secret and origin is required and has no default in any profile (ADR-062). Startup refuses before the port opens
if one is missing or malformed. Supply them as environment variables:

| Variable | Property | Value |
|---|---|---|
| `APP_MFA_TOTP_ENCRYPTION_KEY` | `app.mfa.totp.encryption.key` | 32 random bytes, padded Base64 |
| `APP_MFA_TOTP_ENCRYPTION_KEYVERSION` | `app.mfa.totp.encryption.key-version` | `0`–`255`, e.g. `1` |
| `APP_SECURITY_HMAC_TOMBSTONE_KEY` | `app.security.hmac.tombstone.key` | 32 random bytes, padded Base64 |
| `APP_SECURITY_HMAC_TOMBSTONE_VERSION` | `app.security.hmac.tombstone.version` | `0` or more, e.g. `1` |
| `APP_SECURITY_HMAC_LOG_KEY` | `app.security.hmac.log.key` | 32 random bytes, padded Base64 |
| `APP_ADMIN_USERNAME` | `app.admin.username` | the seed administrator's username |
| `APP_ADMIN_PASSWORD` | `app.admin.password` | the seed administrator's initial password |
| `APP_ORIGINS_SPA` | `app.origins.spa` | e.g. `http://localhost:5173` |
| `APP_ORIGINS_API` | `app.origins.api` | e.g. `http://localhost:8080` |

Generate each key separately. The three keys must be different: startup refuses a reused key. Use a CSPRNG
(R-CFG-008):

```sh
openssl rand -base64 32
```

```powershell
$b = [byte[]]::new(32); [System.Security.Cryptography.RandomNumberGenerator]::Fill($b); [Convert]::ToBase64String($b)
```

Never use PowerShell's `Get-Random`. Never commit a key, or put one in a committed properties or YAML file.

Then start the app under the `dev` profile:

```sh
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

At startup the log shows one `Key loaded` line per key, with its property, version and an 8-hex-character
fingerprint. The key itself is never logged. Compare fingerprints to confirm which key is live.

The tests need none of these variables. The test harness supplies test-only canary values (`TestSecrets`).

## Release build

With no CI pipeline, a developer's green `verify` is the release gate (ADR-068; ADR-069). The release command is:

```sh
mvn -f backend/pom.xml clean verify
```

Never add `-DskipITs`, `-Dmaven.test.skip` or `-Ddependency-check.skip=true` to a release build. The first two skip the
Failsafe-run traceability and drift gates (R-BLD-009); the third skips the dependency scan (R-BLD-007). Surefire and
Failsafe are pinned at exactly 3.6.0, so `-DskipTests` no longer skips the Failsafe gates (R-BLD-005).

`verify` runs these gates:

| Gate | What fails the build | Where |
|---|---|---|
| `@Proves` traceability (ADR-068) | a test cites an unknown T-ID; the test plan is missing, unreadable, empty or has the wrong header (T-BLD-007; T-BLD-008); a row has no test and is not on the pending ledger; a ledger entry already has a test | `TraceabilityGateIT` |
| Register drift (ADR-069; T-BLD-006) | a committed rendering (`docs/register/deferral-register.md`, `docs/register/handover.md`) differs from what `docs/register/register.md` regenerates, or the table breaks the register schema | `RegisterDriftIT` |
| Audit log inventory (R-AUD-027; T-AUD-017) | `docs/audit/log-inventory.md` differs from what `AuditEvent` generates; regenerate with `-Daudit-inventory.regenerate=true` | `AuditInventoryDriftIT` |
| OWASP Dependency-Check (R-BLD-007; R-BLD-010) | a dependency finding at CVSS 7.0 or higher | `dependency-check-maven`, bound to `verify` |

Set `NVD_API_KEY` in the environment before a release build. Without a key the NVD download is slow or refused, and
the first download takes a long time either way.

## Local inner loop

Skip only the dependency scan, never the tests:

```sh
mvn -f backend/pom.xml verify -Ddependency-check.skip=true
```

## Register renderings

Edit `docs/register/register.md` only. Then regenerate both renderings:

```sh
mvn -f backend/pom.xml verify -Ddependency-check.skip=true -Dregister.regenerate=true
```

## Pending ledger

`docs/test-plan/pending-ledger.txt` lists, one T-ID per line, the test-plan rows that have no test yet. A change that
proves a row removes its line. Ticket 28 deletes the file.

To reconcile after a merge, run only the traceability gate:

```sh
mvn -f backend/pom.xml verify -Ddependency-check.skip=true -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=TraceabilityGateIT
```

It writes the ledger entries that already have tests to `backend/target/traceability/ledger-proven.txt`, one per
line, and its failure message lists exactly which lines to add to or remove from the ledger.

## Mutation testing

```sh
mvn -f backend/pom.xml -Pmutation verify
```

PIT runs at a mutation threshold of 85 over the security-decision classes (spec, Mandatory gates 7), listed by class
name in the `pitest-maven` configuration. While none of those classes exist, PIT finds no mutations and passes.
