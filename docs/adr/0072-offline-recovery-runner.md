---
status: accepted
---

# ADR-072: Operator recovery is an offline same-jar runner run inside a planned outage

Operators recover a disabled or lost authenticator with a command-line runner. The runner is **the application jar
itself, started with the application stopped**, and the application is restarted afterwards. So operator recovery
is a planned outage. An in-app admin endpoint, or a live database listener that lets the runner work beside the
running application, is the obvious "no outage" fix. Both are rejected below. The runner also bypasses the
two-admin guard, on purpose.

## Context

Some account states can be cleared only by rebinding a credential, and outside `dev` there is no mail transport to
deliver a reset link:

- the NIST SP 800-63B-4 §3.2.2 cap disables the password authenticator after 100 consecutive failures (ADR-013);
- the TOTP tier-2 lock disables the factor (ADR-027);
- a sole administrator can lose their authenticator.

So a recovery channel had to be built. The alternative was weakening the cap, for example enforcing it only while
"recovery is working". That condition is false in exactly this deployment, so a cap gated on it would never engage.

The database is H2 in embedded file mode, and the H2 documentation (Features, "Automatic Mixed Mode") is explicit:
without `AUTO_SERVER=TRUE`, a second process cannot open a database that another process holds. A runner started
beside the live application therefore cannot run at all.

## Decision

- **Process model:** stop the application, then run the same jar with `spring.main.web-application-type=none` and
  an explicit runner argument, then restart the application.
- **Explicit scope:** `password`, `totp` or `both`, with no default. An admin can hold both states, and an implicit
  default produces a half-recovery that looks complete.
- **Admissible states by scope:**
  - `password`: the account exists and is not tombstoned;
  - `totp`: a factor row exists, in any lock state.

  The runner does **not** require the account to be capped or tier-2 locked. The lost authenticator on a healthy
  account is the case this tool exists for (T-RUN-009).
- **A batch form**, because a mass-lockout event covers hundreds of accounts, and a one-at-a-time procedure fails
  exactly when it is needed. Batch size is capped, and the cap is checked against the whole input before any write.
  The batch runs in one transaction with one outcome row.
- **The runner runs through the full context refresh**, so the refresh-phase configuration validator executes
  (T-RUN-004). Its other preconditions are behaviour stated in the spec (REJ-089):
  - `IFEXISTS=TRUE`;
  - it never migrates;
  - it never runs the admin seeder.
- **The trigger is read from `ApplicationArguments`, never from the `Environment`** (T-RUN-003). Spring Boot's
  relaxed binding lets an environment variable such as `REBIND` satisfy a property lookup for `rebind`, so a
  property-gated trigger could be fired by a leftover variable nobody typed.
- **The app-stopped interlock is the H2 lock.** The runner attaches the audit file appender only after its
  precondition checks pass. A runner started while the application holds the database exits non-zero and appends
  zero bytes to the audit file (T-RUN-010). Logback's `FileAppender` is single-writer unless prudent mode is set,
  and we do not set it.
- **The runner bypasses `AdminActionGuard`.** Scope `both` must be able to take the system to zero enrolled
  admins, because that is what break-glass means. With the application stopped, there is no concurrent guard to
  contend with, and no authenticated principal for the self-action rule to apply to. The invariant becomes
  **observable rather than enforced**: the audit rows record the enrolled-admin count before and after the run, and
  a transition to zero raises an alert (ADR-048).

## Considered options

- **An in-app privileged endpoint.** Rejected. It gives up the property that makes this channel acceptable:
  deploy-level access to the host is a strictly higher bar than admin HTTP access. It also needs an authentication
  surface of its own, on an account that by construction cannot authenticate.
- **`AUTO_SERVER=TRUE`.** Rejected. H2 then starts a server on a random port that accepts remote connections,
  authenticated by possession of a key written to `.lock.db`. That is a permanent network listener on the one
  process that holds every key in the system.
- **An explicit loopback H2 TCP server.** Rejected for four reasons:
  - `h2.bindAddress` is a JVM system property, invisible to the refresh-phase validator;
  - H2's own Spring example passes `-tcpAllowOthers`, so the configuration people copy is the one that opens the
    database to the network;
  - H2 lets an admin `CREATE ALIAS` onto arbitrary Java methods by default, and the application's database user is
    the H2 admin, so a listener turns "can read the datasource password" into "can run code beside every key";
  - it buys "no outage" and nothing else. It does not answer what the runner emits (ADR-073).
- **Offline same-jar runner (chosen).** The outage costs little here: every clustering property is prohibited, so
  there is no high-availability posture to preserve.

Three refresh-phase validator entries keep these rejections closed (T-CFG-012 to T-CFG-014):

- `AUTO_SERVER=TRUE` in the resolved JDBC URL;
- `FILE_LOCK=NO` in the resolved JDBC URL, which would silently remove the single-writer lock the interlock rests
  on;
- any H2 TCP server bean.

## Consequences

- **Recovering a sole admin is a planned outage.** Mass-lockout recovery outside `dev` means many sequential
  interactive runs, each inside an outage window. That recovery time is recorded as uncosted.
- **The outage buys two things:** no live guard to race, and a database that stays still between plan and apply,
  which makes the confirm digest (ADR-074) a strong interlock.
- **The process model does not make the runner's output uncollected.** A Kubernetes Job or a systemd one-shot unit
  captures stdout just as the application's does. What the application can observe is the same file descriptor in
  every case. That is why the runner emits no secret at all (ADR-073).
- **ASVS 6.1.1 (L1) is pass-with-note, pending the first rehearsal.** The rehearsal runs the runner on the deployed
  topology, checks the audit rows reach the collector after restart, and runs the negative cases (wrong database
  path, application still running, leftover invocation). A red rehearsal grades 6.1.1 F and reopens this ADR.
  Rehearsal recurs on:
  - any change to the runner, the password policy or the bootstrap;
  - any JDK, Spring Boot or H2 major upgrade;
  - a calendar ceiling the deployer sets.
- **Ownership is a handover obligation.** A named, reachable operator with deploy-level access must exist. If the
  sole admin holds no shell and the platform team is unreachable, the recovery exists only on paper.
