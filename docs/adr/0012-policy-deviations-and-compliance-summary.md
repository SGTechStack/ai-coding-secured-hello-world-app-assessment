# ADR-0012: Policy deviations and IM8 / App-Standards compliance summary

## Status

Accepted

## Context

This app has to satisfy three sources, in priority order: `prd/assessment-prd.md`, then
App-Standards (SGTechStack/App-Standards, **reviewed from a local clone dated 2026-09-10**; this
review couldn't refresh it from upstream), then IM8 controls. Where they conflict, the PRD wins.
Standard-only features outside the PRD's scope were deliberately not built. This ADR records every
knowing deviation, so a reviewer can tell deliberate gaps from oversights.

## Compliance summary

| Area | Control / source | How it is met |
|---|---|---|
| Password storage | PRD NFR | BCrypt, cost 12 (ADR-0006) |
| Password policy | PRD, NIST 800-63B | 12 characters to 72 UTF-8 bytes, no composition rules |
| Brute force / lockout | UAC §3.5, PRD Story 3 | 5 failures → 20 min lock that can't be extended; per-(IP, user) throttle trips first; per-IP and per-account limits; `Retry-After` (ADR-0005) |
| Enumeration | UAC | Identical 401 for unknown/wrong/locked/disabled with equal BCrypt cost; reset request always 200 with async email; merged registration 409 message plus rate limit |
| CSRF | UAC §3.1, HDR | Session-stored token; nothing JS-readable in cookies (ADR-0003) |
| Sessions | PRD, UAC §4 | Spring Session JDBC; HttpOnly/SameSite/Secure cookie; 15 min idle / 8 h absolute; 1 concurrent session; fixation protection; server-side revocation on reset, disable, role change, delete and logout (ADR-0009) |
| Authorisation | PRD Story 8 | `/api/admin/**` requires ADMIN; self-action and last-admin guards; 403 audited |
| Security headers | HDR | HSTS 1y + includeSubDomains, CSP `default-src 'none'` …, Referrer-Policy `no-referrer`, Permissions-Policy, nosniff, frame DENY, `Clear-Site-Data` on logout |
| Input validation / errors | UAC, OWASP | Bean Validation limits on every body; fixed `{code,message}` errors; no input echoed and no stack traces; generic 500 |
| Audit logging | LOG §3.3/§4 | ECS JSON, typed events, UUID identities only, trace/correlation ids (ADR-0011) |
| Reset tokens | PRD Story 6/7 | 256-bit random, only the SHA-256 stored, 30 min TTL, single use (atomic claim); older tokens invalidated; reset clears lockout and ends all sessions |
| Dependencies | App-Standards | Spring Boot 4.0.x latest patch; `mvn -Pcve verify` (OWASP dependency-check, fails on CVSS ≥ 7); `npm run audit` |
| Quality gate | App-Standards | `mvn verify` enforces JaCoCo ≥ 80% line coverage; frontend lint, typecheck and tests |

## Deviations (accepted)

1. **BCrypt instead of Argon2id.** App-Standards prefers Argon2id, but the PRD mandates BCrypt.
2. **Length-only password policy.** No composition rules (IM8 as-5, privileged-admin recipe), per
   NIST SP 800-63B and the PRD.
3. **Registration still reveals "taken" with a 409** (PRD Story 1). Mitigated by one merged message
   for username and email, plus the per-IP limit.
4. **Out of PRD scope, not built:**
   - inactive-account hygiene (IM8 ac-3)
   - password history and forced change (as-15)
   - self-service password change
   - admin unlock (a password reset unlocks instead)
   - soft-delete tombstones
5. **Local username/password instead of the organisation IdP** (ac-12). The PRD mandates it, and
   the audience is demo/non-production.
6. **Rate limits are per instance and in memory** (ADR-0005). Sessions are shared via JDBC; the
   limits aren't.
7. **`ddl-auto=update`, no migration tool** (ADR-0008). Adopt Flyway before any real database,
   including the Spring Session schema.
8. **The dev-profile email stub logs the reset link** (ADR-0001). It is dev only and doesn't log
   the email address.
   There is **no production `EmailService`**: under `prod` the app deliberately fails to start
   (missing bean) until a real mail implementation is added, rather than silently dropping or
   logging reset links.
9. **No SAST/Semgrep or CI pipeline in the repo.** The CVE scan is a manual Maven profile that
   needs an `NVD_API_KEY`. Frontend `npm run audit` still reports moderate advisories in the vitest
   3.x dev-toolchain (dev only, not shipped). Clearing them needs a major vitest upgrade.
10. **Agent-written code**, delivered uncommitted for full human review before commit (ARC
    CTRL-0070 is satisfied by that review).
11. **Prod cookies are `SameSite=Strict`**, stricter than the standard's `Lax`. This works because
    SPA and API are same-site in prod.

## Consequences

Anything above that becomes in scope (production use, multiple instances, a real IdP) must reopen
the relevant ADR. Items 6, 7 and 9 block production readiness.
