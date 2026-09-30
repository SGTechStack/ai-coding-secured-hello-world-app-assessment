# 33 — Build the canonical register and handover table

Type: task (AFK)
Status: resolved
Blocked by: 34
Blocks: 18, 38

## Question

What is the single source table behind the deferral register and the handover document? Every row carries
ticket 25's schema and a stable ID, and each source it came from points back at it.

This is ticket 25's "47-row pass" (25:748–751), which ticket 25 pushed to ticket 17's re-split. Ticket 17's
Answer §2 hands it here.

## Inputs

- The inventory: [`deferral-register/inventory/part-A…G.md`](../deferral-register/inventory/). Use the kinds
  `register`, `handover` and `trigger`, and every "cross-file effects" list.
- The row schema: 17:199–205, plus ticket 25 §4's four worked rows as the spike.
- Ticket 25's own content sections. The **back-fill for tickets resolved before the declaration rule** lives
  there (25:832–838). About 25 inventory items routed to 25 outside the exact heading (tickets 10, 11, 20, 21 and
  23, and 14 via 25:808–831) are **de-duplicated against those sections, not added on top**.
- Ticket 17's inherited sections: 25, 15, 16, 28–32 and 06's artefact pointer to `docs/api/error-contract.md`.

## What to do

- Apply every cross-file effect before de-duplicating, so that a superseded or withdrawn row never reaches the
  table.
- Give each row a stable ID `R-<PILLAR>-nnn`. IDs are never renumbered or reused.
- **Every PRD deviation gets a row.** The requirement ID is the PRD story and acceptance criterion. The map's
  conflict rule, as amended by ticket 17, sends PRD deviations here, and ticket 18 grades their completeness here.
  Ticket 34 also hands you deviations it routed to the register.
- **Renumber standards defects in one global sequence.** Ordinals collide: 10 runs seventh–fourteenth, 11 runs
  seven–twelve, and 24 claims "eighth". Record the old ordinals in this ticket's reconciliation only, never in
  the table.
- **The no-`NN:line` rule (17 Answer §0) binds every table row.** Each row's rationale, residual and acceptance
  check must stand on its own:
  - no ticket, `NN:line`, ticket-local ADR number or `.scratch/` path;
  - primary-source citations instead (standard §, ASVS ID with level, NIST §, CVE, vendor docs with version);
  - cross-references by `ADR-…`, `R-…` or `T-…` only.

  Where a row's argument currently lives only in `research/`, carry the primary citation into the row. `research/`
  is deleted at handoff.
- Fill `responsibility`, `status` and `priority`, plus an acceptance check or the sentinel, on every row.
- Cite `T-…` IDs from [`docs/test-plan/test-plan.md`](../../../docs/test-plan/test-plan.md) on `asserted-by-test`
  rows.
- Carry the adopted-reading note on NIST §4.2.1, the citation-hygiene rules and the drift family, as 17:211–227
  states them.
- Append to every consumed source line: "*Consolidated into the register (ticket 33): R-…. Amend the table by ID,
  not this list.*" This follows ticket 32's precedent, and the pointers are appended so that `NN:line` citations
  still resolve.
- **No rejected ADR candidates in this table.** They go in ticket 34's rejection log.

## Done when

- The table exists under `docs/`, with a per-source reconciliation (`source → n items → n R-IDs`, every merge
  named, every drop cited to the line that superseded it). The reconciliation is kept in `.scratch/`, not
  `docs/`.
- A grep of the table for `\b\d{2}:\d+`, `ticket \d`, `\d{2} ADR` and `.scratch` returns nothing.
- The handover population is **computed** as 47 + 9 unowned + later heading items − overlaps, and every term
  in that sum is shown.
- Both renderings are derivable from the table: the compliance view (requirement ID, level, verdict, deviation,
  residual, anchor) and the operational view.
- Every consumed source carries the pointer.

---

## Input from ticket 34

Ticket 34 is resolved, and this ticket is unblocked. What 34 routed to the register and handover table is listed
in [`adr-routing/routing.md`](../adr-routing/routing.md) §5, with the PRD-deviation rows in §6. Most of it already
exists as inventory `register` or `handover` rows, so treat §5 as a checklist rather than new content.

Two things are new:

- **A register row and an ADR can cover one decision.** When they do, each cross-references the other by ID. The
  ADR IDs are fixed in `docs/adr/README.md`.
- **Rejection-log lines (`REJ-nnn`) whose destination is "register"** need a row here. The `REJ` line is not a
  register row, as 17 Answer §1 says.

## Amendment from ticket 43 (platform ADRs)

ADR-064 to ADR-074 name their residuals in words, because no `R-` IDs existed when they were written. When this
ticket mints the IDs, link them back into the ADRs with an in-place edit. The list is in
[43's Answer](43-adrs-harness-handover-runner.md), under "Cross-references left for other tickets". One of the
residuals is **new** and has no inventory row: ADR-066's Caffeine monotonicity regression. It is small, but it is a
recorded deviation from the library default.

---

## Amendment from ticket 36 (platform ADRs)

One register input that routing §5 does not carry: **ASVS 5.0 2.3.3 (L2)**. It asks that a business-logic operation
succeed entirely or roll back. The session kill behind each ADR-037 trigger cannot be atomic with its state change,
because Spring Session JDBC writes under `PROPAGATION_REQUIRES_NEW`. ADR-039's startup reconciliation sweep is the
recovery path. It is L2 against the L1 target, so the verdict is unaffected. ADR-039 says "the register grades it",
so this needs a row.

---

## Answer

**The table exists.** It has 324 rows, each with a stable ID `R-<PILLAR>-nnn`, at
[`docs/register/register.md`](../../../docs/register/register.md). Every row carries ticket 25's schema
(`responsibility`, `status`, `priority`, and an acceptance check or the sentinel), a verdict against a primary
requirement ID with its level, a standalone rationale, and cross-references by `ADR-`, `REJ-` and `T-` ID only. The
audit trail is in [`deferral-register/consolidation/`](../deferral-register/consolidation/reconciliation.md).

### How it was produced

1. **Staging, 424 rows.** Thirteen parallel passes wrote staged rows to one spec,
   [STAGING-SPEC](../deferral-register/consolidation/STAGING-SPEC.md). Twelve covered every `register`, `handover` and
   `trigger` row in inventory parts A–G. Each pass read the source ticket at the cited lines, applied every
   cross-file effect from all seven parts, and wrote the row in its final form. The thirteenth (stage H) covered
   what has no inventory row: ticket 25's pre-rule content sections, the notes ticket 17 requires, every routing §6
   PRD deviation, and the routing §5 REJ checklist. It checked each candidate against the other twelve stages first,
   so it added 26 rows and recorded 106 as covered or superseded.
2. **Deduplication, 424 → 324.** One merge pass, working to [MERGE-SPEC](../deferral-register/consolidation/MERGE-SPEC.md),
   merged 100 rows into the row of the ticket that owns the decision and made 36 field edits. Nothing was retired
   and nothing had to be minted, because every checklist item already had a staged row. Every merge is named in the
   reconciliation.
3. **Build.** [`build.py`](../deferral-register/consolidation/build.py) applies the dedup map, mints IDs and
   validates every row:
   - vocabulary;
   - `priority` empty if and only if `responsibility` is `none`;
   - a `sequence` on every `deployer` or `shared` row;
   - a `T-` ref on every `asserted-by-test` row, and every `T-`, `ADR-` and `REJ-` ref resolving in its file;
   - an ASVS level on every ASVS ID;
   - the no-`NN:line` patterns.

   It fails on any violation. It also writes both renderings, the reconciliation and the handover arithmetic.

### Done-when, checked

- **The table is under `docs/`, and the reconciliation stays in `.scratch/`.** The
  [reconciliation](../deferral-register/consolidation/reconciliation.md) has these parts:
  - a per-source table (`source → n items → n R-IDs`);
  - every staged key → R-ID;
  - every merge, named;
  - every drop (138 not-staged items), cited to the line that superseded it or the row that covers it.
- **The grep returns nothing.** `\b\d{2}:\d+`, `ticket \d`, `\d{2} ADR` and `\.scratch` each have 0 hits in
  `docs/register/register.md`. The build also enforces `\d{2} §`, `§R` and `research/`.
- **The handover population is computed.** The operational view is every row with a `sequence`:

  | term | value |
  |---|---|
  | 25's stated figure (never enumerated) | 47 + 9 = 56 |
  | pre-rule term, computed from 25's own content sections | 54 |
  | later heading items | 49 live items → 41 rows |
  | overlap between the two | 26 |
  | register-origin deployer, shared and limitation rows that neither term carried | 26 |
  | **population** | **54 + 41 − 26 + 26 = 95** |

  By step: keys 8, proxy 8, time 2, headers 7, mail 6, logs 22, rehearsal 19, limitations 23.

  The pre-rule term comes out at 54, not 25's 56. 25's figure was an unrecorded extraction, and it counted verdicts
  rather than deduplicated rows. The difference is stated here, not forced to match.
- **Both renderings are derivable.** The table's "Renderings" section defines each projection. The build produced
  both, as [`render-compliance.md`](../deferral-register/consolidation/render-compliance.md) and
  [`render-operational.md`](../deferral-register/consolidation/render-operational.md), to prove it. They stay in
  `.scratch/`: writing the committed renderings and their `verify` gate (T-BLD-006) is `/do-work`, as 25:759 says.
- **Every consumed source carries the pointer.** 808 source lines gained "*Consolidated into the register
  (ticket 33): R-…. Amend the table by ID, not this list.*" The pointer was appended to the line, inside the last
  cell for table rows. No line was inserted, and every file's line count was verified unchanged. Four secondary refs
  land on a heading or inside a code fence (02:400, 05:786, 08:392, 29:140) and got no pointer. Their items carry
  pointers on their other lines. The full list is in [`pointers.md`](../deferral-register/consolidation/pointers.md).

### What else the ticket asked for

- **Every PRD deviation has a row keyed on its story and AC.** That includes Story 3 AC3 (REJ-013, graded
  `partial` under the adopted state-independence reading) and one row for the beyond-PRD additions.
- **Standards defects are renumbered in one global sequence.** There are 54 `defect` rows, R-STD-001 onward;
  R-STD also holds three register-wide notes. The old per-ticket ordinals (10's seventh to fourteenth, 11's seven
  to twelve, 24's eighth, 08's fourth to sixth, 22's third) appear only in the reconciliation's defect table.
- **The notes ticket 17 requires are rows:**
  - Reading B of NIST §4.2.1, R-CRED-019;
  - ASVS citation hygiene, R-STD-035;
  - the drift family with its 13.1.1 companions, R-BLD-006;
  - the deployment-assumption header, R-OPS-005. The table's own preamble states it too.
- **25 §4's four worked rows are graded exactly as the spike graded them:**

  | worked row | R-ID | responsibility / status / priority | sequence |
  |---|---|---|---|
  | audit-log read access | R-AUD-012 | deployer / unmitigated / required | 6 |
  | who terminates TLS | R-OPS-003 | deployer / procedural / blocking, with the sentinel | 4 |
  | break-glass restoration | R-RUN-001 | shared / procedural / blocking, conditional pass on mail transport | 7 |
  | unexercised production config | R-CFG-004 | none / asserted-by-test, no priority | `L` |

- **No rejected ADR candidate is a row.** REJ IDs appear only as `refs`, on rows whose destination routing §3
  gives as "register".

### What the checking found

- **A reopen trigger that had already fired, with nobody holding it.** It was staged independently three times.
  The 30-day forced-change expiry runs in `DefaultPostAuthenticationChecks`, so its audit reason confirms a correct
  password to anyone who can read the audit log (11:782–799, 13:763–777). It was filed as a trigger on ticket 11,
  its condition is already true, and no ticket discharged it. It is recorded as **R-AUD-018**, `fail`,
  `application / unmitigated`, and **graduated as
  [ticket 44](44-forced-change-expiry-password-oracle.md)**, which blocks 18.
- **Two unowned FAILs,** left for ticket 18 to reopen per its own rule and not graduated here:
  - R-FE-002: IM8 dp-8 labels on every input field. It binds regardless of the population declaration, and ticket
    14 never mentions it.
  - R-AUD-019: ASVS 16.3.3 (L2)'s two new event families. They have no catalogue rows and no tests, which is a
    hand-off lost between 13, 25 and 32.
- **Nobody has declared the IM8 risk classification** (R-OPS-001). `im8-review` derives every severity from it.
- **The TLS row appeared three times with two different verdicts.** It is kept as `partial` (IM8 as-10 WARN/FAIL),
  since 25 §4 gives no verdict word.
- **Stale figures corrected in the rows, not in their sources:**
  - "7×" becomes ≈100× per bucket;
  - "~10 h" becomes 580 min;
  - the log key rotates no faster than the investigation window, not "freely" (25:234 is stale);
  - 28's "up to 240" runner runs are kept, flagged against the corrected 228 × 6.3 h and 504 × 14 h.

### Judgement calls a reader may disagree with

- The pepper decline is graded `pass-with-note` as a declined SHOULD with a stated reason, not `fail`.
- ASVS 6.3.3 is graded `partial`, because MFA covers the admin surface only.
- The declared-L1 note makes every L2 and L3 citation a per-control claim.
- 90-day retention is two rows: the not-built durable store and the platform TTL obligation.
- 16.4.2 read access (the worked row) and 16.4.3 separation are separate rows. Twins that stated both merged into
  the separation row.
- Four rows from ticket 24 were split by verdict (F1 stage), so one inventory id can map to several R-IDs.
- Many `sequence` placements are judgements, because 25 §5 has no build, capacity or recurring-work step. Build
  gates went to 1, monitoring to 6 and recurring work to 7. Each is noted in its staged row.

### Handover items (ticket 25)

None new. This ticket consolidates the handover population and creates no deployer obligation of its own.

### Amendments made to other files

- **18:** blocked by 44; an input section listing the register, the unowned FAILs and the notes it needs.
- **38:** an input section with the register's R-IDs, the pointer list, and one dangling path: R-AUTH-003 names
  `docs/api/error-contract.md`, which does not exist yet.
- **44:** new ticket.
- **808 source lines:** consolidation pointers.
- **`map.md`:** Decisions-so-far pointer.
