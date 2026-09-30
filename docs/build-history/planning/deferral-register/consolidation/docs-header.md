# Register: deviations, residuals and deployer obligations

This is the single source table behind two documents for the Secured Hello World auth app:

- the **compliance rendering** (the deferral register): requirement, level, verdict, deviation, residual, and an
  anchor into the operational rendering;
- the **operational rendering** (the handover document): what a deployer or operator must do, in deployment
  order, and how a reader proves it was done.

Both are projections of the rows below. Neither is written by hand. The drift test (T-BLD-006) regenerates both
renderings in the Maven `verify` phase and fails when a committed rendering differs from the regenerated output.

## Deployment assumption

**No real deployment of this build exists.** The application runs as two localhost origins over plain HTTP, and
every production-only value is asserted by test and never executed. Two things follow, and a reader must not
mistake either for an omission:

- A row whose `responsibility` is `none` has no `priority`. That is the schema, not a gap.
- Limitation rows (`sequence` `L`) state what nobody can currently prove. On the first real deployment their
  `responsibility` becomes `deployer`, they gain a `priority`, and the table is regraded.

## Rules

- IDs are `R-<PILLAR>-nnn`. They are never renumbered or reused. A deleted row retires its ID.
- Amend this table by ID. Nothing else holds a copy of these rows.
- Every row carries its own rationale and cites primary sources: the standard and section, the ASVS 5.0
  requirement with its level, the NIST section, the RFC, the CVE, or vendor documentation with its version.
  Cross-references use surviving IDs only: `ADR-nnn` and `REJ-nnn` in [`../adr/README.md`](../adr/README.md),
  `T-…` in [`../test-plan/test-plan.md`](../test-plan/test-plan.md), and `R-…` here.
- ASVS 5.0 has no RFC 2119 modals, so no row translates an ASVS requirement into SHALL. ASVS V6.5's prose cites
  NIST SP 800-63-3, so no row maps an ASVS ID onto an SP 800-63B-4 section. See the citation-hygiene note row.
- Every standards or corpus defect is pillar `STD`, numbered in one sequence across all standards.
- The declared target is ASVS 5.0 Level 1, with named L2 and L3 controls adopted where cheap. An L2 or L3
  citation is a per-control claim, never a level claim.

"Std" is the Standalone User Access Control Application Standard. "Logging Std" is the Structured Logging
Application Standard, and `Log_Schema.md` its field schema. "MFA_Core" and "MFA_Frontend/Standalone" are the
MFA standard and its frontend standard. "PRD" is the product requirements document. `Std §5:452` means section 5,
line 452 of the standard.

## Columns

| Column | Meaning |
|---|---|
| `pillar` | The control area: AUTH, SES, CSRF, LCK, RL, CRED, ADM, MFA, AUD, HDR, CFG, RUN, OBS, FE, BLD, DATA; OPS for platform obligations with no application control; STD for standards defects. |
| `kind` | `deviation` from a standard or the PRD; `residual` risk accepted; `n/a`; `not-built`; `fidelity` (the harness or environment cannot exercise it); `defect` in a standard; `obligation` on a deployer or operator; `trigger` that reopens a decision; `note` the register must carry. |
| `requirement` | The clause graded against. A PRD deviation names the PRD story and acceptance criterion. |
| `level` | ASVS level for ASVS requirements; otherwise the modality (SHALL, SHOULD, MAY, MUST, Enforced Constraint, PRD) or the IM8 severity. One entry per requirement, in order. |
| `verdict` | `pass`, `pass-with-note`, `conditional-pass`, `partial`, `fail`, `n/a`, `satisfied-by-procedure`, or `—` where nothing is graded. |
| `responsibility` | Who acts: `application`, `deployer`, `shared`, or `none`. A role nobody fills is a `deployer` row with `priority` `blocking` and an unmet acceptance check, never a fifth value. |
| `status` | **The application's own enforcement, and nothing else**: `enforced`, `enforced-elsewhere-cited`, `asserted-by-test` (with a `T-…` in `refs`), `procedural`, or `unmitigated`. |
| `priority` | `blocking`, `required` or `recommended`. Empty if and only if `responsibility` is `none`. |
| `sequence` | The operational rendering's position: 1 keys and generation commands, 2 trusted-proxy configuration, 3 time synchronisation, 4 headers and origins, 5 mail transport, 6 log forwarding and retention, 7 recovery rehearsal; `L` limitation. Required on every `deployer` or `shared` row. The order follows the application's own failure points, not severity. |
| `acceptance` | How a reader proves the row holds, or the sentinel "none possible — attests a named role is filled and its holder reachable." |
| `refs` | `ADR-…`, `REJ-…` and `T-…` IDs. Where a decision has both a row here and an ADR, each names the other. |

## Renderings

- **Compliance rendering:** every row, as `ID`, `requirement`, `level`, `verdict`, `kind`, `decision` (the
  deviation), `residual`, and an anchor to the row's entry in the operational rendering when `sequence` is set.
- **Operational rendering:** rows with a `sequence`, grouped by step 1–7 then `L`, as `ID`, `decision` (what to
  do), `responsibility`, `priority`, `status` and `acceptance` (how to prove it). Severity appears only as
  `priority`; the grouping is deployment order.

## Table

