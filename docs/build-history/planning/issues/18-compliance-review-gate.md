# 18 — Compliance review gate

Type: task
Status: resolved
Blocked by: 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44 (ticket 17 split into 33–38; ticket 34 split the ADR writing
into 35, 36 and 39–43; 32 resolved; 44 graduated from 33)
Rescoped: from a five-skill plan audit to a triage gate, before any work was done. See "Why rescoped".

## Question

Is anything still undecided, unowned or mis-graded that would force a design change after code exists, and if so,
what reopens before `/to-spec` runs?

This is the last ticket on the map. It is still a gate, not a formality. **An IM8 FAIL against a control outside PRD
scope may be accepted as a recorded residual with a stated reason. A FAIL against a PRD requirement must be fixed or
reopen its ticket.** *(Amended during resolution. The rule previously said every FAIL reopens a design ticket, which
contradicted accepting IM8 ac-3 as a recorded residual.)*

## Why rescoped

The original ticket ran `policies`, `spec-compliance`, `spec-standards-check`, `app-standards-review` and
`pragmatic-reviewer` across "the completed spec". Two of its premises fail:

- **No spec exists.** `/to-spec` runs after this map, so the spec-shaped skills have nothing of the right shape to read.
- **The design-level review has already happened, ticket by ticket.** The register grades all 324 rows, every
  deviation carries its reason, and the threat model is done. A full re-audit would mostly repeat verdicts already
  recorded.

What the code-phase gates cannot catch is still worth catching here. They see control patterns, not whether a decision
was right: a deliberate deviation looks correct in code, and `policies` marks scope, boundary and permission-model
problems FAIL rather than fixing them, which after `/do-work` means rebuilding. So this ticket keeps the part only a
plan-time gate can do and hands the rest to the build.

## How

1. **Close every item handed to this ticket.** Each is a fix at source or a reopen, never a note.
   - Unowned FAILs: **R-FE-002** (IM8 dp-8 classification labels on every input field) and **R-AUD-019**
     (ASVS 16.3.3 (L2) second limb: input-validation and business-logic bypass events). Find or assign an owner; per
     this ticket's rule, a FAIL with a design owner reopens that owner.
   - The other `application / unmitigated` rows: R-ADM-011, R-CFG-019, R-FE-005. Confirm each is an accepted residual
     with a reason, or reopen.
   - **R-OPS-001:** declare the IM8 risk classification, since `im8-review` derives every severity from it.
   - Register corrections, at the source row (the register is generated, ADR-069): R-CRED-013, R-STD-024, R-CRED-004
     (ticket 35); R-ADM-002, R-ADM-008, R-ADM-015 (ticket 41); R-CFG-007, R-CFG-012, R-CFG-020, R-HDR-003, R-SES-004
     (ticket 42). Confirm ASVS 2.3.3 (L2) landed as a row (ticket 36).
   - Missing or broken test-plan rows: the three ADR-036/037/039 invariants (ticket 36, below); tier-2 factor disable
     sets `force_password_change` (ticket 39); the nine rows ticket 42 lists, including T-HDR-003; the ArchUnit row
     enforcing ADR-065's slice ban (ticket 43). Add each by ID.
   - **ADR-046's open item:** does completing a forced change clear `credential_issued_at`? Decide it, or name it as a
     spec-owed decision with its failure mode stated.
   - T-AUD-011's uncited Logback arithmetic (ticket 38).
2. **One `policies` pass over the register's non-`pass` rows only** (`fail`, deferred, unmitigated), not the whole
   plan. The question for each row: is this an accepted residual with a stated reason, or a scope, boundary or
   permission-model problem the skill would mark FAIL? The second kind reopens its owning ticket.
3. **Clear the map's remaining fog.** The map cannot be done while in-scope fog remains, and none of it has a ticket:
   - **Owner notification mechanics.** Graduate a ticket, or rule it out of scope with the SHALL failures it leaves
     recorded as register rows.
   - **Accessibility conformance target** and how keyboard and screen-reader behaviour get verified. Same choice.
   - **Rate-above spikes and ratios.** Already declared permanently a handover item. Confirm the register rows exist,
     then clear the entry.
4. **Write the gate list** (below) where `/to-spec` will carry it into the spec.

## The gates the build must pass

These cannot run now and are deliverables *of* this map. Write them as mandatory gates:

**After `/to-spec`, before `/to-tickets`** (moved here from this ticket's original scope, because they need a spec):

- `spec-compliance` against IM8 and ARC
- `spec-standards-check` against the applicable appfw standards, including
  `Standalone_User_Access_Control_Application_Standard.md`
- `pragmatic-reviewer` over both sets of findings

**Build phase:**

- `dependency-vuln-scan` and the Maven-bound OWASP Dependency-Check with its CVSS threshold
- `semgrep` on all changed code
- `spring-security-review`, `spring-web-review`, `spring-data-review`, `spring-logging-review`,
  `spring-test-review`
- `react-review`
- `im8-review` against the implementation. It targets Boot 3.4 / Security 6.4 config spellings, so a 4.1 control
  reported absent is checked by hand before it counts as a FAIL.
- `build-check` and `code-reviewer`
- `mutation-testing` on the security-decision classes (scope and threshold in ticket 16's amendment, below)
- the traceability gate (`@Proves("T-…")`) and ticket 25's drift test
- a recorded manual keyboard and screen-reader pass against WCAG 2.2 AA on every route (R-FE-005)
- `browser-test` against the PRD's acceptance criteria as the final acceptance gate

## Done when

Every inbound item in step 1 is fixed at source or has reopened a ticket; every non-`pass` register row has passed the
`policies` question in step 2; the three fog patches are ticketed, ruled out of scope or cleared; the gate list is
written; and the map has no open tickets left, so `/to-spec` can run.

---

## Amendment from ticket 16 (test plan)

- **Blocked by [32](32-test-plan-table-transcription.md).** Test coverage cannot be graded against a table that does not exist yet.
- **`mutation-testing`: a scoped yes.** It runs in a `-Pmutation` profile with an explicit `--threshold=85`; the skill's default is 70. The scope is the security-decision classes:
  - lockout counter and ladder;
  - `AdminActionGuard`;
  - `SourceKeyResolver`;
  - `PasswordService` policy;
  - token hashing and consume;
  - TOTP window and replay;
  - emitter key validation.

  The guard, token consume and TOTP window logic are extracted into pure decision functions, so PIT does not boot a context per mutant. PIT is **not** evidence for the pessimistic lock: no standard mutator swaps a locking query for a non-locking one.
- **`browser-test` stays a separate acceptance gate.** Ticket 16's Playwright suite (level E) is narrow, about eight tests, and does not replace it.
- **Two more build-phase gates** to write into the spec:
  - the traceability gate (`@Proves("T-…")`);
  - ticket 25's drift test.

  Both run under Failsafe 3.6.0.

## Note from ticket 43 (platform ADRs)

ADR-065 bans `@WebMvcTest` and every other slice for security-control tests, but **no T-row enforces the ban**. The
ADR says so plainly. An ArchUnit row that forbids slice annotations on `@Proves`-annotated test classes would make it
enforced at almost no cost. Grade ADR-065 as a documented-but-unenforced rule unless that row is added by ID.

---

## Amendment from ticket 36 (platform ADRs)

Three invariants in ADR-036, ADR-037 and ADR-039 have no row in `docs/test-plan/test-plan.md`. Check that each has
landed before passing the gate:

- a replay test for the **automatic tier-2 factor disable** ending the subject's sessions (ADR-037);
- a **header-only CSRF** test: a valid token sent only as `_csrf`, in the query string or a form body, gets 403
  `CSRF_TOKEN_INVALID` (ADR-036);
- **reconciliation-sweep** cases for admin disable, deletion and an in-force lock, beside T-SES-022's cap case
  (ADR-039).

One new register input goes to ticket 33: ASVS 2.3.3 (L2). The session half of each ADR-037 trigger cannot be atomic
with its state change.

---

## Input from ticket 33 (register)

The register exists: [`docs/register/register.md`](../../../docs/register/register.md), **324 rows** with IDs
`R-<PILLAR>-nnn`. Grade completeness against it, not against the ADR set. Both renderings are projections of it
(its "Renderings" section); the operational one is the **95** rows with a `sequence`.

- **22 rows are graded `fail`.** Most are deliberate and carry their reason. Six are `application / unmitigated`,
  which means nothing enforces them yet: R-ADM-011, R-AUD-018, R-AUD-019, R-CFG-019, R-FE-002, R-FE-005. Three of
  those have no owning decision anywhere on the map:
  - **R-AUD-018**, the forced-change expiry password oracle. Graduated to [ticket 44](44-forced-change-expiry-password-oracle.md), which resolved it. The row is now `pass` / `asserted-by-test` (T-ADM-015, T-ADM-030), so it has left this list.
  - **R-FE-002**, IM8 dp-8 classification labels on every input field. It binds regardless of the user-population
    declaration, and no frontend decision mentions it.
  - **R-AUD-019**, ASVS 16.3.3 (L2)'s second limb. The two event families (input-validation and business-logic
    bypass attempts) have no catalogue rows and no tests.

  Per this ticket's own rule, a FAIL here reopens the owning design ticket.
- **R-OPS-001:** the IM8 risk classification is not declared anywhere. `im8-review` derives every severity from
  it.
- The register carries three notes you need while grading: the adopted Reading B of NIST §4.2.1 (R-CRED-019), ASVS
  citation hygiene (R-STD-035) and the drift family (R-BLD-006). It also carries the deployment-assumption header
  (R-OPS-005), which is why an empty `priority` on a `none` row is not an omission.

## Input from ticket 35 — three register rows to correct before grading

Ticket 33 is resolved, so these land here. Ticket 18 grades PRD and standard deviations against the register, and
these rows would mis-grade. The register is generated (ADR-069), so fix the source row, not `docs/register/register.md`.

- **R-CRED-013 (graded `deviation` / `fail`):** the Standard provides a self-service change endpoint (§2 Happy Path
  step 13, §2 Decision Logic, §3.1, §5 Self-Service Password Change Tests). §2:121 bars changes through generic
  profile updates, and §4:401 constrains reset endpoints, not change. The endpoint's existence complies. The genuine
  deviation is the forced-change current-password rule, already R-STD-025. See ADR-008.
- **R-STD-024 ("redemption versus account state is undefined"):** §2 Decision Logic line 132 says an admin-reset
  locked account "remains locked until the lock expires or the administrator explicitly unlocks it". Clearing the
  lock at redemption of an admin-issued token is a deviation, not a gap-fill. See ADR-009.
- **R-CRED-004 (no pepper):** check whether its rationale rests on "a peppered hash cannot be rotated" alone. That
  holds for keyed hashes only. NIST §3.1.1.2 also allows an encryption pass, which can rotate. ADR-004 now gives the
  grounds for declining that variant too.

---

## Answer

**The plan passes the gate. Nothing reopens, no ticket graduates, and `/to-spec` can run.** Every inbound item is
fixed at source. The register's `fail`, `partial` and `unmitigated` rows were put to the `policies` question. Two IM8
FAILs remain, and both sit outside PRD scope, so the amended rule accepts them as recorded residuals: ac-3 (R-ADM-011)
and ac-4's review half (R-ADM-001, partial). The last three fog patches are cleared or ruled out of scope.

The register is now **335 rows** and the test plan **361**. Every `R-`, `T-`, `ADR-` and `REJ-` ID cited anywhere
under `docs/` or in `CONTEXT.md` resolves. Nothing cites a ticket or `.scratch/`, and every row passes the column
vocabulary. The register is the source table, so it was amended by ID. Ticket 35's "fix the source row, not
`register.md`" misread ADR-069: `register.md` *is* the source, and only the two renderings are generated.

### 1. Inbound items, all fixed at source

- **Register corrections.**
  - From ticket 35:
    - R-CRED-013 is regraded `fail` → `pass` (kind `note`). The Standard provides the endpoint.
    - R-STD-024 is regraded from undefined-case to `deviation`. The Standard says an admin-reset account stays
      locked.
    - R-CRED-004 now gives ADR-004's grounds for declining the rotatable encryption-layer pepper.
  - From ticket 41:
    - R-ADM-002 now says `user_id`.
    - R-ADM-015 now separates the four-path lock set from the three-path count. R-ADM-008 was already correct.
  - From ticket 42:
    - R-CFG-007 and R-CFG-012: IM8 as-8 names no annotation. Only `im8-review` greps for `@Value`.
    - R-HDR-003: a hash source admits a fixed inline script, so only per-response inline script reopens the
      topology.
    - R-SES-004: no CSRF cookie exists.
    - R-OBS-008: the `OTEL_*` endpoint fallbacks.
    - R-CFG-020 already had the header fallbacks.
- **New register rows owed by 36 and 42:**
  - R-SES-012: ASVS 2.3.3 (L2), partial.
  - R-SES-013: 3.3.2 (L2).
  - R-HDR-009: 3.4.3 (L2), with its L3 clause unmet.
  - R-AUD-037: the logging standard's §4 separate-destination deviation.
  - R-AUD-038: 16.2.3 (L2), met by documentation.
- **Test-plan rows added:**
  - T-SES-036: sweep cases for disable, delete and an in-force lock.
  - T-CSRF-009: header-only CSRF.
  - T-MFA-023: the tier-2 disable forces a change and ends sessions. This row covers both ticket 36's item and
    ticket 39's.
  - T-ARCH-006: ADR-065's slice ban, so that rule is now enforced.
  - T-AUD-042 to T-AUD-045: `session.hash` keyed, unknown key degraded, stdout copy, reason codes pinned.
  - T-BLD-010: the CSP meta tag precedes every script, checked against W3C CSP3 §3.3.
- **Test-plan rows amended:**
  - T-CFG-010: the override goes through `SPRING_APPLICATION_JSON`, and its arrival is asserted first. The old row
    passed without testing anything.
  - T-HDR-003: port 5173 and a build-time API origin. The old setup could not pass.
  - T-AUD-014: now cites LOG §3.4.
  - T-AUD-011: the uncited Logback claim is removed. The row does not depend on it.
- **ADR-046's open item is decided.** Setting a user-chosen password clears `credential_issued_at`. This covers
  forced-change completion *and* token redemption. The second case was also exposed, because redemption clears the
  flag (ADR-009) and would otherwise leave a stale issue time for a later tier-2 disable. ADR-046 and ADR-009 are
  amended, and T-ADM-031 is added.

### 2. Decisions taken with the user

- **R-OPS-001:** IM8 risk classification declared **Low Risk**. The IM8 levels cited in the register are normalised
  to the LR column.
- **R-AUD-019:** ASVS 16.3.3's second limb is accepted as an L2 partial. Two new pre-authentication event families
  would be an audit-amplification surface, and V16 has no L1 requirement.
- **R-CFG-019:** `setRequiredProperties` now covers every presence-only required property, so the row is
  `asserted-by-test` (T-CFG-038).
- **R-HDR-010:** ASVS 3.4.6 (L2). The API CSP carries `frame-ancestors 'none'`, and T-HDR-002 is extended to assert
  it.
- **R-FE-005:** the accessibility target is WCAG 2.2 AA, verified by a recorded manual keyboard and screen-reader pass
  before the `browser-test` gate.
- **dp-8: an agreed decision was reversed on a failed premise.**
  - This ticket first graded R-FE-002 as binding, and the user agreed to build labels. The `policies` reference and
    `im8-review` both scope dp-8 to internal applications serving public officers, and N/A for public-facing ones.
  - The "binds regardless of population" premise came from ticket 11 and was never checked.
  - R-FE-002 is now `n/a`, the reversal is recorded in its rationale, and T-FE-028 is dropped.
  - Its reopening trigger is R-ADM-006, the one R-ADM-003 (ac-12) uses, now widened to name both controls.
- **IM8 ac-3:** Option B. It is recorded as an IM8 FAIL and accepted as a residual on its out-of-scope reason: no PRD
  story and no PRD data-model field asks for it. The gate rule above was amended to make that legal. R-ADM-011's
  residual keeps the path to a clean ac-3: a lazy 90-day check at login, using `last_login_at` with a `created_at`
  fallback and a persisted disable in `preAuthenticationChecks`, following ADR-046. ac-4 stays a recorded partial.
- **PRD coverage is unchanged.** The user's `prd-coverage.md` check confirms that none of dp-8, ac-3 or ac-4 touches a
  PRD story. Its three test gaps (the Story 5 body, the Story 8 list fields, the Story 12 empty-database seed) are
  outside this ticket and are not added here.

### 3. What the `policies` pass found beyond the question

Three findings. All were checking what earlier tickets took for granted, as the map's verification rule predicts.

- **ac-7 was misread as a provisioning control.** Its text is Singpass and Corppass. The provisioning text is ac-8.
  R-ADM-005 is now `n/a` on the population declaration, and the wrong citation is removed from R-ADM-009 and
  R-ADM-010.
- **ck-1, ck-2 and ck-4 had gone stale as N/A.** Ticket 01 ruled them N/A because the application managed no keys,
  and tickets 19 and 24 then gave it three. New rows:
  - R-CFG-024: ck-1, pass-with-note.
  - R-CFG-025: ck-2, partial. The tombstone key never retires.
  - R-CFG-026: ck-4, N/A at LR, per `im8-review`'s own risk level.
- **IM8 controls met by design had no surviving record.** Once `.scratch/` is deleted, nothing would state their
  disposition. R-OPS-011 records as-1, as-2, as-3, as-12, as-14, lm-15, lm-19 and ga-8, checked by the build-phase
  `im8-review` run.

About sixty rows are grade A: an accepted residual or deviation with its reason stated. Two findings are cleanup, not
grading:

- **Staging IDs had leaked into the register.** Ten references used consolidation keys (`SC-037`, `SC-054`,
  `SC-041` and others) that die with `.scratch/`. That breaks the survival rule, and ticket 38's grep did not look
  for that shape. Each is replaced by the `R-` ID the consolidation build mapped it to. The user's instruction to
  reuse "SC-037" is honoured as R-ADM-006, which is what SC-037 became.
- **Ten rows state fewer levels than requirements**, against the register's one-level-per-requirement rule:
  R-ADM-008, R-AUTH-002, R-CFG-005, R-CRED-020, R-CRED-021, R-CRED-024, R-CRED-025, R-MFA-016, R-SES-007 and
  R-STD-049. Each carries one shared modality, so nothing is misgraded. They are left for `/to-spec` to expand, not
  guessed at here.

### 4. Fog

- **Owner notification mechanics:** ruled out of scope, because there is no mail transport. The SHALL failures it
  leaves are already rows (R-CRED-022, R-CRED-024, R-MFA-019), and R-CRED-026 is the trigger that brings the patch
  back.
- **Accessibility target:** decided here (R-FE-005).
- **Rate-above spikes and ratios:** cleared. R-OBS-004 holds them as deployer-owned thresholds.

### 5. The gate list

The gate list is in "The gates the build must pass" above. `/to-spec` carries it into the spec as written.

### Handover items (ticket 25)

- **The deployed static host sends `frame-ancestors 'none'` on the document.**
  - Discharges: ASVS 3.4.6 (L2), R-HDR-010.
  - The application enforces it on API responses and in dev and preview, but not on the deployed document.
  - Proof: the deployed document response carries the header. This is already an acceptance check on R-HDR-005.
- **Run the yearly key-rotation procedure and record it.**
  - Discharges: IM8 ck-2 (LR 2), R-CFG-025.
  - The application enforces versioning and startup fingerprints, not the interval.
  - Proof: the rotation record and the new key fingerprints logged at startup.
- **Record a manual keyboard and screen-reader pass against WCAG 2.2 AA for every route before acceptance.**
  - Discharges: R-FE-005.
  - Nothing is enforceable in the application.
  - Proof: the recorded pass.
  - It is a build-phase obligation on the implementer, not an operator task. It is listed here so extraction finds
    it.

Status: resolved.
