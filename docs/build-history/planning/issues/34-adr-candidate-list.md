# 34 — Route the ADR candidates and write the rejection log

Type: task (AFK)
Status: resolved
Blocked by: —
Blocks: 33, 35, 36, 38

## Question

For each decision the map owes an ADR for, where does it survive? Each one is routed to at least one of these: a
register row, a test-plan row with a rationale, a handover row, the spec, or an ADR. Which decisions pass the ADR
filter, and how many is that?

The rules are in ticket 17's Answer §0 and §1. This file does not restate them, except for the filter itself:

> **Would a maintainer reading the code and the spec plausibly undo this?** If yes, it gets an ADR, with one
> decision per ADR. If no, it goes to the rejection log.

## Inputs

- The inventory, [`deferral-register/inventory/part-A…G.md`](../deferral-register/inventory/):
  - kinds `adr-new`, `adr-amend`, `adr-reversed` and `adr-rejected`, plus the cross-file effects;
  - 198 live new claims and 91 amendments.
  - The `shape` column is a first read only, and the filter replaces it.
- Ticket 17's seven-item sketch. Items 1 (cookies over JWT) and 2 (Spring Session JDBC) are owed by no resolved
  ticket. That has been checked, and both belong to 36.

## What to do

1. Apply supersessions and reversals first.
2. Group owed bullets into **decisions**: sets that would be reversed together. Amendments attach to their target
   and are never candidates of their own. Worked examples:
   - the BCrypt cluster (02-A-3 / 04-A-2 / 07-A-1 / 17-A-3);
   - 13-A-a ≡ 13-A-3;
   - 15-A-3 ≡ 27-A-1;
   - 04-A-1 superseded by 20-A-1;
   - ticket 11's ADR 13 with its five amendments.
3. Route each decision. Record every destination it goes to:
   - **Register or handover:** hand the deviation to ticket 33 as an input.
   - **Test-plan rationale:** name the `T-…` row and write the standalone sentence for its new `rationale` column.
     Ticket 38 adds the column.
   - **Spec:** name the spec section it belongs to.
   - **ADR:** mint `ADR-nnn`, with a title, merged sources, attached amendments, the filter answer in one
     sentence, and an **owner**, 35 or 36, by the source split in 17 Answer §2.
4. Apply the filter to **every PRD deviation** as well. Every PRD deviation already gets a register row. The filter
   decides only whether it also gets an ADR.
5. Write the **rejection log** for `docs/adr/README.md`. Give each candidate the filter turned away one line: its
   title, where it went instead, and the reason.
6. Append to every consumed source line: "*Consolidated into the ADR routing (ticket 34): ADR-… / R-… / T-….
   Amend by ID, not this list.*"

**The no-`NN:line` rule applies to everything this ticket writes toward `docs/`.** That means the ADR titles and
filter sentences, the rejection log and the test-plan rationale sentences. Nothing in them may cite a ticket,
line, ticket-local ADR number or `.scratch/` path; cite primary sources instead. The `source → ID` reconciliation
stays here, in `.scratch/`.

## Done when

- Every live ADR-kind inventory row ends in exactly one place: a decision, an attached amendment, or an explicit
  drop that cites what superseded it. There are no silent drops.
- Every decision has at least one destination.
- **The resulting ADR count is reported** per owner (35, 36) and in total, with no target. If either owner's share
  will not fit one session, split that ticket as the last act, and wire 18 to the pieces.
- Every consumed source carries the pointer.

## Answer

**The filter produces 74 ADRs: 28 owned by 35 and 46 owned by 36.** It also produces a 92-line rejection log.
Every one of the 311 ADR-kind inventory rows ends in one of four places:

- a decision (a merged source of an ADR);
- an attached amendment;
- a rejection-log line;
- a drop that cites what superseded it.

The full reconciliation, keyed `part:id`, is in [`adr-routing/routing.md`](../adr-routing/routing.md). This
section gives the decisions and nothing it can point to instead.

### 1. The filter reading (a judgement, open to challenge by ID)

17 §1 routes two filter-passing invariants to the test plan, not to an ADR. So "plausibly undo" is read as **undo
and get away with it**:

- **A reversal that a named `T-…` row fails, where one sentence carries the whole reason**, goes to that row's
  `rationale` column. 26 candidates went there, most of them first read as `adr`.
- **An ADR is kept when the reason needs options and consequences, or when nothing stops the reversal.**

Routing §1 states the rule and gives the three outcomes.

### 2. What was produced

| Artefact | Where | For |
|---|---|---|
| ADR-001 to ADR-074: ID, title, merged sources, attached amendments, one-sentence filter answer, owner | routing §2 | 35, 36, 39–43 |
| Reserved index and rejection log (`REJ-001` to `REJ-092`), standalone | [`docs/adr/README.md`](../../../docs/adr/README.md) | survives |
| Register and handover hand-offs | routing §5 | 33 |
| PRD deviations, each filtered | routing §6 | 33, 18 |
| 30 test-plan rationale sentences | routing §7 | 38 |
| 13 ticket-local ADR references mapped to surviving IDs | routing §8 | 38 |
| Row accounting (exact sums per kind) | routing §9 | 18 |

`docs/adr/README.md` passes the no-`NN:line` grep. **`REJ-nnn` is a new surviving ID prefix.** It lets ticket 38
rewrite a `clause` cell such as "08 ADR 5" to something that survives.

### 3. PRD deviations (step 4)

All get a register row. Thirteen pass the filter and have an ADR. One fails the filter: the Story 3 AC3 verdict,
REJ-013, which is a verdict with nothing in the code to revert.

The seven standard-mandated additions beyond the PRD are register rows only. The exceptions are self-service change
(ADR-008) and activation (ADR-032). **The MFA addition is ADR-023**, and no resolved ticket had owed it as a PRD
deviation. Ticket 19's six ADRs cover the MFA scope, but not the fact that the PRD excluded MFA.

### 4. Premises of this ticket that failed

- **"Items 1 and 2 belong to 36" holds, but the owner split did not survive intact.** ADR-072 (the recovery
  runner) has its earliest statement in 09, which belongs to 35. It goes to 36 anyway, because 28 inverted the
  design and 36's scope names the runner. Routing §2 argues it.
- **"Amendments attach to their target" assumed every target is an ADR.** 15 amendments target a rejected
  candidate, a register row, the enum or a schema. Routing §4 routes them with their target.
- **Inventory IDs are not unique across parts.** `11-M-1`, `11-M-2`, `23-M-1`, `23-M-2` and `03-X-1` each name two
  different rows, and G restates `06-A-1…5`. Every key is therefore `part:id`.

### 5. The split, the last act

Neither share fits one session. Thirteen ADRs, each with primary-source verification, is the ceiling used. ADR
writing is now seven tickets. Each blocks only on 34, and 18 blocks on all of them.

| Ticket | ADRs | Scope |
|---|---|---|
| [35](35-adrs-authentication.md) | 001–009 (9) | passwords, hashing, credential tokens |
| [39](39-adrs-lockout-and-throttling.md) | 010–020 (11) | lockout, throttling, source keying |
| [40](40-adrs-mfa-factor.md) | 021–028 (8) | the MFA factor |
| [36](36-adrs-platform.md) | 029–041 (13) | error envelope, sessions, CSRF, both sketch ADRs |
| [41](41-adrs-admin-and-data-model.md) | 042–053 (12) | admin module, data model |
| [42](42-adrs-logging-observability-config.md) | 054–063 (10) | logging, observability, configuration, topology |
| [43](43-adrs-harness-handover-runner.md) | 064–074 (11) | test harness, threat model, handover design, runner |

### 6. Source pointers (step 6)

Every consumed source line now ends with "*Consolidated into the ADR routing (ticket 34): … Amend by ID, not this
list.*" The superseded rows get "*Dropped in the ADR routing (ticket 34)…*" instead.

Pointers were appended to existing lines, so every `NN:line` citation still resolves. Where a cited line was `---`, blank, inside a code fence or partway through a sentence, the pointer went to the
nearest line that closes the cited item. Nineteen pointers moved that way, all within 15 lines of the citation.
Some pointers still sit partway through a sentence, where the cited range ends that way (for example 04:203 and
10:391). They are harmless.

### Handover items (ticket 25)

None. This ticket routes decisions. It creates no deployer or operator obligation.

### Amendments made to other files

- **35 and 36** were narrowed. **39–43** were created.
- **18** is blocked by 33–43.
- **33 and 38** each got an "Input from ticket 34" section.
- **`map.md`** got a Decisions-so-far pointer.
- **`docs/adr/README.md`** was created.
- **Issues 02–31** got source pointers, and **17** got pointers on the lines of its restated lists.
