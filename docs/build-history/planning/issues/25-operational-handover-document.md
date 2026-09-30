# 25 — Decide the contents and owner of the operational handover document

Type: grilling
Status: resolved
Blocked by: 20, 24

## Question

What does the operational handover document contain, who owns it, and where does it live?

## Why this graduated from fog

The map's "Not yet specified" section carried an "HTTPS/HSTS handover notes" patch whose shape was
unknown. It is now sharp, because six required contents have accumulated from five different tickets
and none of them has an owner. "Probably a section of some document" stopped being an honest plan.

Four of the six are compensating controls for deferrals, which is the part that makes this
load-bearing rather than administrative: we have justified deferring real controls **on the grounds
that this document exists**. If it stays unwritten, those justifications collapse.

## Required contents already owed

From [Decide the origin topology](20-deployment-origin-topology.md):

1. **The same-site deployment constraint.** The cookie posture holds only while the SPA and API share
   a registrable domain. Sibling subdomains are the recommended shape; different registrable domains
   break `SameSite=Strict` outright. Deployment-blocking, not advisory.
2. **The static host's header set**, including `frame-ancestors 'none'`, which the templated meta tag
   cannot carry. Plus the production CORS allow-list value, which the PRD leaves undetermined, and the
   standard's Q27 prohibition on `localhost` origins in production.
3. **Who terminates TLS.** The PRD requires HTTPS for any real deployment (line 121) and nominates no
   component, having put all hosting infrastructure out of scope (line 21).

From [Decide the password policy and hashing parameters](07-password-policy-and-hashing.md), via the
fog patch it left behind:

4. **Password blocklist refresh obligation** — who updates the breach corpus slice, on what trigger,
   and how a change is reviewed. The source is pinned; the refresh duty is not. A blocklist that is
   never refreshed decays as a control while continuing to look like one.

From [Extract the MFA_Core recipes](22-mfa-core-recipe-extraction.md) and
[Resolve the MFA scope conflict](19-mfa-scope-conflict.md):

5. **Break-glass runbook for a TOTP-locked sole admin.** `MFA_Core` prescribes no unlock path for the
   TOTP table at all (Recipe 11 clears `PIN_USER_DETAILS` only) while mandating `lockedAt` be "cleared
   by admin", and the lockout window is a DoS lever against a known admin username. Ticket 19 named
   this runbook line as a **compensating control for deferring MFA recovery codes**, so it is not
   optional prose.

From [Decide secrets and configuration handling](24-secrets-and-configuration-handling.md) — exact
contents depend on that ticket, which is why it blocks this one:

6. **TOTP secret encryption key handling**: where the environment-supplied key comes from, and the
   yearly rotation procedure. Ticket 19 established the key must exist, must not live in the database,
   and must rotate; ticket 07 established that a peppered hash cannot be rotated, a constraint this
   inherits.

## What to decide

- **Owner and audience.** Who is this written for — a platform team, a future maintainer, an assessor?
  The audience determines whether deferral justifications belong in it or only in the ADR register.
- **Where it lives.** A single `docs/` markdown file, a README section, or part of the deferral
  register in [17](17-deferral-register-and-adrs.md)? Note the overlap: four of the six contents exist
  because a control was deferred, and ticket 17 already owns the deferral register. Decide whether
  these are one artefact or two, and if two, which one a reader hits first.
- **Whether each item is a requirement or a recommendation**, stated per item. The same-site constraint
  is deployment-blocking; the subdomain shape is a recommendation. Conflating them means a deployer
  cannot tell which lines they may ignore.
- **How the unexercised config is flagged.** Ticket 20 settled that production is a documented
  requirement only, so `Secure`, HSTS, `__Host-SESSION`, the strict CSP and the production CORS
  allow-list are all asserted by tests but never run against a real deployment. That limitation needs
  stating in one place rather than being inferred.
- **Whether anything here is verifiable.** A handover document is the weakest form of control. Decide
  which items can be converted into something enforced — a profile-scoped test, a startup assertion
  that fails fast on a missing key, a config validation — and which genuinely can only be prose.

## Done when

The document's owner, location, audience and per-item requirement-vs-recommendation status are
decided; all six contents are assigned; and it is recorded which of them remain prose versus which get
converted into an enforced check.

## Inherited from ticket 11 — three required contents, two of them recovery scenarios

[Decide the admin module, role model, and initial admin bootstrap](11-admin-module-role-model-and-bootstrap.md)
adds three named contents, and sharpens the break-glass line ticket 19 already owed.

**1. Two independent fresh-install lockout scenarios, named separately.** A newly deployed system legitimately
runs with **one** admin, because the two-enrolled-admins rule is a guard on removal and not a bootstrap
precondition. That single admin can lose access two ways, and they need different text:

- **Account lockout.** An admin cannot unlock themselves, by design. This one is *not* unrecoverable: per-account
  lockout auto-expires (standard default 20 minutes; ticket 09 confirms the number), so the sole admin is
  delayed, not locked out. The runbook's job is to say so, because the instinct on being locked out of the only
  admin account is to start editing the database. *Consolidated into the register (ticket 33): R-LCK-009. Amend the table by ID, not this list.*
- **Lost authenticator.** This one *is* unrecoverable without direct database access, since recovery codes are
  deferred and the reset endpoint requires a second enrolled admin. This is the genuine break-glass case and the
  only one the runbook must solve rather than explain.

The distinction matters more than either scenario: conflating them produces a runbook that sends an operator to
the database for a problem that resolves itself in twenty minutes. *Consolidated into the register (ticket 33): R-LCK-009. Amend the table by ID, not this list.*

**2. Promote before demote.** At exactly two admins, neither can be removed until a third exists. An operator
offboarding a leaver will hit this and read it as a bug. State the sequence: create or promote the replacement,
have them **enrol** (an unenrolled admin does not count toward the invariant), then demote or delete the leaver.

**3. The tombstone HMAC key never rotates.** Its own line in the secrets section, distinct from the TOTP key's
yearly rotation, with the reason: rotating it silently stops deleted users' usernames and email addresses from
blocking reuse. Paired with its absence behaviour — the application refuses to start rather than accept
registrations it can no longer screen.

One thing that is *not* a handover item, recorded so nobody adds it: the 30-day forced-password-change deadline
needs no operational procedure. Ticket 11 evaluates it lazily at login rather than through a scheduled job, so
there is no cron to document, no job to monitor, and no failure mode where the reaper stops running.

---

## Amendment from ticket 09 (lockout and dual rate limiting)

Four required contents, taking the total higher again. The first is the one with an outage behind it.

1. **Trusted-proxy configuration, and the startup failure that enforces it.** The app binds
   `app.security.client-ip.source` (`socket` default, or `proxy`) and `app.security.client-ip.trusted-proxies`
   (**no default**); a `@ConfigurationProperties` bean fails during context refresh if `proxy` is selected
   without an explicit list, and a `WebServerFactoryCustomizer` derives Tomcat's `internal-proxies` **and**
   `forward-headers-strategy` from that one input. The handover must say why the deployer cannot shortcut it:
   `server.forward-headers-strategy: framework` is **prohibited** because `ForwardedHeaderFilter` validates
   nothing; Tomcat's shipped `internal-proxies` default trusts **every RFC 1918 peer**, which in a shared
   private network is every neighbour; and `source=proxy` with the strategy left unset would install no valve,
   making every request's client IP the proxy's address — **one shared bucket for the entire internet**, 60
   logins per minute globally, then 429 for everyone. An outage that looks like a working config, which is
   exactly the class of thing this document exists for. Also document the name collision: our
   `trusted-proxies` maps to Tomcat's `internalProxies`, **not** its `trustedProxies`, whose matches are
   recorded into `X-Forwarded-By` rather than stripped. *Consolidated into the register (ticket 33): R-RL-007. Amend the table by ID, not this list.*
2. **Admin password reset does not lift a lock, so resolving a locked-and-forgotten-password case is two
   actions.** Ticket 09 followed standard L131 over the admin recipe, which clears the lock as a side effect
   of reset — an undocumented unlock path bypassing ticket 11's audited endpoint and its mandatory
   `unlockReason` enum. The runbook line: reset, then unlock with reason `PASSWORD_RESET_COMPLETED`. Without
   it, support reads a lingering lock as a failed reset and retries the reset. *Consolidated into the register (ticket 33): R-LCK-010. Amend the table by ID, not this list.*
3. **The rate limiter is in-memory and single-instance.** A restart resets every bucket; lockout state is on
   the user row and survives. Deployment topology is not testable, so the enforcement is a **startup check
   that fails if a clustering-related property is present**, plus this line. Also worth stating so nobody
   plans around it: Bucket4j ships **no H2 `ProxyManager` and no generic JDBC one** — only per-dialect
   PostgreSQL, MySQL, MariaDB, MSSQL, Oracle and DB2 — so "make it distributed later" is a local change at
   the call site and **untestable on the H2 baseline**.
4. **The sole-admin recovery path is the 20-minute auto-lift, and it is load-bearing for three separate
   decisions.** Extends the two fresh-install lockout scenarios this ticket already carries. WSTG-ATHN-03
   attaches a precondition to manual administrator unlock — the administrator needs a recovery method for
   their own account — and ours is the timed lift. A future change to permanent-until-admin-unlock would break
   ticket 11's bootstrap, ticket 09's NIST §3.2.2 deviation, and that WSTG precondition simultaneously. The
   handover should say so, because it will otherwise read as a tuning knob. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CFG-029. Amend the table by ID, not this list.*

---

## Amendment from ticket 23 (TOTP enrolment, step-up, and factor-reset flows)

**The break-glass runbook line is promoted to a required *control*, and gains three companions.**

**1. Break-glass becomes a control, not prose — and it has a second trigger.** Ticket 19 named a break-glass runbook
line as a compensating control for deferring recovery codes, against the **lost authenticator** case. Ticket 23 adds
a second route to the same unreachable state: its **tier-2 cumulative disable** (100 failures) can only be cleared by
`DELETE /api/admin/users/{uuid}/totp`, which itself sits behind the factor — so if every enrolled admin is disabled,
nobody inside the application can reinstate anyone.

So this needs an actual documented, environment-gated path that clears a tier-2 disable and a TOTP enrolment
**without an authenticated admin session** — a profile-gated procedure, an admin CLI, or a direct database procedure —
with its own audit trail and its own "this was used" alarm. ASVS **6.1.1 (L1)** is the hook: it requires the
documentation to state how rate limiting, **anti-automation** and adaptive response are configured and how they
prevent malicious account lockout.

**Priced honestly, because overstating it would let a reviewer discount the entry.** Every guess against a tier-2
counter costs the attacker that admin's **password**, since the verification endpoint sits behind an authenticated
session and the username is taken from the `SecurityContext` rather than the request body. Burning one admin's factor
costs one password compromise; burning both costs two — strictly more than the attack tier 2 defends against, and an
attacker holding both passwords wants in, not a dark surface. What genuinely remains is a trusted admin burning their
own counter (the other resets them) and both doing so. So this is the **same control ticket 19 already owes, with a
second trigger** — not a new compensating control for a cheap denial-of-service lever.

**2. Seed the admin's authenticator before the deployment is reachable by anyone else.** Pre-enrolment the admin
surface is closed rather than password-protected, but provisioning must be reachable password-only — which NIST SP
800-63B-4 §4.1.2.1 explicitly sanctions (bind at the lower of available and target AAL). The residual is a
**first-enroller race**: whoever reaches provisioning with the seed password first binds their own authenticator and
thereafter holds the surface with indistinguishable legitimacy. This is an operational instruction, not a control, and
it is the only mitigation.

**3. TOTP depends on the server's clock, and the tolerance is smaller than the standard claims.** STD L231 advertises
"±30 seconds of tolerance" from a ±1 window, but the replay rule rejects any matched counter ≤ `lastUsedCounter`, so
after the first success the `counter-1` and `counter` windows are permanently dead and the **effective tolerance is
+1 window only**. A device running ~25 s behind succeeds once and then fails until the server catches up. The runbook
needs: server time synchronisation as a deployment prerequisite, and the diagnostic that "my codes stopped working
after the first one" is clock drift, not a broken enrolment.

**4. The key-rotation procedure is owed here.** STD L253 mandates yearly rotation of the TOTP encryption key; ticket
19 added a key-version column so rotation runs incrementally rather than stop-the-world, and Q14 leaves the procedure
itself open and unwritten. The document owes the steps: mint a new version, re-encrypt each row under it, retire the
old version, and the assertion that no row remains on a retired version. Note the envelope now binds the key version
**inside** the ciphertext as part of ticket 23's context prefix, so a row still on an old version fails the prefix
check rather than decrypting silently — which gives the procedure a verification step it would not otherwise have.

**5. One line for the alarm list:** a context-prefix mismatch on TOTP secret decryption only ever trips when database
rows have been moved between users or replayed across key versions. It is not a decrypt error and should not be
triaged as one. *Consolidated into the register (ticket 33): R-MFA-020. Amend the table by ID, not this list.*

---

## Amendment from ticket 24 (secrets and configuration handling)

Item 6 of this ticket's required contents is now specified.
[Decide secrets and configuration handling](24-secrets-and-configuration-handling.md) settled the
**policy** — which keys exist, where they come from, what happens when they are absent, and which can
rotate. This document owes the **procedures**. Six additions, the first of which is an outage waiting to
happen.

**1. The file is the source of record and environment variables override it.** Secrets arrive by
`spring.config.import=optional:configtree:/run/secrets/` with environment variables as a fallback — but
Boot's documented property-source order puts OS environment variables *above* config data, and imported
files are config data. So a leftover `APP_MFA_TOTP_ENCRYPTION_KEY` in a shell profile, systemd unit or
container spec **silently wins over the mounted file that was just rotated**. Nothing fails; the
application starts on the old key. For the TOTP key that surfaces as decryption failures after a rotation
that appeared to succeed; for the tombstone key it surfaces as nothing at all. An operator who believes
the file wins will debug in the wrong place, which is exactly what this document exists to prevent. *Consolidated into the register (ticket 33): R-CFG-022. Amend the table by ID, not this list.*

**2. Three rotation procedures, not one, because the three keys rotate differently.**

- `app.mfa.totp.encryption.key` — **yearly**, incremental. Mint a new version, re-encrypt each row under
  it, retire the old version, assert no row remains on a retired version. The key version is bound
  *inside* the ciphertext as part of ticket 23's context prefix, so a row still on an old version fails
  the prefix check rather than decrypting silently — which gives the procedure a verification step it
  would not otherwise have.
- `app.security.hmac.tombstone.key` — **forward only**. New tombstones are written under the newest key;
  candidates are verified against every retained version. Versions **accumulate and never retire**,
  because we hold no plaintext to re-derive them. State that plainly: an operator who prunes old versions
  to tidy up silently stops deleted users' addresses from blocking reuse.
- `app.security.hmac.log.key` — **freely**, and it is the only key here whose rotation is free of user
  impact. But **a rotation leaves no marker in the log stream itself**: the hash values simply stop
  correlating, and the only record that it happened is the startup fingerprint line. One line for whoever
  analyses logs across a restart.

**3. Key generation, with a named command and a named trap.** Nothing in the application can verify a key
is random — ASVS 11.5.1 (L2) is satisfied by procedure, and the only enforced check rejects an
all-printable-ASCII key, which catches Base64-encoding a string rather than generating bytes. So the *Consolidated into the register (ticket 33): R-CFG-008. Amend the table by ID, not this list.*
command belongs here:

```bash
openssl rand -base64 32
```

```powershell
$b = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
[Convert]::ToBase64String($b)
```

**`Get-Random` must not be used** — it is backed by a non-cryptographic generator and it is the obvious
thing a Windows operator reaches for. All three keys are exactly 32 decoded bytes, 44 Base64 characters;
16 bytes would put us on ASVS Appendix C's **Legacy** list rather than merely being weaker.

**4. The startup fingerprint is the diagnostic for three separate failures**, so the document should say
what it is for rather than leaving it as log noise: a key that is present but wrong, a key shadowed by an
environment variable (item 1), and a log-key rotation boundary (item 2). One INFO line per key carrying
the property name, the key version, and 8 hex characters of a domain-separated digest over the decoded
bytes. Comparing that value across two environments is the only way to confirm they hold the same key. *Consolidated into the register (ticket 33): R-CFG-022. Amend the table by ID, not this list.*

**5. Datasource residuals, stated as accepted rather than left to be found.** The H2 credential is
**declared non-secret** — it guards a file its holder can already read. Least privilege was attempted and
declined: [h2database#2846](https://github.com/h2database/h2database/issues/2846) reports that DML grants
confer DROP and that only admin users may own a schema, so a "DML-only" runtime user would be a control
that looks enforced and is not. ASVS **13.2.1 (L2)** and **13.2.2 (L2)** are accepted failures scoped to a
dev-only embedded database; **13.2.3** is satisfied by a non-default username and password. Also worth a
line: an unset datasource URL does **not** fail — Boot resolves an ephemeral in-memory H2, Flyway migrates
it successfully and `ddl-auto: validate` passes, so all three layers agree nothing is wrong. A startup
check converts that into a failure; the document should say why the check exists so nobody removes it. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CFG-031. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-DATA-015. Amend the table by ID, not this list.*

**6. Who performs the frontend build, and what that makes the build machine.** `VITE_*` values are baked
into the bundle at build time, and with no CI in scope `npm run build` runs on a developer's machine. So
the deployed bundle's API origin *and* its CSP come from whatever `.env.production` existed locally at
build time: **the build environment is part of the security configuration surface.** Two consequences —
changing the API origin is a **rebuild, not a configuration change**, and **build-once-deploy-many does not
hold**, so any process promoting an artefact from staging to production is invalid. A post-build assertion
covers the riskiest step (no `%VITE_` placeholder and no `localhost` in `connect-src` in the emitted
`index.html`), but who runs the build is procedural and lands here. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-BLD-009. Amend the table by ID, not this list.*

**And one requirement this ticket is asked to adopt because it has no owner anywhere on the map: ASVS
13.4.1 (L1).** Source-control metadata — `.git`, `.svn` — must not be deployed, and must be unreachable
both externally and by the application itself. It is **L1**, inside the mandatory target, and it lands on
both artefacts: the static bundle directory and the jar. Ticket 24 found it while building the V13
register; ticket 20 is resolved and cannot take it, so it belongs here unless the reader prefers a ticket
of its own. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-BLD-004, T-BLD-005. Amend the table by ID, not this list.*

---

## Inherited from ticket 13 (audit event catalogue)

[Build the audit event catalogue](13-audit-event-catalogue.md) adds **five required contents** to this
document, four of them compensating for something declined with written justification. All five are
obligations on whoever deploys this, not controls the application can hold.

**1. The centralised-platform retention configuration.** The Standalone standard §3.3 requires audit
events retained ≥ 90 days durably; the logging standard §3.5 states the TTL is enforced by the central
log platform's index lifecycle policy, not the application. Ticket 13 builds the in-app half — a
dedicated rolling file appender, daily pattern, `max-history: 90`, **deliberately no `total-size-cap`**
— so the deployer owes the platform-side policy. Record the asymmetry plainly: an admin-unlock
accountability record expires on a policy nobody on this project controls, while the tombstone it
explains is retained indefinitely, and since ticket 12 added no unlock-reason column, **the audit event
is the only record of why an account was unlocked**. *Consolidated into the register (ticket 33): R-AUD-011. Amend the table by ID, not this list.*

**2. Disk monitoring for the audit appender.** `total-size-cap` is deliberately omitted because a cap
silently deletes the oldest archives while every configuration value still reads compliant. The trade is
a disk-full path — which is §3.4's "audit logging failed" condition, and the point at which operations
occur without an audit record. The application raises it (an ERROR with `error_follow_up_action: true`
plus a Logback `<statusListener>`); somebody has to be watching. *Consolidated into the register (ticket 33): R-AUD-010. Amend the table by ID, not this list.*

**3. ASVS 16.4.2 and 16.4.3, both recorded F with no compensating control.** A rolling file the
application itself owns and rewrites is neither tamper-proof (16.4.2) nor a logically separate system
(16.4.3, whose stated aim is that a breach of the application does not take the logs with it). stdout is
the route to 16.4.3 and is emitted in every profile, but nothing consumes it in this build. So the
deployer owes: a forwarding agent, WORM or signed storage, and least-privilege read access. Note for the
reviewer that **V16 contains no L1 requirement at all**, so neither failure bears on the declared ASVS
L1 claim — but both are the first thing a logging reviewer looks for. *Consolidated into the register (ticket 33): R-AUD-012, R-AUD-013. Amend the table by ID, not this list.*

**4. Who may read the audit log.** ASVS 16.1.1 asks the inventory to record how access is controlled.
In this build the audit file sits on disk with the process's own permissions and the stdout copy is
visible to anyone who can see the terminal. This is stated as **unmitigated**, deliberately: ticket 13
declined to assert file permissions from the application because such a check would pass on the one
platform we run and mean nothing on the platform that matters — ticket 11's inert
`ImmutableSecurityHandler` precedent. In production the stream goes to a collector, and that is where
access control belongs. *Consolidated into the register (ticket 33): R-AUD-012. Amend the table by ID, not this list.*

**5. The reset-link logger, and why it is a deployment gate rather than a footnote.** Ticket 10
established that the PRD-mandated `EmailService` stub logs the link it would send, so anyone who can read
application logs can take over any activated account — contained only by ticket 19's TOTP, and only for
confidentiality. Ticket 13 confines it to a **`dev`-profile-only, non-audit logger** with three
enforcement points: a prohibited-configuration entry in ticket 24's refresh-phase validator, an
`ApplicationReadyEvent` check on the *effective* level (because `LOGGING_LEVEL_…=DEBUG` binds to no
`@ConfigurationProperties` class and passes every property validator), and `/actuator/loggers` absent or
read-only outside `dev`. The handover owes the statement that **a real mail transport must replace the
stub before any deployment carrying real accounts**, and that the three checks above are what stand in
for it until then.

**Already-owed contents that ticket 13 sharpens rather than adds:** the break-glass runbook for a
TOTP-locked sole admin now has a named trigger — the **factor tier-2 disable** row at ERROR/critical —
and the TOTP decrypt **context-prefix mismatch** row is the only detector of moved or swapped TOTP rows,
so the runbook needs a response for it. Both are audit rows, which means the runbook's entry condition is
observable rather than assumed. *Consolidated into the register (ticket 33): R-MFA-020. Amend the table by ID, not this list.*

---

## Inherited from ticket 21 (observability signals)

[Decide the observability signals and monitoring surface](21-observability-signals.md) is resolved and owes
this document **one section**, not five lines: *"what this application cannot observe about itself."*

Consolidated deliberately. Three of the five items are the same shape — a signal the application emits
correctly into a void — and five separate bullets would be graded as five gaps rather than one deployment
boundary. Each item carries the control it compensates, the finding left open if it is skipped, and an
**acceptance check**, because an obligations list with no way to prove discharge is read and skipped.
Severity is **not** flattened: item 1 is the High that ticket 18 carries, the rest are residuals.

1. **Alerting and rate computation**, for both the rate-above and rate-below classes. This is the half of
   **lm-16** the application cannot close, and lm-16 is a Level-2 control, so a WARN here is a **High**.
   Acceptance: the thresholds in ticket 21's §6 table exist as rules in whatever consumes the stream. *Consolidated into the register (ticket 33): R-OBS-004. Amend the table by ID, not this list.*
2. **External absence-of-logs detection.** The one signal that cannot be in-process, because a process that has
   stopped logging cannot report that it has stopped. Compensates the third half of §3.4's
   alert-when-audit-logging-fails. Acceptance: an alert fires when the application stops emitting.
3. **Re-enable `management.endpoint.health.probes.enabled` and `management.health.db.enabled` together**, with
   `probes.add-additional-paths` left `false`. Both were disabled on consumer-absence, and a container
   orchestrator is the consumer that makes them worth having. Acceptance: both probe paths return 200.
4. **The OTLP collector — two settings, not a URL.** Flip `management.otlp.metrics.export.enabled`, supply
   `…export.url` under required-property validation, and know that a Docker Compose or Testcontainers
   service-connection bean **overrides both** because the adapter reads the `ConnectionDetails` bean and never
   reads the property. `…export.headers.*` is a credential sink and is ticket 24's fourth secret. Acceptance:
   ticket 21's absent-bean assertions, inverted.
5. **Post-deployment dependency re-scan.** The Maven `verify` gate covers pre-deployment only and cannot see a
   CVE published after the last build — CVE-2026-40976 landing on Boot 4.0.x is this project's own example.
   Cadence: **monthly, and on any dependency change.** Acceptance: a scan report dated inside the cadence.

**Plus one recomputation obligation**, which belongs with the person who can invalidate it rather than in the
register: the `management.health.diskspace.threshold` value is `daily_volume × lead_days` with `lead_days = 2`,
a published judgement. The deployer sizes the mount, so **the deployer owns recomputing it** whenever the mount
is resized or the audit log location moves — at which point the number stays arithmetically valid and becomes
semantically wrong, which is the failure mode no register entry would catch.

**And one framing note for the whole document.** Ticket 21's fog patch establishes that this application has no
mechanism that could ever learn a traffic baseline — no scheduler, no time-series store, no persistence of
prior rates, each a settled decision rather than an omission. So the deployer inherits not merely an alerting
stack but a **tuning obligation on numbers chosen blind**. Say that here, because it is the honest version and
because a reader who thinks the thresholds were calibrated will not revisit them.

---

## Inherited from ticket 09 (§R, the ticket 21 reopening) — a seventh required content, and the first one that is an ownership question

**Your existing break-glass line covered a TOTP-locked sole admin. There is now a second, independent way to reach the
same dead end, on the password axis, and closing it produced a tool you have to own rather than a procedure you have
to describe.**

### 1. The operator rebinding runner — required content, with its ownership question

Implementing NIST SP 800-63B-4 §3.2.2's cap creates a state that does not auto-lift and is cleared only by rebinding.
Outside `dev` there is **no self-service route to it**: ticket 13 confines the stubbed reset link to a `dev`-only
logger, so a reset request produces no deliverable artefact at all. Ticket 09 §R.3 therefore builds a command-line
runner — `--rebind=<username>`, scope `password|totp|both`, with a **batch form** — which invalidates the hash, mints
a single-use token through ticket 10's machinery, and prints it once to `System.out`.

What you owe is four things, and the third is the one that makes this different from your other lines:

1. **The procedure**, including the batch form. Ticket 09 §R.2 quantifies an event of **hundreds of accounts** at 36
   permanent disables per hour from a single source, so a one-at-a-time procedure fails exactly when it is invoked.
2. **The output warning**: the token is printed to `System.out` once and must not be captured into any log, shell
   transcript or CI artefact. It is a plaintext credential on a channel that exists in every profile.
3. **An ownership statement, not just a procedure.** This needs **deploy-level access to the running system**, a
   strictly higher bar than admin HTTP access. If the sole admin holds no shell and the platform team is unreachable,
   the recovery **exists on paper only** — and ticket 11's bootstrap decision (one seeded admin) now rests on this
   tool existing and being reachable by someone. Name who. *Consolidated into the register (ticket 33): R-RUN-003. Amend the table by ID, not this list.*
4. **The reserved-name note**: the bootstrap admin username must not be a guessable reserved name, because it is the
   targeting step for the attack above. Enforced by a denylist, but the deployer chooses the name. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-ADM-022. Amend the table by ID, not this list.*

### 2. Two residuals to state plainly, because ticket 18 will read the ADR either way

- **Roughly 100 unauthenticated requests permanently disable one account's password authenticator**, and one host can
  sustain ~36 such disables per hour at its permitted rate. The cardinality limiter reduces that to ~5/hour per
  source — **7×, and defeated by IP rotation**. The escalating lockout ladder buys ~10 hours of lead time between the
  alert and permanence, and is throughput-neutral. *Consolidated into the register (ticket 33): R-LCK-011. Amend the table by ID, not this list.*
- **ASVS 6.1.1 (L1) is pass-with-note conditional on this document existing and on the runner being operable.** Its
  failing clause, if the conditions are not met, is the final one — documentation making clear how the controls
  "prevent malicious account lockout". That is the sharpest instance yet of the pattern you already carry: a
  compliance verdict justified on the grounds that this document exists. *Consolidated into the register (ticket 33): R-LCK-003. Amend the table by ID, not this list.*

### 3. Lockout behaviour the support desk will misread

The lockout duration now **escalates 20 / 40 / 60 minutes** as an account approaches the cap, so "wait twenty minutes"
is wrong advice for a user under sustained attack, and a lingering lock is not a failed reset. Ticket 09's existing *Consolidated into the register (ticket 33): R-LCK-009. Amend the table by ID, not this list.*
handover line — admin password reset does **not** clear the lock, only redemption does — is unchanged and now matters
more, because the same is true of the cap. *Consolidated into the register (ticket 33): R-LCK-010. Amend the table by ID, not this list.*

### 4. Whether any of this can become an enforced check

Your ticket's own ambition. Of the four items in §1: the output warning **is** enforceable (asserted on ticket 13's
negative list and as a ticket 24 prohibited-configuration entry); the reserved-name note **is** enforceable (ticket
11's refresh-phase validator); the batch procedure is code you now have rather than prose. **Only the ownership
statement cannot be converted** — no check can assert that a human with shell access is reachable. That is worth
saying explicitly, because it is the residue after three of four items were converted. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-ADM-022, T-AUD-027. Amend the table by ID, not this list.*

---

## Answer

**One generated inventory rendered twice, a three-field row schema, a `verify`-phase gate that fails when the
renderings drift, deployment-sequence ordering derived from the application's own failure points, and a
break-glass path that stops being permanently unenumerated and becomes a conditional pass with one named
prerequisite.** The document is owned by the implementing repo, written for an assessor first and a maintainer
second, and it names **roles rather than people** — with every vacancy encoded as an *unmet acceptance check*
rather than a satisfied row, because that is the only shape in which "nobody is named" cannot read as discharged.

The count this ticket was framed against was wrong by an order of magnitude. Its header says "all six contents";
an exhaustive extraction across `issues/` found **47 verdicts** resting on this document or on deployer action
(ASVS, IM8, ARC, NIST and the org standards) plus **nine obligations with no owner anywhere**. Five distinct
**L1** requirement IDs are among them — 6.1.1, 6.3.1, 6.3.2, 6.4.1, 13.4.1 — so this is not an L2-and-below
artefact that ticket 18 can grade leniently.

### 1. Owner, audience, and the actor gap

Assessor primary, maintainer secondary, owner is the repo. The assessor ranks first on ticket 01's evidence, not
on preference: **as-10 is WARN/FAIL in the top severity band (2 | 2) and dp-3 is WARN**, both "survivable only via
documented TLS-terminating topology — which makes the HTTPS handover note real evidence." No platform team
exists to hand this to, because the PRD puts hosting infrastructure out of scope and ticket 20 settled production
as a documented requirement rather than an environment.

The extraction's sharpest result is that the most repeated gap is **not a missing procedure but a missing named
actor** — who terminates TLS, who operates the rebinding runner (this ticket's own line 403 ends "Name who."),
who refreshes the two word lists. The document therefore names roles and states that each is unfilled in this
build, because a named-but-fictional owner is worse evidence than a declared vacancy. Encoding, which is the part
that matters: a vacancy is `responsibility: deployer`, `priority: blocking`, acceptance check *"the role is filled
and its holder is reachable"* — **an unmet check, never a fourth responsibility value**. One normative obligation *Consolidated into the register (ticket 33): R-RUN-003. Amend the table by ID, not this list.*
attaches: §4.6's repudiation clause, "The notification SHALL provide clear instructions, including contact
information, in case the recipient repudiates the event associated with the notification." §4.1.2.2's
binding-mishap clause does **not** join it — its SHALL attaches to providing "clear instructions", with a contact
address appearing inside an "e.g." beside an in-session button and qualified "as appropriate". One firm
contact-address SHALL, not two.

### 2. One source, two renderings, one gate

Ticket 17's register and this document are populated from **the same 47 rows**, so two hand-maintained documents
over one source is precisely the drift that four binding requirements punish. A single extracted table is the
source; ticket 17 renders the **compliance view** (requirement ID, level, verdict, deviation, residual) and this
ticket's artefact renders the **operational view** (what to do, in what order, how to prove it). The compliance
rendering carries a **per-row anchor into the operational rendering's procedure**, which discharges ticket 17's
own constraint that "a split must not leave the register itself split across artifacts a reviewer has to
reassemble" — free, because it is generated.

**A generated inventory with no gate is a transcription with extra steps.** Nothing would make anyone re-run the
extractor, and ticket 24 established there is no CI: `npm run build` runs on a developer's machine. So the
extractor runs in the Maven **`verify` phase and fails when the committed renderings differ from regenerated
output**, on the precedent the map already set for OWASP Dependency-Check for exactly this reason. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-BLD-006. Amend the table by ID, not this list.*

The binding argument for generation is **not** house style, and the version this ticket was first given was too
broad. **6.3.1 (L1) is not a general drift detector** — verbatim, it is scoped to controls preventing "attacks
such as credential stuffing and password brute force ... implemented according to the application's security
documentation", making it 6.1.1's implementation twin. It therefore binds **ticket 09's lockout and throttling
items specifically**, at L1. The generalisation exists as separate per-domain IDs, and the textual ones are:

| ID | Level | What the documentation is the reference point for |
|---|---|---|
| **6.3.1** | **L1** | anti-credential-stuffing / anti-brute-force controls |
| **6.1.2** | L2 | the documented context-specific word list must exist |
| **6.2.11** | L2 | that word list must actually be used |
| **16.2.3** | L2 | logs go only to destinations recorded in the inventory |
| **16.3.3** | L2 | the security events the documentation defines are actually emitted |

**13.2.4 / 13.2.5 (L2) are cited as 13.1.1's enforcement companions, not as members** — they require an allowlist
to exist and reference no documentation at all, so counting them would let a reviewer checking them find no
documentation dependency and conclude the family was padded. 13.2.6 and 13.3.4 are **L3** and support rather than
carry. *Consolidated into the register (ticket 33): R-BLD-006. Amend the table by ID, not this list.*

### 3. The row schema — three fields, because two lose a distinction

`responsibility` = `application` | `deployer` | `shared` | `none` — **who acts**.
`status` = `enforced` | `enforced-elsewhere-cited` | `asserted-by-test` | `procedural` | `unmitigated` —
**scoped explicitly to the application's own enforcement, nothing else**.
`priority` = `blocking` | `required` | `recommended` — **absent if and only if `responsibility` is `none`**.

Three fields rather than four ranked classes, because a four-item ordered list is read as a severity ranking, so
the limitation class lands as "least urgent obligation" — the exact misreading it was introduced to prevent. And
three rather than two because FedRAMP's shared-responsibility artefacts pair **Responsible Role** with
**Implementation Status** and **Control Origination** per control, holding severity in a *separate* artefact (the
POA&M) — independent precedent that the shape is right and that ranking does not belong in this rendering. Not a
framework being adopted; only evidence that the vocabulary already exists.

The scoping sentence on `status` is load-bearing. Without it, ticket 13's audit-log read access reads
`unmitigated` + `deployer` as one statement made twice; with it, `status` says the application enforces nothing
and `responsibility` says the deployer acts, which are different facts.

Two schema rules the worked rows forced:

- **Every row carries an acceptance check or the sentinel.** Not "requirement-and-above" — row 4 has no `priority`
  and still carries a check, and a rule that excluded it would put an off-schema row in the one document that
  cannot afford one. Sentinel value for the irreducible rows: *"none possible — attests a named role is filled and
  its holder reachable."*
- **The rendering carries a deployment-assumption header.** `priority` is absent iff `responsibility` is `none`,
  and row 4's reopening trigger flips `responsibility` to `deployer` the moment a real deployment exists. So the
  grid is a snapshot against an assumption, and that assumption belongs in a header rather than being implied by
  one row's trigger — otherwise ticket 18 reads an empty `priority` cell as an omission. *Consolidated into the register (ticket 33): R-OPS-005. Amend the table by ID, not this list.*

### 4. The four worked rows — a schema spike, not a transcription

Chosen because each broke a previous version of the design.

**Audit-log read access.** `deployer` / `unmitigated` / `required`. IDs 16.1.1 (L2) access-control clause,
16.4.2 (L2) **F**, 16.4.3 (L2) **F**. Status reason: ticket 13 declined to assert file permissions from the
application, on ticket 11's inert `ImmutableSecurityHandler` precedent. Acceptance check: a least-privilege read *Consolidated into the register (ticket 33): R-AUD-012. Amend the table by ID, not this list.*
policy on the collector. Anchor: sequence §6. *Proves the two fields are orthogonal.*

**Who terminates TLS.** `deployer` / `procedural` / `blocking`. IDs as-10 (MUST, 2 | 2), dp-3, PRD line 121.
Status reason: no application check can assert an upstream terminator exists. Acceptance check: **the sentinel**.
Anchor: sequence §4. *Proves the sentinel reads as a decision rather than an omission.*

**Break-glass access restoration.** `shared` / `procedural` / `blocking`, prerequisite **mail transport**. IDs
NIST §4.2.2.2 conditional pass, §4.2.3 notification SHALL failing, ASVS 6.4.4 (L2) satisfied-by-parity, 6.5.6
(L3) supporting, 6.4.1 (L1) on the token. Acceptance check: **the rehearsal** plus the documented risk analysis
covering the interim. Anchor: sequence §7. *Proves a row can carry a conditional pass with a named prerequisite
instead of a permanent gap.*

**Unexercised production config.** `none` / `asserted-by-test` / *(no cell)*. Covers `Secure`, HSTS,
`__Host-SESSION`, the strict CSP and the production CORS allow-list. Acceptance check: the assertions exist and
pass; no runtime evidence is obtainable by anyone. Reopening trigger: `responsibility` becomes `deployer` on any
real deployment. *Proves the limitation class cannot be misread as an obligation, and that the two fields are
independent in both directions — this row is well enforced with nobody responsible.* *Consolidated into the register (ticket 33): R-CFG-023. Amend the table by ID, not this list.*

### 5. The organisation axis — deployment sequence, derived from failure points

Severity appears nowhere in this rendering; it lives in `priority` in the compliance one. **An earlier draft put
mail transport first on a "clears the most failing SHALLs" argument, which is a severity ranking smuggled into the
axis that excludes severity.** Corrected, the sequence comes off the application's own failure points:

1. **Keys, with the generation commands** — the application refuses to start without them.
2. **Trusted-proxy configuration** — fails context refresh if `proxy` is selected with no list; also pre-boot.
3. **Time synchronisation** — before the first TOTP enrolment, and it discharges 16.2.2's synchronisation half.
4. **Headers and origins** — before the SPA is served.
5. **Mail transport** — gates real accounts, and now also gates recovery compliance (§6 below).
6. **Log forwarding and retention** — before the first real audit event.
7. **Recovery rehearsal.**

Step 7 is not "read the runbook". Ticket 09 §R.3's whole point is that an unexercised procedure exists on paper
only, so the step is **rehearsing it on the deployed topology before real accounts exist**: run the rebinding
runner, clear a tier-2 disable through the break-glass path, confirm the output warning holds. That is
simultaneously a sequence item and the acceptance check for the two rows that otherwise carry only the sentinel,
which converts the weakest evidence in the document into the strongest.

### 6. Break-glass: the compliance construction, and the limb we do not stand on

The framing question — is this §4.2 subscriber account recovery or §4.1.2 CSP-side binding — **closes for §4.2**,
and neither reading yields an exemption. §4.2's scope is set by the situation ("recovering from losing control of
the authenticators that are needed to authenticate at a desired AAL"); its SHALLs are already addressed to the
CSP with the subscriber as acting party, so being the CSP is not an exemption but the addressee; and the converse
case is explicitly carved out — replacement of a forgotten password where the subscriber can still authenticate
with another authenticator "is considered to be the binding of a new authenticator", which is not our case.
Under the binding reading what attaches instead is authentication **at the account's maximum available AAL**,
which is exactly what someone who has lost their authenticator cannot supply.

**An earlier draft of this answer routed break-glass through §4.2.1's application-specific-method MAY while
simultaneously filing §4.2.1 against §4.2.2.1 as a standards contradiction. That banked both limbs of one clause
and is withdrawn.** Read in full, **Reading B (general/specific — an application-specific method must still land
inside one of the four classes) is better supported**: §4.2.2.1 and §4.2.2.2 are unconditional SHALLs over closed
lists, §4.2.2 contains no carve-out for application-specific methods, §4.2's own introductory summary omits agent
interaction, and §6's Table 5 treats human-assisted recovery as a threat to be avoided rather than a sanctioned
equal. Confidence is **~70%**, and the residual rests on the unexplained word "alternative" and on §4.2.2's
silence. **Adopted reading recorded explicitly, because the alternative would have produced a compliance route and
the next reader will otherwise find that clause and think it was missed.** Nothing is filed against ticket 02;
an editorial clarity note is the most this warrants. *Consolidated into the register (ticket 33): R-CRED-019. Amend the table by ID, not this list.*

So the route runs **inside the enumeration**. §4.2.2.2's second option is "One recovery code from the set (i.e.,
saved, issued, and recovery contacts) plus authentication with a single-factor authenticator that is bound to the
subscriber account", and **a password qualifies**: §3.1.1 states "A password is 'something you know'", the
glossary defines single-factor as requiring only one factor, and no exclusion exists — no different-factor rule
and no bar on the factor the subscriber still holds. Two texts affirmatively support it. §4.2's introduction
contemplates recovery "perhaps in conjunction with using an authenticator that is still available to the
subscriber bound to their subscriber account", and NIST restricts the type where it means to (§4.4: a backup
authenticator "SHALL be a password or a physical authenticator") while §4.2.2.2 says only "a single-factor
authenticator". The contrast is drafted.

Ticket 09 §R.3's runner already mints a single-use token through ticket 10's machinery. **What takes it out of
class 2 is printing it to `System.out` instead of delivering it** — and the channel is permitted, explicitly:
§3.1.3.1, immediately after prohibiting email for out-of-band authentication, carves out that codes "issued as
recovery codes (see Sec. 4.2.1.2) are not authentication processes and not affected by the above prohibition",
with a 24-hour cap when emailed. The asymmetry against §4.1.2.2's binding code — "SHALL NOT be communicated over
any insecure channel (e.g., email)", single-use, 10 minutes — is real and deliberate, and it is a second reason
the binding reading is the wrong one: it forbids the channel that makes the remedy possible.

**Chosen mitigation for the correlation this opens (branch 2 of three).** §3.1.3.1 lists among email's weaknesses
"Access using only a password", so issued-code-plus-password can collapse to one secret if the recovery mailbox is
protected by the same password — on the admin account, which is the entire reason MFA is in scope. Two candidate
fixes were rejected and one taken:

- **Recovery contacts (rejected).** §4.2.1.3 requires the CSP to "allow the subscriber to specify one or more
  addresses of trusted associates" and to "provide methods for subscribers to view and manage recovery contacts".
  Ticket 19's second admin is **system-designated, nominated by nobody, with no management surface** — so nominating
  it would satisfy the *shape* of option 1 without satisfying the class it claims, while adding a contact-list
  feature and leaving the transport dependency unchanged.
- **Saved plus issued (deferred to ticket 19, not taken here).** Strongest option and genuinely feasible —
  §4.2.1.1 imposes no channel constraint at all and its issuance clause is scoped "At enrollment", which is
  exactly when our admin holds a password-plus-freshly-confirmed-TOTP session. But it **adds an authenticator
  lifecycle feature**, and ticket 25 owns a document, not the auth design. Raised as a reopening trigger on
  ticket 19 instead. *Consolidated into the register (ticket 33): R-MFA-021. Amend the table by ID, not this list.*
- **Taken: option 2 plus a recovery address distinct from the login address.** This adds *nothing* beyond what the
  route already obliges — §4.2.1.2 independently carries "CSPs SHALL allow the subscriber to establish at least two
  recovery addresses" and "A recovery address SHALL be established only after the subscriber provides the correct
  confirmation code" — so it converts an already-owed obligation into the mitigation. Weaker than two independent
  codes (one secret plus one mailbox), so **the residual survives as a documented choice rather than an unexamined
  one**, which is the whole difference this register exists to record. *Consolidated into the register (ticket 33): R-CRED-025. Amend the table by ID, not this list.*

Grade, stated precisely: the **invalidation half** is satisfied CSP-side under §4.3 and §4.5 and needs no argument.
The **access-restoration half** is a **conditional pass with mail transport as its single named prerequisite**;
until that exists the shell runner is the only route and is an **accepted failure with the same single cause** —
a target rather than a permanent gap. The §4.2.1 risk analysis is still written, as the **interim compensating
artefact**, not as the compliance route. §4.2.3's notification SHALL ("In all cases, account recovery SHALL cause
a notification to be sent") remains **failed**, cause: no transport outside `dev`. *Consolidated into the register (ticket 33): R-CRED-022, R-RUN-001. Amend the table by ID, not this list.*

Declining recovery codes is **not** a NIST failure, which is worth stating because the map has twice implied
otherwise: §4.2.1.1's issuance clause is a **SHOULD**, scoped to "a CSP that supports this recovery option". What
is required is §4.2.2.2's two-element combination, and we meet it.

### 7. The adopted intake set

**Adopted here.** The four actor gaps (TLS terminator, rebinding-runner operator, blocklist refresher, plus
§4.6's repudiation contact); 16.1.1's access-control acceptance check; **ASVS 13.4.1 (L1)**, which ticket 24 found
unowned — two assertions, no decision left, and keeping it inside the artefact ticket 18 reads; and **ASVS 13.1.1
(L2)**, whose inventory is operational rather than architectural (diagrams are L3 13.1.2/13.1.3, so refusing
pm-6's diagram costs nothing here) and whose third limb — a user-supplied external location the application then
connects to — is **already discharged by ticket 10's three negative assertions** against `Host`, `X-Forwarded-*`
and a caller-supplied body field, giving the nil finding a citation instead of an assertion. Completeness is
13.1.1's whole test, so the list runs past mail/OTLP/log-forwarder/TLS-terminator to the database, **NTP** (newly
in scope via sequence step 3), registries in the runtime path, and probe callers. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-BLD-004, T-BLD-005. Amend the table by ID, not this list.*

**Required content 4 is split, because there are two lists and it named one.** The breach-corpus slice (6.2.4 L1,
6.2.12 L2) and the **context-specific word list** (6.1.2 + 6.2.11, both L2, tracked as owed by tickets 07 and 10
and ownerless until now) decay identically and both need a refresh trigger.

**Refused.** pm-6's network topology and data-flow diagram — architecture documentation, ticket 15's data-flow
work plus ticket 17, not an operational obligation. ac-3 / ac-4's scheduled halves — already the account-hygiene
deferral on the map's out-of-scope list.

**Corrected rather than adopted.** Ticket 24 records 13.4.5 (L2) **S** partly on an "authenticated base path",
while ticket 21 records `/actuator/health` as `permitAll`. 13.4.5's text is "not exposed unless explicitly
intended" and ticket 21 *did* intend it, so **the verdict stands and only the clause is wrong**.

### 8. Where verification reversed a position

Five external facts checked out and **four were wrong in the direction that favoured the argument being made**,
which is this map's recurring pattern and the reason the rule exists.

- **The Appendix C key-wrapping strengthening is declined.** The mandate is verbatim — "AES-256 MUST be used for
  key wrapping, following [NIST SP 800-38F]" — but Appendix C's approved key-wrapping modes are **KW and KWP
  only**, and our TOTP secret uses **AES-GCM**. Invoking it to mandate 32 bytes we already have would volunteer a
  *mode* finding on the one crypto path ticket 19 kept inside Spring Security after CVE-2026-47842. The carve-out
  is also wider than claimed: "AES-192 and AES-128 MAY be used if the use case demands it, but its motivation MUST
  be documented" — two conditions, and AES-192 is graded **Approved**, so ticket 24's Legacy argument implies a
  false floor and is amended rather than replaced.
- **The `git.properties` exposure path does not exist, and prohibition alone would be the wrong artefact.**
  Ticket 21 line 246 already decided we generate neither `git.properties` nor `build-info.properties`, and its
  config is `denyAll` on every endpoint but health. But **four sentences later the same ticket recommends adding
  the build-info file, "which is mild"** — so the trigger is written into the argument it invalidates. And a
  refresh-phase property validator **cannot see a Maven plugin addition**, so the enforcement surface is the jar
  content test 13.4.1 already needs: both filenames join its list, one line, `enforced` rather than `procedural`.
  This is **13.4.6 (L3) plus 13.4.5 (L2)**, not 13.4.1 (L1). *Consolidated into the register (ticket 33): R-BLD-014, R-BLD-015. Amend the table by ID, not this list.*
- **13.4.1 is disjunctive** — "either without any source control metadata ... or in a way that these folders are
  inaccessible both externally and to the application itself" — and both ticket 24 and this ticket render it
  conjunctively, which is stricter than the source. We take the first branch.
- **The 15-character floor is a SHALL barred two independent ways, not a conservative default.** §3.1.1.2:
  "Verifiers and CSPs SHALL require passwords that are used as a single-factor authentication mechanism to be a
  minimum of 15 characters in length", with the relaxation a **MAY-to-shorten bounded by a SHALL-eight** and
  scoped to passwords "only used as part of multi-factor authentication processes". Regular users are password-only
  per ticket 23 §8, so 15 is mandatory for that population outright; and it is mandatory for admins the moment the
  password serves as the §4.2.2.2 companion. Recorded so a future "relax to 8, admins have MFA" proposal dies on
  the user population before it reaches the recovery argument. 15 is a **floor a CSP may raise** — a higher minimum
  is not a composition rule, and composition rules are what §3.1.1.2 SHALL NOT impose. *Consolidated into the register (ticket 33): R-CRED-023. Amend the table by ID, not this list.*
- **Citation hygiene, three items that would each fail a reviewer's check.** The binding-code clauses are
  **§4.1.2.2 "Binding Across Endpoints"**, confirmed three ways (heading levels, the document's own hyperlinked
  cross-references, and Appendix E naming "Section 4.1.2.2: Adds requirements for binding authenticators that are
  not connected to an endpoint"); "binding an external authenticator" is lead-in wording, not a title, and that
  lead-in's second cross-reference is **Sec. 4.2**, not Sec. 4.1.2. **"Binding to a Subscriber-Provided
  Authenticator" is §4.1.3, not §4.1.2.3** — §4.1.2 has exactly two children. And **§4.1.2.2 carries its own
  112-bit figure** (binding-code length), unrelated to the 112-bit storage boundary in §3.1.2.2: two 112s in one
  document. Publish the per-section URLs, because the single-page render strips section numbers and a reviewer
  landing there cannot self-verify.
- **Saved recovery codes are not classified as look-up secrets.** §3.1.2 says only that "A typical application of
  look-up secrets is for one-time saved recovery codes", one-directionally, and Table 2 lists the two as separate
  secret types. So if ticket 19 ever takes the saved-code branch, the storage obligation is ambiguous between
  §4.2.1.1's "approved one-way function" and §3.1.2.2's salted password-hashing scheme — **minting at ≥112 bits
  dissolves it**, and simultaneously satisfies ASVS 6.5.2 (L2) and 6.5.4 (L2). Also: 112 in §3.1.2.2 is a *Consolidated into the register (ticket 33): R-MFA-021. Amend the table by ID, not this list.*
  **pointer** to SP 800-131A's latest revision, not a constant, where ASVS hard-codes it; and **ASVS V6.5's prose
  cites NIST 800-63-3**, so ASVS citations must not be cross-walked onto 63B-4 section numbers.
- **16.3.3's second limb is real and its overlap argument is not.** Verbatim: "and also logs attempts to bypass the
  security controls, such as input validation, business logic, and anti-automation." Authorization is **not** among
  the three, so 16.3.2 subtracts nothing — but anti-automation *does* collapse to zero for us, because ticket 09
  declined CAPTCHA and progressive backoff, leaving the limiters and lockout as our only anti-automation controls
  and a throttle trigger as the detection point. **Two new event families for ticket 13: input-validation
  rejections and business-logic rule violations.** The limb's *first* half is already discharged by ticket 13's
  generated-from-enum inventory. *Consolidated into the register (ticket 33): R-AUD-019. Amend the table by ID, not this list.*
- **The §4.6 residual has two causes and mail transport fixes one.** The verb is "support" — "CSPs SHALL support
  at least two notification addresses per subscriber account" — so it obliges capacity, not population. A
  single-valued email column fails it on **capacity**, independently of transport. Declining anyway, because a
  supported-but-never-populated second address is a hollow claim while the parent obligation fails; recorded as one
  residual with **three address axes**: §4.6's two notification addresses (capacity), no transport outside `dev`
  (delivery), and §4.2.1.2's two *recovery* addresses (a genuinely distinct SHALL, different subject and verb,
  activated by the route taken in §6). *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-BLD-004. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-CRED-024. Amend the table by ID, not this list.*

### 9. What this ticket deliberately does not do

**The 47-row pass is not here.** The schema, the gate, the axis, the two renderings, the adopted set and four
worked rows are; assigning `responsibility`, `status`, `priority` and an acceptance check to every row is
mechanical once the extractor exists and belongs with whoever builds it — ticket 17's re-split. Doing it here would
hand-transcribe the very table the gate exists to generate. The four worked rows are the **schema spike** that
stands between ticket 17 and a grid that turns out not to fit; each of the schema's three late fixes
(`enforced-elsewhere-cited`, the `status` scoping sentence, the acceptance-check sentinel) was found by trying to
encode one of them.

**The document itself is not written here either.** This ticket decides its contents, owner, location, schema and
ordering. Writing it is `/do-work`, behind the extractor.

### 10. Amendments owed to other tickets

- **23** — §8 gains a fourth row marked *not a pathway*, naming the break-glass path and the rebinding runner and
  saying why the existing carve-out reaches them; **6.4.4 (L2) regraded from N/A to satisfied-by-parity**, because
  the trigger is the loss event and the obligation is parity against enrolment, so the N/A reasoned from the wrong
  half of the sentence; §4.1.2.1's "first enrolment" reframed as binding an additional authenticator to an
  AAL1-capable account. **6.5.6 (L3)** added as the supporting positive citation, cited nowhere on this map before.
- **13** — 16.3.3's two new event families. *Consolidated into the register (ticket 33): R-AUD-019. Amend the table by ID, not this list.*
- **24** — AES-192 is Approved, so the "Why 32" row's implied floor is corrected; `git.properties` and
  `build-info.properties` onto the 13.4.1 jar-content test; 13.4.5's "authenticated base path" clause struck with *Consolidated into the register (ticket 33): R-BLD-014. Amend the table by ID, not this list.*
  the verdict intact.
- **21** — the "which is mild" sentence flagged as the reopening trigger for its own `info`-endpoint argument. *Consolidated into the register (ticket 33): R-BLD-015. Amend the table by ID, not this list.*
- **19** — reopening trigger: the recovery-codes deferral is what forces every expensive branch in §6, and
  **saved-plus-issued is the branch to take if it is ever reversed**, with the ≥112-bit minting note attached. *Consolidated into the register (ticket 33): R-MFA-021. Amend the table by ID, not this list.*
- **07** — the 15-character floor recorded as a §3.1.1.2 SHALL barred two independent ways.
- **17** — 13.2.4 / 13.2.5 as 13.1.1's enforcement companions, not drift-family members; and the register carries *Consolidated into the register (ticket 33): R-BLD-006. Amend the table by ID, not this list.*
  the adopted-reading note on §4.2.1. *Consolidated into the register (ticket 33): R-CRED-019. Amend the table by ID, not this list.*
- **08** — the `Clear-Site-Data` item taken from its body (`cookies` reaches the registered domain, `cache` and
  `storage` do not reach the SPA) rather than from its lossy handover bullet.
- **09 / 10** — a third throttled counter: §4.2.1.2 imports §3.2.2 onto recovery-code verification, beside the
  password axis and the factor tiers. A fourth arrives only with ticket 19's saved-code branch. *Consolidated into the register (ticket 33): R-MFA-021. Amend the table by ID, not this list.*
- **02** — nothing. The §4.2.1 tension is not a defect under the adopted reading. *Consolidated into the register (ticket 33): R-CRED-019. Amend the table by ID, not this list.*

### 11. Register entries, ADRs, reopening triggers

**Nine register entries:** §4.2.3's notification SHALL failed (cause: no transport); §4.6's two-address SHALL *Consolidated into the register (ticket 33): R-CRED-022. Amend the table by ID, not this list.*
declined with the three-axis residual; the issued-code/password correlation accepted as a documented choice; *Consolidated into the register (ticket 33): R-CRED-024, R-CRED-025. Amend the table by ID, not this list.*
13.3.1 (L2) and 13.3.3 (L3) inherited; 16.4.2 / 16.4.3 (L2) inherited as deployer obligations; 13.2.1 / 13.2.2 *Consolidated into the register (ticket 33): R-AUD-013, R-CFG-013. Amend the table by ID, not this list.*
(L2) accepted; 11.5.1 (L2) satisfied-by-procedure; 13.4.6 (L3) satisfied-by-absence with the plugin trigger; *Consolidated into the register (ticket 33): R-BLD-014, R-CFG-008, R-DATA-015. Amend the table by ID, not this list.*
lm-16's alerting half as a **High**. *Consolidated into the register (ticket 33): R-OBS-004. Amend the table by ID, not this list.*

**Six ADRs:** one generated source with two renderings behind a `verify` gate; the three-field schema with *Consolidated into the ADR routing (ticket 34): ADR-069. Amend by ID, not this list.*
`priority` structurally absent on limitations; deployment-sequence ordering with severity excluded; break-glass as *Consolidated into the ADR routing (ticket 34): REJ-075 / REJ-076. Amend by ID, not this list.*
§4.2.2.2 option 2 with the adopted Reading B recorded; the distinct-recovery-address mitigation in preference to *Consolidated into the ADR routing (ticket 34): ADR-070. Amend by ID, not this list.*
recovery contacts and to saved codes, with both rejections reasoned; roles-not-people with vacancies as unmet
checks. *Consolidated into the ADR routing (ticket 34): ADR-071 / REJ-077. Amend by ID, not this list.*

**Four reopening triggers:** any real deployment (row 4 flips `responsibility` to `deployer`); a mail transport *Consolidated into the register (ticket 33): R-CFG-023. Amend the table by ID, not this list.*
entering scope (the §6 conditional pass closes and three notification SHALLs resolve at once); ticket 19 reversing *Consolidated into the register (ticket 33): R-CRED-026. Amend the table by ID, not this list.*
the recovery-codes deferral (branch 1 supersedes the mitigation taken here); and the `git.properties` / *Consolidated into the register (ticket 33): R-MFA-021. Amend the table by ID, not this list.*
`build-info.properties` plugin being added. *Consolidated into the register (ticket 33): R-BLD-015. Amend the table by ID, not this list.*

**Two glossary terms:** *acceptance check* (how a reader proves a deployer obligation was discharged, as distinct
from an enforced check, which the application holds); *declared vacancy* (a named role with no holder, encoded as
an unmet acceptance check rather than a satisfied row).

---

## Post-resolution capture: ticket 14, and the back-fill scope

[Design the frontend architecture](14-frontend-architecture.md) resolved **during** this ticket's grilling session,
after the declaration rule landed in the map but too late to be read by it, and it routed two contents here **in
prose** rather than under `### Handover items (ticket 25)`. Captured now rather than left to the extractor, because
an item nobody has transcribed and nobody can extract is the exact failure the rule exists to prevent — arriving
one session late is its first real test, and it failed:

1. **The two-admin, three-condition support copy.** Ticket 11 already owed the promote-before-demote sequence; ticket
   14 sharpens it to the three conditions the UI must state together — `activated_at IS NOT NULL`, promoted, **and**
   TOTP-enrolled — because "naming two of the three produces the same support call one stage earlier". Folds into the
   existing promote-before-demote content rather than becoming a new row.
2. **One-time-token handling rules** for the frontend, which pair with ticket 09 §R.3's output warning: the rebinding
   token and the issued recovery code are plaintext credentials that must not reach any log, transcript, analytics
   sink or error report. The warning was already `enforced` on ticket 13's negative list and as a ticket 24
   prohibited-configuration entry; ticket 14's contribution is the client-side half, so the row's `responsibility`
   is `shared` rather than `deployer`. *Consolidated into the register (ticket 33): R-FE-007. Amend the table by ID, not this list.*

Ticket 14 also narrowed a ticket 08 item that touches this document: of the three `Clear-Site-Data` directives,
`cache` "carries a partial implementation with a documented hang bug and a 44-version Firefox hole" and buys least
given the SPA clears its own state unconditionally. That does not change the amendment recorded on ticket 08 — the
operational statement is still that **cookies reach the registered domain while cache and storage do not reach the
SPA** — but it means the `cache` directive's *value* is contested on browser-support grounds as well as on scope. *Consolidated into the register (ticket 33): R-HDR-008. Amend the table by ID, not this list.*

**Back-fill scope for the extractor, stated so it is bounded.** The declaration rule binds prospectively; the
tickets resolved before it are ticket 25's own debt. That set is **every resolved ticket except 15 and 16** — and of
those, the ones carrying items not already transcribed into this file are **08** (the `Clear-Site-Data` item, from
its body rather than its bullet), **02** (the profile-driven cookie name and `Secure` flag), **01** (the as-10 /
dp-3 evidence role) and **14** (the two above). The remaining ten tickets already have sections in this file. Tickets
15 and 16 are the only ones the rule will actually govern as intended, which is worth stating plainly rather than
letting the rule look more load-bearing than its timing allowed.

---

## Amendment from ticket 15 (threat model) — a consequence for a trigger you already record, and one row graded too kindly

Ticket 15's handover items arrive under the declaration heading, as your rule requires, so this section is only
the two places where the threat model contradicts something already written here. You named tickets 15 and 16 as
"the only ones the rule will actually govern as intended"; this is 15 taking that seriously in both directions.

### 1. Your mail-transport reopening trigger carries an unrecorded consequence — TM-13

Your §6 makes break-glass **NIST §4.2.2.2 option 2** — one issued recovery code plus the admin's password — with
a recovery address distinct from the login address as the adopted mitigation for the password/mailbox
correlation, and records mail transport as the single named prerequisite with the shell runner as the interim.
Your §11 records "a mail transport entering scope" as a reopening trigger, on the grounds that the conditional
pass closes and three notification `SHALL`s resolve at once. All of that is good news, and it is the only half
recorded.

The other half: **ticket 10 §12's containment claim inverts.** Its load-bearing sentence is that the log leak is
total for ordinary users and **partial for administrators**, contained only by ticket 19's TOTP, and
confidentiality-only — a log reader can reset an admin's password but cannot obtain the factor. Your recovery
code clears the TOTP enrolment. So a reader of the `dev` log who obtains **both** a reset link and a recovery
code holds option 2 in full, and the containment goes from partial to total for administrators — against the
population the whole MFA scope decision exists to protect. §4.2.1.2's recovery-address confirmation code travels
the same channel, so the mitigation's own **establishment** step is in the leak too.

Nothing is live today: with no transport the route does not exist and the shell path is the only one, exactly as
you record. The finding is that the consequence is not attached to the trigger, and ticket 13's dev-only
confinement is the **wrong control** for it — that control confines the link *to* `dev`, and `dev` is precisely
where the stub is.

**Amendment: the trigger carries the consequence**, plus a build-phase control — the recovery-code route and the
recovery-address confirmation must be **structurally unavailable while the transport is the stub**, not merely *Consolidated into the register (ticket 33): R-CRED-020. Amend the table by ID, not this list.*
unused. Deliberately **not** a reopening of ticket 10 or 19: the map's rule reopens when an amendment weakens a
compensating control standing in for a declined `SHALL`, and this weakening is contingent on a trigger that has
not fired. The call is recorded rather than assumed so a later reader can disagree with it. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CRED-023. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-CRED-026. Amend the table by ID, not this list.*

### 2. Your §4 grades the rebinding runner's output warning `enforced`; it is not — TM-12

Your §4 concludes that of the four items on the rebinding runner, "the output warning **is** enforceable (asserted
on ticket 13's negative list and as a ticket 24 prohibited-configuration entry)", leaving only the ownership
statement unconvertible. The premise is that `System.out` is not the logging system. It is not — and it is
nonetheless the **same file descriptor as the audit stream**.

Ticket 13 §13 emits audit NDJSON to **stdout in every profile**, calling it the Enforced Constraint, the platform
ingestion path and the only route to 16.4.3, and it explicitly reversed a draft that would have dropped the
console copy outside `dev`. Your own sequence step 6 then has the deployer install log forwarding over that
stream. A collector tails a descriptor; it does not distinguish bytes Logback wrote from bytes
`System.out.println` wrote, and on a container runtime that collection is unconditional. So the control protects
against *the application* writing the token to an appender and does nothing against *the platform* collecting the
identical bytes — for the credential of last resort, on a channel present in every profile by design.

Two consequences for this file — **both now closed by [ticket 28](28-out-of-band-privileged-channels.md), edited
in place here rather than appended beside, so this section does not carry two statements of one correction.**

(Citation note: the stdout topology this section quotes is ticket 13 **§12**, not §13, which is the reset-link
leak. The mis-citation originated here and was inherited by ticket 28.)

- **The row's grading — closed, and the item it grades has changed.** Ticket 28 did not choose among the three
  channels this section listed. It found that **the runner as specified could never execute** (H2 file mode is
  single-writer), moved it to an offline run of the same jar, and **inverted the channel**: the operator supplies
  the new password on stdin or a prompt, and nothing secret is emitted at all; the batch form mints nothing. So the
  §4 item is no longer "the output warning" but **"no secret is emitted"**, graded **`status: asserted-by-test`,
  `responsibility: shared`** — the deployer half being the out-of-band handover of the operator-set password to
  the user. The named test: run the runner with a known password and assert that string appears in **none** of
  stdout, stderr or the audit file. This remains the second item your extraction caught mis-graded rather than *Consolidated into the register (ticket 33): R-RUN-001. Amend the table by ID, not this list.*
  missing, which is still an argument for the gate.
- **Step 7 — replaced.** The rehearsal's first two clauses stand ("run the rebinding runner, clear a tier-2 disable
  through the break-glass path"); the third, "confirm the output warning holds", is replaced by: confirm the audit
  rows (dry-run, intent, outcome) are in the audit file **and reached the collector after restart**; confirm
  forced change fires at the user's first login; run ticket 28 §6's negative cases **on the deployed topology** —
  wrong database path, application still running (non-zero exit, zero bytes appended to the audit file), leftover
  invocation refused; and compare the printed database path, schema version and mtime against the deployed
  database. The break-glass worked row inherits this automatically, since its acceptance check is "the rehearsal".
- **Step 7 gains a recurrence.** A rescue path exercised once at install rots, and this one proved it by being
  unexecutable for its entire documented life. Re-run on any change to the runner, the password policy or the
  bootstrap; on **any JDK, Boot or H2 major upgrade**; and within a calendar ceiling the deployer sets. *Consolidated into the register (ticket 33): R-RUN-006. Amend the table by ID, not this list.*
- **ASVS 6.1.1 (L1) is re-asserted, not inherited: pass-with-note pending first rehearsal.** Not graded pass until
  the rehearsal has passed once. **A red rehearsal grades 6.1.1 F with the failure as cause and reopens ticket 28.** *Consolidated into the register (ticket 33): R-LCK-003, R-RUN-002. Amend the table by ID, not this list.*
- Ticket 28 also declares eight handover items under the heading; they reach this document by extraction. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-027. Amend the table by ID, not this list.*

### 3. One item your §7 refusal points at, now delivered

Your §7 refused pm-6's network topology and data-flow diagram as "architecture documentation, ticket 15's
data-flow work plus ticket 17, not an operational obligation". That work exists:
[`threat-model/secured-hello-world.json`](../threat-model/secured-hello-world.json) (three diagrams, six trust
boundaries, fourteen threats) and [`threat-model/report.md`](../threat-model/report.md). The refusal stands and
now has an artefact behind it rather than a forward reference.

## Amendment from ticket 31 (IPv6 source keying)

- **`25:421-424` carries two superseded figures.** "7×, and defeated by IP rotation" becomes: *≈100× per bucket; the
  ladder binds from ≈20 source keys, which IPv6 makes free for anyone holding more than one /64.* "~10 hours of lead
  time" becomes **≈9.7 h**, with the cap at ≈14 h. The ladder is now floor-checked at startup. *Consolidated into the register (ticket 33): R-LCK-011. Amend the table by ID, not this list.*
- [Ticket 31](31-ipv6-source-keying.md) declares **five handover items** under its own heading, for extraction: AAAA
  declaration; IPv6 aggregation in the edge per-source limit, which tightens 26's and 29's existing item rather than *Consolidated into the register (ticket 33): R-OPS-008. Amend the table by ID, not this list.*
  adding one; edge access-log retention with **both** a minimum (580 min) and a deployer-set maximum (30-day planning *Consolidated into the register (ticket 33): R-OPS-006. Amend the table by ID, not this list.*
  value, PDPA); the warning-window runbook; and IP literals in `X-Forwarded-For`. *Consolidated into the register (ticket 33): R-LCK-012, R-OPS-009, R-OPS-010. Amend the table by ID, not this list.*

---

## Amendment from ticket 30 (sole-admin bootstrap premise)

[Ticket 30](30-sole-admin-bootstrap-premise.md)'s four items are declared under its own `### Handover items (ticket 25)` *Consolidated into the register (ticket 33): R-ADM-017. Amend the table by ID, not this list.*
heading; extract from there. The corrections to this file:

1. **25:88–99, "a newly deployed system legitimately runs with one admin".** It *boots* with one; it **goes live with
   two enrolled** (a handover item, not a gate). The two scenarios become: *Consolidated into the register (ticket 33): R-ADM-017. Amend the table by ID, not this list.*
   - **Account lockout** — still delayed, not locked out, but on the 20/40/60 ladder (password) and 20 minutes
     (TOTP tier 1), not a flat 20. *Consolidated into the register (ticket 33): R-LCK-009. Amend the table by ID, not this list.*
   - **Lost authenticator** — no longer "unrecoverable without direct database access". With another
     `authenticable` admin it is an in-app factor reset (now exempt from the two-admin count). With none it is
     ticket 28's runner, scope `totp`, as a **planned outage** (28:241–245 cites these lines).
   25:100–101's warning gains its sibling: if the other admin is merely locked out, wait; do not reach for the runner. *Consolidated into the register (ticket 33): R-ADM-019. Amend the table by ID, not this list.*
2. **25:146–151, "the sole-admin recovery path is the 20-minute auto-lift, load-bearing for three decisions".**
   Copied from 09:535–541 and corrected with it: a ladder, and the NIST-deviation argument is superseded by the cap.
   The sole-admin recovery path is ADR 13's three routes (ticket 30 §4).
3. **25:402–410 and 25:621** still describe the runner minting and printing a token. Do not restate; they are
   superseded by this file's in-place TM-12 correction at **25:891–902**.
4. **25:103–105, promote before demote,** now applies from day one of go-live, and does not block factor reset. *Consolidated into the register (ticket 33): R-ADM-018. Amend the table by ID, not this list.*

---

## Amendment from ticket 16 (test plan)

- **The drift gate is one JUnit test run under Failsafe 3.6.0, not a plugin execution as well.** Two implementations of one comparison can disagree. Bypasses: `-DskipITs` and `-Dmaven.test.skip`. `-DskipTests` no longer skips Failsafe as of 3.6.0 (apache/maven-surefire#3371). *Consolidated into the ADR routing (ticket 34): ADR-069 (attached amendment). Amend by ID, not this list.*
- **The step-7 rehearsal is an acceptance check, not a test,** as this ticket drew it. Ticket 16 automates what surrounds it (ticket 28's runner tests 2, 6 and 7 run both ways).
- **Constraint on the mail-transport reopening trigger (TM-13).** No path, gate or mechanism exists yet for the recovery-code route or the recovery-address confirmation (25:865: "with no transport the route does not exist"). When they are built:
  - their availability gate keys on a **declared transport property**, **never** on the `EmailService` bean type, because ticket 16's `ctx-default` replaces the stub bean with a capture bean;
  - the route paths and their matcher are fixed at that time;
  - ticket 16's absence test then gains a pinned HTTP status for each path and principal. *Consolidated into the register (ticket 33): R-CRED-026. Amend the table by ID, not this list.*

  Until then the test is structural (ticket 16 Answer §7). *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-BLD-006, T-CRED-023. Amend the table by ID, not this list.*
