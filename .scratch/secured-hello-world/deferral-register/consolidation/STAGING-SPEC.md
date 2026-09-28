# Ticket 33 staging spec (scaffolding; dies with `.scratch/`)

Ticket 33 builds the single source table behind the deferral register and the handover document. This file tells
a staging agent exactly what to produce. **Do not edit any `issues/` file, `map.md`, `docs/` or the inventory.**
Line numbers must stay stable; pointers are appended in a later phase by script.

Paths below are relative to `.scratch/secured-hello-world/` unless they start with `docs/`, which is the repo's
`docs/` folder (`../../docs/` from here).

## Inputs every agent reads first

1. `issues/33-canonical-register-and-handover-table.md` (the ticket).
2. `issues/17-deferral-register-and-adrs.md` lines 180–230 (row schema, drift family, adopted reading, citation
   hygiene) and its `## Answer` §0–§1 (the no-`NN:line` rule and routing by destination).
3. `issues/25-operational-handover-document.md` lines 461–588 (§1–§5: actors, renderings, schema, the four worked
   rows, the deployment sequence). **The four worked rows in 25 §4 are the model for every row you write.**
4. The **cross-file effects** section (§3, or however titled) of **all seven** inventory parts
   `deferral-register/inventory/part-A.md … part-G.md`. Apply every effect that targets a row you stage.
5. `adr-routing/routing.md` §2 (ADR set, with `also → register/handover` columns), §3 (rejection log with
   destinations), §4 (drops), §5 (hand-offs to 33) and §6 (PRD deviations). Use them to fill `refs` with
   `ADR-nnn` / `REJ-nnn` IDs: the routing rows are keyed `part:id` (e.g. `C:10-A-7`), matching inventory ids.
6. `docs/test-plan/test-plan.md` — grep it for the control to find `T-…` IDs for `asserted-by-test` rows.

Then your assigned items (below the spec, in your prompt).

## What a row is

One row per **register item**: a deviation from a standard or from the PRD, a residual or accepted risk, an N/A,
a not-built deferral, a fidelity item (the harness or environment cannot exercise it), a standards/corpus defect,
a deployer/operator obligation, a reopening trigger, or a note the register must carry (an adopted reading, a
citation rule). Inventory kinds `register`, `handover` and `trigger` are your inputs.

- **Read the source ticket at the cited lines, every time.** The inventory gist is ≤25 words and was written by a
  different pass. The row states the decision as the ticket (after all amendments) finally stands. A remembered or
  summarised decision is an unverified fact.
- **Apply supersessions before writing.** If a later line (in any ticket, or a cross-file effect) supersedes,
  withdraws, narrows or corrects the item, write the row in its final form, or, if nothing survives, do not write a
  row and list it under `## Not staged` with the superseding `NN:line`.
- **Merge inside your assignment.** If two assigned items are the same register fact, one row, both inventory ids
  in `inv`, all refs in `source`. Where you suspect a twin outside your assignment, still write the row and put
  `≡? <inventory id or NN:line>` in `dup`.
- **Not a register item** (it is purely a spec fact — an endpoint, a code, a column — with no deviation, residual
  or obligation): list under `## Not staged`, reason `spec`.

## Row format (Markdown pipe table, exactly these 20 columns, in this order)

`| key | inv | source | pillar | kind | requirement | level | verdict | subject | decision | rationale | residual | responsibility | status | priority | sequence | acceptance | refs | dup | note |`

No `|` inside any cell (use `/` or "or"). One physical line per row.

**Scaffolding columns** (stay in `.scratch/`, never reach `docs/`):

- **key**: `S<stage>-<nnn>` in reading order, e.g. `SC-001`.
- **inv**: inventory ids, `part:id`, `;`-separated, e.g. `C:10-R-1; F:24-R-3`. For items with no inventory row
  (ticket 25 back-fill, PRD deviations from routing §6), write `25:<line>` or `routing§6`.
- **source**: every `NN:a` or `NN:a–b` ref of the item in the issue files, `;`-separated, **primary item first**.
  Use exact current line numbers. A pointer will be appended to the last line of every range, so cite the lines
  where the item is actually stated, not a whole section.
- **dup**: empty or `≡? …`.
- **note**: anything the merge needs: supersession applied, old standards-defect ordinal ("ticket 10's
  seventh"), a judgement call. May be empty.

**Table columns** (these reach `docs/`, and the no-`NN:line` rule binds every one of them):

- **pillar**: one of `AUTH SES CSRF LCK RL CRED ADM MFA AUD HDR CFG RUN OBS FE BLD DATA OPS STD`. Chosen by the
  control concerned, same guidance as the test plan (AUTH login/envelope/enumeration; SES sessions/cookies; CSRF;
  LCK lockout, ladder, caps; RL rate limiters, budgets, source keying, shedding; CRED registration, activation,
  reset, change, password policy, hashing, tokens; ADM admin module, guard, roles, bootstrap, tombstone; MFA TOTP
  and factor; AUD audit and log content, retention, destinations; HDR headers, CSP, CORS; CFG configuration,
  secrets, keys, validators, profiles; RUN rebinding runner; OBS metrics, health, tracing, alerting; FE frontend;
  BLD build gates, dependency scanning, test harness; DATA schema, migrations, portability seams). Two extras:
  **OPS** for deployment and platform obligations with no application control (TLS termination, time sync,
  mail transport, log forwarding, proxy topology); **STD** for **every** standards or corpus defect (kind
  `defect`), whatever it concerns. Defects are numbered in one global sequence by the build; never write an
  ordinal ("seventh", "defect 8") into a table column, only into `note`.
- **kind**: `deviation` | `residual` | `n/a` | `not-built` | `fidelity` | `defect` | `obligation` | `trigger` |
  `note`.
- **requirement**: the requirement or clause the row is graded against, primary form. Examples:
  `ASVS 6.3.8`, `NIST SP 800-63B-4 §3.2.2`, `Std §3.2`, `Std §5:452` (Standalone standard section and line),
  `MFA_Core §4.1`, `Logging Std §3.4`, `IM8 as-10`, `IM8 lm-16`, `PRD Story 1 AC2`, `PRD §Out of scope`,
  `WSTG-ATHN-03`, `RFC 9457`, `CVE-2026-22753`. Several may be `;`-separated. **Every PRD deviation's requirement
  is the PRD story and acceptance criterion.** Triggers: the `ADR-…` or `R-…`-less decision they reopen, stated in
  words (the build cross-links). Defects: the defective clause.
- **level**: ASVS level `L1`/`L2`/`L3` for ASVS requirements (mandatory whenever ASVS is cited, multiple
  `;`-separated in requirement order); otherwise the modality: `SHALL`, `SHOULD`, `MAY`, `MUST`,
  `Enforced Constraint`, `PRD`, `IM8 (sev a | b)` where a severity is known, or `—`.
- **verdict**: `pass` | `pass-with-note` | `conditional-pass` | `partial` | `fail` | `n/a` | `satisfied-by-procedure`
  | `—` (defects, triggers, notes, pure obligations with no graded requirement). Grade honestly and exactly as the
  ticket grades it. ASVS 5.0 uses no RFC 2119 modals, so never back-translate ASVS into SHALL. ASVS V6.5 prose cites
  NIST 800-63-3: do not cross-walk ASVS onto 63B-4 section numbers.
- **subject**: ≤12 words naming the thing (`Admin-create issues an invite token`).
- **decision**: what we did or what the item states, one or two sentences.
- **rationale**: why, **standing alone**, with **primary citations** (standard §, ASVS ID with level, NIST §, RFC,
  CVE, vendor docs with version, e.g. "Spring Session 4.1 `JdbcIndexedSessionRepository`"). Where the argument
  lives only in `research/`, open the research file and carry the primary citation into this cell; `research/` is
  deleted at handoff.
- **residual**: what remains after the decision, or `none`.
- **responsibility**: `application` | `deployer` | `shared` | `none` — who acts.
- **status**: `enforced` | `enforced-elsewhere-cited` | `asserted-by-test` | `procedural` | `unmitigated` —
  **scoped to the application's own enforcement only**. `asserted-by-test` requires at least one `T-…` in `refs`
  (grep the test plan; if none exists, use another status and say so in `note`).
- **priority**: `blocking` | `required` | `recommended`, and **empty if and only if responsibility is `none`**.
- **sequence**: the deployment-sequence step from 25 §5 for every row in the operational view:
  `1` keys and generation commands, `2` trusted-proxy configuration, `3` time synchronisation, `4` headers and
  origins, `5` mail transport, `6` log forwarding and retention, `7` recovery rehearsal. **Required (1–7) when
  responsibility is `deployer` or `shared`.** `L` for a limitation row with responsibility `none` that the
  operational view must still show (25 §4's fourth worked row). Empty otherwise.
- **acceptance**: how a reader proves the row holds (a test, an inspection, an attestation), or exactly the
  sentinel `none possible — attests a named role is filled and its holder reachable.` Every row has one. A
  vacancy (nobody named for a role) is `responsibility: deployer`, `priority: blocking`, acceptance "the role is
  filled and its holder is reachable" — an unmet check, never a fourth responsibility value.
- **refs**: surviving IDs only: `ADR-nnn`, `REJ-nnn`, `T-PILLAR-nnn`, `;`-separated. (Cross-refs to other register
  rows are added by the merge as `≡`/`see` keys; you may write `see <key or inventory id>` in `note`.) When routing
  §2 says an ADR's `also →` includes this register item, or §3 says a REJ line's destination is "register", put
  that ADR/REJ ID here.

### The no-`NN:line` rule, mechanically

No table column may contain: a ticket number used as a reference ("ticket 09", "09 §R", "per 23"), any
`NN:line`, a ticket-local ADR number ("08 ADR 6", "ADR 13" meaning ticket 11's list), a `.scratch` path, or a
research file name. Before returning, check your table columns against these regexes and fix every hit:
`\b\d{2}:\d+`, `ticket \d`, `\d{2} ADR`, `\d{2} §`, `§R`, `\.scratch`, `research/`. (Standard line refs such as
`Std §5:452` are primary citations and are allowed. Write them with a single-digit section as the standard does.)
Name mechanisms, not tickets: "the per-IP budget filter", "the rebinding runner", "the two-admin guard".

## Output

Write `deferral-register/consolidation/stage-<STAGE>.md` with exactly these sections:

```
## Rows
<the 20-column table>
## Not staged
| inv | source | item | reason | superseded by |
## Counts
| inventory ticket | items assigned | rows | not staged |
```

`## Counts` must reconcile: every assigned inventory id appears in exactly one row's `inv` or one `Not staged`
line (an id merged into a row counts there once).

Return to the caller: the output path, rows written, not-staged count, and a short list of problems (ambiguities,
contradictions between tickets, items you could not grade, suspected cross-stage twins worth a look).
