# Ticket 33 merge spec (scaffolding; dies with `.scratch/`)

Inputs: `stage-A.md … stage-H.md` in this folder (424 staged rows, format in `STAGING-SPEC.md`), and `index.md`
(one compact line per staged row, sorted by pillar and kind: use it to find twins, then read the full rows with
grep). Output: `dedup.md`. A script (`build.py`) turns staged rows plus `dedup.md` into the canonical table,
mints the R-IDs, renumbers defects and computes the reconciliation, so `dedup.md` must be exact and parseable.

## Output format (three sections, pipe tables, exactly these headers)

```
## Map
| key | action | target | reason |
## Edits
| key | column | value | reason |
## Minted
<the 20-column staged-row table, keys SM-001 upward>
```

- **Map**: every staged key from every stage file appears **exactly once**.
  - `keep`: becomes a canonical row. `target` empty.
  - `merge`: the same register fact as the `keep` (or minted) key in `target` (exactly one target). The script
    unions this key's `inv`, `source` and `refs` into the target. `reason` names the equivalence in words the
    reconciliation prints, e.g. `mail-transport trigger, one event`.
  - `retire`: not a register row after all (superseded, withdrawn, a spec fact, or a note the register must not
    carry). `target` is the superseding `NN:line`. `reason` is one sentence.
- **Edits**: overrides on `keep` or minted rows only. `column` is any table column (`pillar kind requirement level
  verdict subject decision rationale residual responsibility status priority sequence acceptance refs`). Use it
  when the canonical row's field is wrong or when a merged twin carries the better statement: pull the better
  wording in. Values obey the staging spec, including the no-`NN:line` rule. No `|` in values.
- **Minted**: only for a register fact that the checklist below shows is missing from every stage.

## Canonical choice

Prefer the row staged from the ticket that **owns** the decision, and the most final, most precise statement.
Where twins disagree on grading, decide from the sources (read `issues/NN-*.md` at the cited lines: a remembered
decision is an unverified fact) and write the result as Edits on the kept row.

## Rulings already made (apply; do not re-decide)

1. **25 §4's four worked rows are the schema spike and their grading wins** (25:544–568): audit-log read access
   `deployer / unmitigated / required`, sequence 6; who terminates TLS `deployer / procedural / blocking`,
   sequence 4, acceptance the sentinel; break-glass access restoration `shared / procedural / blocking`, sequence
   7, verdict `conditional-pass` with mail transport as its single named prerequisite, ASVS 6.4.1 (L1) now
   attaching to the operator-set password rather than a token; unexercised production config `none /
   asserted-by-test`, no priority, sequence `L`. Every twin of these merges into one kept row each.
2. **The mail-transport trigger is one row** with every consequence in its decision (break-glass pass closes,
   notification SHALLs resolve, containment inversion TM-13, recovery routes gated on a transport property never
   the bean type, tier-2 key space re-argued, batch runner recovery converges on reset, warning-window owner reset
   becomes real).
3. **The red-rehearsal trigger is one row**; the rehearsal-recurrence obligation is a separate row.
4. **The credential-expired audit-reason oracle** (the post-authentication 30-day expiry whose audit reason
   confirms a correct password; staged as a fired trigger in B, C and D1) is **one row, kind `residual`, verdict
   `fail`, `application / unmitigated / required`**, with a note that it is an open design defect owed back to its
   owning decision. Say so in `reason`.
5. **Standards defects**: every standards or corpus defect is kind `defect`, pillar `STD`. A defect and the
   deviation it forces are separate rows only if both were staged and state different facts; otherwise keep one.
   Duplicate defect rows (e.g. the per-account redemption limit, staged in B, C and G3) merge.
6. **Register-wide notes**: Reading B, ASVS citation hygiene, drift family, deployment-assumption header, the L1
   target note, the L2-hooks-adopted note: one row each.
7. **Declared vacancies**: a vacancy row (role unfilled) keeps `deployer / procedural / blocking` and acceptance
   "the role is filled and its holder is reachable" or the sentinel.
8. Distinct facts that share a topic stay separate (e.g. 16.4.2 tamper-proofing versus 16.4.3 separation may be
   one row if every twin states them together; the read-access worked row is separate from separation).
9. Split rows from one inventory id (stage F1) are fine; do not re-merge them.

## Checklist (confirm each is covered by a kept row; mint only if missing)

- `adr-routing/routing.md` §5: every ADR ID listed under "Standard deviations that also have an ADR" should be in
  some kept row's `refs` (add via Edits where a kept row states that deviation and lacks the ref). Every REJ ID
  under "Register-only destinations" and "Handover destinations" should be in some kept row's `refs`.
- `adr-routing/routing.md` §6: every PRD deviation line has a kept row whose `requirement` names the PRD story and
  AC.
- `issues/17-deferral-register-and-adrs.md` "Amendment from ticket 32": nine owed rows (32-R-1…9) present.
- `issues/17-deferral-register-and-adrs.md` "Amendment from ticket 16": §6b not-built rows and §6a fidelity rows.
- `issues/17-deferral-register-and-adrs.md` "Inputs from ticket 28" seven register lines; "Inputs from ticket 29"
  five; "Inputs from ticket 30" TM-08 widened.
- `issues/25-operational-handover-document.md` §11's nine register entries.

## Process

Work pillar by pillar from `index.md`, then across pillars for the listed families (TLS, audit store, mail
transport, rehearsal, runner operator, ASVS 6.1.1, NIST cap residual, source rotation, stubbed email, single
instance, hygiene jobs, retention, notifications). Resolve every `≡?` hint in the `dup` column. Read the stage
agents' `note` cells for flagged problems and resolve what the rulings cover.

Finish with a self-check and put it at the end of `dedup.md` under `## Self-check`: keys in Map = 424, every merge
target is `keep` or minted, counts of keep / merge / retire / minted, and a short list (≤15 bullets) of judgement
calls the resolver should report. Reply to the caller in under 250 words.
