# 38 — Make `docs/` stand alone

Type: task (AFK)
Status: resolved
Blocked by: 33, 34
Blocks: 18

## Question

After `.scratch/` is deleted, does anything under `docs/` still point into it? Rewrite every surviving artefact
so that the answer is no. This ticket applies the no-`NN:line` rule (17 Answer §0) to the artefacts that already
exist, or that arrive by move.

## Scope

1. **`docs/test-plan/test-plan.md`**
   - **`source` column.** All 349 rows hold `ticket:line` provenance. Its job, reconciling the transcription, is
     done, so **remove the column**.
   - **The traceability gate parses this table**, so the gate needs to change first:
     - check the parser contract in the `T-BLD-…` rows before removing anything;
     - amend that contract by row ID;
     - update the table's own legend.
   - **`clause` column.** 273 cells carry ticket-local references such as "08 §5" or "21 ADR 7". Rewrite each
     one to a primary clause (standard §, ASVS ID with level, NIST §, CVE) or to a surviving ID (`ADR-…`, `R-…`).
     Use ticket 34's routing and ticket 33's table to map them.
   - **New `rationale` column.** Fill it from ticket 34's test-plan-rationale routes, one standalone sentence per
     row that has one, and leave it empty elsewhere.
   - **Rewrite the header prose**, which names tickets 16 and 32.
2. **`.scratch/secured-hello-world/threat-model/` moves to `docs/threat-model/`.** Why it survives is in 17
   Answer §0. De-scratch the report and the Threat Dragon JSON the same way: threat-to-ticket citations become
   `ADR-…`, `R-…` or `T-…` IDs.
3. **`docs/api/error-contract.md`**, if it exists by then, and any other file under `docs/` not written by
   tickets 33–37.
4. **A final sweep** over all of `docs/` and `CONTEXT.md`.

## Done when

- A grep of `docs/` and `CONTEXT.md` for `\b\d{2}:\d+`, `ticket \d`, `\d{2} (ADR|§)` and `.scratch` returns
  nothing, apart from matches listed as false positives with a reason for each.
- The test-plan legend matches the new columns, and the parser contract rows are amended.
- `docs/threat-model/` exists and its old `.scratch/` copy is removed.

---

## Input from ticket 34

Ticket 34 is resolved. Its routing is in [`adr-routing/routing.md`](../adr-routing/routing.md):

- **§7** gives the standalone sentence for 30 rows of the new `rationale` column. Carry each sentence's primary
  citation in with it. §7's verification note says why.
- **§8** maps the 13 ticket-local ADR references in the `clause` column to `ADR-…` or `REJ-…` IDs. Every other
  `NN ADR k` is found as `NN-A-k` in §2 or §3.
- **A new surviving ID prefix, `REJ-nnn`**, names rejection-log lines in `docs/adr/README.md`. Treat it like
  `ADR-…` in the final sweep.
- `docs/adr/README.md` already exists and passes this ticket's grep.

---

## Input from ticket 33

Ticket 33 is resolved. [`docs/register/register.md`](../../../docs/register/register.md) exists and already passes
this ticket's grep: no `NN:line`, ticket reference, ticket-local ADR number or `.scratch` path. Its R-IDs are the
surviving targets for rewriting ticket-local references in `clause` cells and in the threat model.

- Map a source line to its R-ID using the pointers appended to every consumed source line: "*Consolidated into the
  register (ticket 33): R-…*". The full per-line list is in
  [`deferral-register/consolidation/pointers.md`](../deferral-register/consolidation/pointers.md). Its staged-key
  → R-ID table is in [`reconciliation.md`](../deferral-register/consolidation/reconciliation.md).
- One reference is known to dangle. R-AUTH-003 points at `docs/api/error-contract.md`, which does not exist yet.
  Item 3 of your scope already covers that file "if it exists by then". If it still does not exist, rewrite the
  row by ID.

---

## Answer

**No. After this ticket nothing under `docs/` or in `CONTEXT.md` points into `.scratch/`**, and the done-when grep
returns only the false positives listed in §5. Scaffolding for the work is in
[`de-scratch/`](../de-scratch/) (`transform.py`, the two manual clause batches, `rationale.md`) and dies with `.scratch/`.

### 1. `docs/test-plan/test-plan.md`

- **The parser contract was checked first.** T-BLD-007 and T-BLD-008 were the only contract rows, and neither fixed
  the columns. That gap was worth closing before a column was removed and one added, because a positional parser
  would misread `polarity` without saying so. **T-BLD-008 is amended by ID:** `verify` also fails when the header is
  not exactly `ID | pillar | control | assertion | level | context | isolation | clause | rationale | polarity`. A
  new rule says a column change amends T-BLD-008 and the parser in the same change.
- **`source` is removed. `rationale` takes its place**, so the table still has 10 columns and `polarity` stays at
  index 9.
- **`clause`: 288 cells rewritten**, up from the 273 the ticket counted, because rows were added after it was written.
  The table now has 350 rows. The rule was:
  - Keep every primary clause.
  - Replace each ticket-local token with the surviving IDs that hold its decision: `ADR-`/`REJ-` from routing §2, §3
    and §8 plus the written ADR files, then the `R-` rows whose refs name the row.
  - 40 rows had nothing left after that. Two subagent batches mapped them against the source lines and the
    standards' text, citing file and line. Abbreviations were checked too: `STD §2.5/3.4/4.1` are MFA_Core, and
    `Recipe 9/10` are the MFA_Core reimplementation recipes. Both are now written `MFA §` and `MFA Recipe`, and the
    legend defines them.
  - Every cited ID resolves (script check: 0 dangling).
  - Cost: some cells are long, up to 12 IDs on T-LCK-011, because every register row that cites a test is listed.
- **`rationale`: 30 rows from routing §7, each carrying its primary citation.** Five were reworded beyond fit
  because the source said something else: T-ARCH-005, T-FE-017, T-AUD-025, T-ADM-022, T-CFG-024. The reasons are in
  `de-scratch/rationale.md`. Two citations were verified here:
  - RFC 6265 §5.3 step 3 (Max-Age sets the persistent flag), for T-CFG-032.
  - Spring Boot 2.2.0-M4 release notes (Tomcat MBean registry off by default), for T-OBS-005.

  One claim is still uncited: T-AUD-011's "`totalSizeCap` arithmetic reported inconsistently". It rests on R-AUD-010,
  which names no Logback source either.
- **Header prose and legend rewritten.** Tickets 16 and 32 are gone. `Context` cites ADR-065/ADR-067, and the gate
  cites ADR-068.
- **Fifteen assertion cells cited tickets outside the columns the ticket named.** They have been rewritten:
  - "ticket 09's", "ticket 26's" and "08:522";
  - `S29-01` is now T-SES-026, and `S30-05` is now T-ADM-027;
  - "09's budget table" and "(11)/(09)/(13)".
- **Finding, T-AUTH-007:** omitting `WWW-Authenticate` on 401 breaks RFC 9110 §15.5.2's MUST (checked at
  rfc-editor.org), and no register row recorded it. **R-AUTH-005 is added** as a deviation. No ADR, because nothing
  here passes the filter.

### 2. Threat model

`docs/threat-model/report.md` and `secured-hello-world.json` now exist, and the `.scratch/` copy is gone.
- Ticket citations became ADR/REJ/R/T IDs.
- The verification-asset link became a list of the primary sources it cited.
- The header links ADR-064.
- The JSON parses, and all 86 and 80 IDs cited in the two files resolve.

A subagent did the rewrite. This session checked it by grep, ID resolution and a read of the header, not line by
line.

### 3. Other `docs/` files

`docs/api/error-contract.md` still does not exist. It is a build artefact. **R-AUTH-003 is rewritten by ID** to say
that, to name ADR-031 and the enum's generated schema as the contract, and to add T-AUTH-011 to its refs.

### 4. Final sweep

The sweep covered `docs/` (74 ADRs, README, register, test plan, threat model) and `CONTEXT.md`. It used the four
done-when patterns plus `issues/`, `research/`, inventory keys, "ticket", `§R`, `NN's` and `S\d\d-\d\d`. The ADRs,
the README, the register and `CONTEXT.md` needed no change.

### 5. False positives

- `\d{2} §`: every remaining hit is an RFC or APNIC section, such as "RFC 6585 §4", "RFC 9110 §15.5.2" or
  "APNIC-114 §10.1".
- `\b\d{2}:\d+`: one hit, `MFA Recipe 10:710` (recipe 10, line 710 of the MFA_Core recipes) on T-MFA-016.
- `row NN` / `row 46's`, in the test plan, register and threat model: these are audit-event catalogue row numbers,
  which the spec carries. They are not tickets.

### Handover items (ticket 25)

None.
