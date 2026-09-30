# 36 — Write the platform ADRs: error envelope, sessions and CSRF

Type: task
Status: resolved
Blocked by: 34
Blocks: 18

## Question

Write the thirteen ADRs that [ticket 34](34-adr-candidate-list.md) routed to this ticket. They go to `docs/adr/`, in
the `domain-modeling` skill's ADR format: **ADR-029 to ADR-041**. That covers the following:

- the two sketch ADRs no resolved ticket owns: session cookies over JWT (ADR-029) and Spring Session JDBC
  (ADR-030);
- the error envelope;
- registration and lockout on the wire;
- session invalidation, rotation and after-commit dispatch;
- CSRF;
- anonymous-session creation and shedding.

*Narrowed by ticket 34.* Owner 36's full share was 46 ADRs. Ticket 34 split it four ways. The admin module and data
model moved to [41](41-adrs-admin-and-data-model.md). Logging, observability, configuration and topology moved to
[42](42-adrs-logging-observability-config.md). The test harness, threat model, handover design and recovery runner
moved to [43](43-adrs-harness-handover-runner.md).

The ID, title, merged sources, attached amendments and filter answer for each ADR are in
[`adr-routing/routing.md`](../adr-routing/routing.md) §2.

## Rules

Same as [ticket 35](35-adrs-authentication.md) §Rules: one decision per ADR as 34 grouped it, the no-`NN:line`
rule, and primary-source citations carried into each ADR.

The two sketch ADRs have no resolved ticket behind them. Argue them from the PRD and the stack baseline in
`map.md` Notes, and check their external facts at source. The PRD's own appendix argues ADR-029 and is a
citable primary source for it.

## Done when

- ADR-029 to ADR-041 exist, and their `docs/adr/README.md` index rows move from `reserved`.
- The same grep as ticket 35 returns nothing for these files.

---

## Answer

**All thirteen are written, `accepted`, and linked from the index.** Files are
[`docs/adr/0029-…` to `0041-…`](../../../docs/adr/README.md), named `00NN-slug.md` per the `domain-modeling` skill,
which is the same convention ticket 43 used. Grouping is exactly as routing §2. No regrouping, and no amendment to 34.

**Done-when check.**
- Index rows ADR-029 to ADR-041 moved from `reserved` to `accepted`, with links.
- The ticket 35 grep (`\b\d{2}:\d+`, `ticket \d`, `\d{2} ADR`, `.scratch`) plus a bare `\bticket\b` returns **0**
  matches across the thirteen files.
- Every `ADR-`, `REJ-` and `T-` ID they cite (73 distinct) resolves to a row in the index or the test-plan table.

### 1. External facts checked at source for this ticket

Checked fresh, not carried from `research/`:

- OWASP ASVS 5.0 V7 (7.2.4 L1, 7.4.1 L1, 7.4.2 L1, 7.4.3 L2), V6 6.3.8 (L3), V3 (3.5.1 L1, 3.3.2 L2), V14 14.2.1 (L1),
  V2 (2.1.3, 2.3.3, 2.4.1, all L2), V15 15.1.3 (L2). Fetched from `OWASP/ASVS` master, `5.0/en`.
- RFC 9457 §3.1–3.2, including §3.1.4 "consumers SHOULD NOT parse `detail`", which now backs the branch-on-`code` rule
  directly.
- Spring Boot 4.1 reference: `spring.mvc.problemdetails.enabled` is off by default; the `/error` and
  `BasicErrorController` behaviour.
- Spring Security 7.1.1 source: `CsrfTokenRequestHandler#resolveCsrfTokenValue` (header, then `_csrf` parameter);
  `XorCsrfTokenRequestAttributeHandler` is `final` and inherits it.
- Spring Session 4.1.1 source: the `SpringSessionBackedSessionRegistry` constructor takes a
  `FindByIndexNameSessionRepository`. Spring Session Redis reference: Boot's default Redis repository has no index.
- CVE-2026-48117 (NVD, SentinelOne), GHSA-qq9h-g4jm-xgf3 (GitHub advisory, read in full), CVE-2026-56081
  (SentinelOne).
- OWASP CSRF Prevention Cheat Sheet (the login-CSRF pre-session passage) and Session Management Cheat Sheet (renew
  the session id after any privilege change, which names password changes).
- The governing standard's clauses, read in the standard itself rather than taken from ticket quotes: Happy Path
  steps 5, 8 and 13, Failure Paths 1, 2, 8 and 22, §3.1, §3.2, §4, §5 and §6. The recipes' `sendError` calls and
  `revokeOtherSessions`.

Research-asset facts reused, and cited in the ADRs by class and version: Spring Session `REQUIRES_NEW` (the Boot 4.1
asset, §19); the §F facts on `CsrfFilter` session creation, the `loadDeferredToken` trap and the H2 measurements (the
anonymous-session asset); and the Spring Security 7.1 MFA filter facts (the MFA API asset).

### 2. What the checking changed

None of these reverses a decision. Each changes what an ADR says.

1. **ADR-030's premise was half wrong.** The sketch says Spring Session JDBC is "driven by the requirement to
   invalidate all of a user's sessions". Redis can meet that requirement too, through `RedisIndexedSessionRepository`
   (`repository-type=indexed`). It fails only with Boot's *default* Redis repository, which keeps no index. So the
   requirement rules out container sessions and default Redis. What rules out indexed Redis is the stack: a second
   datastore where the baseline has one. The ADR says so, and it states the swap rule as "the store must implement
   `FindByIndexNameSessionRepository`, and T-SES-009 must pass". The standard's own §4 persistence constraint and
   Happy Path step 13 turned out to be stronger primary sources than the sketch's argument.
2. **ADR-034 and ADR-037 interact, and no ticket had written it down.** Lockout ends sessions (ADR-037). So an
   attacker who knows a username can still end that user's session, with five failures inside the observation window
   rather than one. ADR-034 removes the one-request kill. It does not remove the power. Both ADRs now say this. The
   decision stands: the five-failure path is throttled, audited, and already implied by the standard's lockout.
3. **ADR-037's trigger set is larger than its title.** Ticket 23's amendment from 09 §R added the **automatic tier-2
   factor disable** as a session-kill trigger. It is not in 34's title, and it has **no replay test** in the test
   plan. The title is kept, because the index reserves titles, and the ADR's table carries the row.
4. **Two ADR invariants have no test row.**
   - Header-only CSRF resolution (ADR-036). Removing the wrapper fails nothing today. Owed: a valid token sent only
     as `_csrf`, in the query string or a form body, gets 403.
   - The reconciliation sweep (ADR-039). T-SES-022 covers only `password_disabled_at`. The rule covers every
     durable-state trigger: cap, admin disable, deletion, a lock still in force.

   Each ADR names the test it needs without claiming the test is absent, so the text does not go stale once the rows
   exist.
5. **Where the sweep runs was stated two ways.** Ticket 08's amendment hosts it on the Spring Session cleanup job.
   The glossary (`CONTEXT.md`, *Reconciliation sweep*) and the Boot 4.1 asset say "at startup". ADR-039 takes
   startup, which covers the crash residual the sweep exists for. It records the scheduled placement as an unverified
   option, because nothing checked that Spring Session's scheduler can host a foreign task. The residual this leaves:
   a dispatch that fails *without* a crash waits for the next start.
6. **ASVS 2.3.3 (L2) is newly engaged.** It asks that a business-logic operation succeed entirely or roll back, and
   the session half of every ADR-037 trigger cannot. It is L2 against the L1 target, so the verdict is unaffected.
   It is a new register input.
7. **ADR-032's advisory citation is sharper than the source ticket's.** GHSA-qq9h-g4jm-xgf3's fix treats "current
   proof of control over the address as authoritative" and removes any password set before that proof. That is the
   precise principle behind setting the password at activation, so the ADR cites it that way.
8. Minor: ticket 05 says both handlers resolve `X-CSRF-TOKEN` "or `X-XSRF-TOKEN`". The 7.1.1 source reads only the
   repository's configured header name, then `_csrf`. Nothing depended on the second name.

### 3. Owed elsewhere

- **Test plan** (amend the table by ID): the three missing rows in §2 items 3 and 4. They are a tier-2 replay test, a
  header-only CSRF test, and sweep cases for the other durable-state triggers.
- **Ticket 33 (register):** ASVS 2.3.3 (L2), from §2 item 6. The register rows ADR-032, 034, 035 and 037 say "are recorded
  in the deferral register" were already routed to 33 by routing §2's `also →` column. Nothing new beyond 2.3.3.
- **Ticket 18 (compliance gate):** §2 items 2, 3 and 4 are the things a reviewer should check landed.

### Handover items (ticket 25)

No new items. ADR-036's same-site deployment constraint is already a deployment-blocking handover item from ticket 20.
The ADR points at the handover document for it and adds no obligation.
