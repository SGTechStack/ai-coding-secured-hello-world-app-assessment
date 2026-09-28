# 32 — Transcribe the test-plan table

Type: task (AFK)
Status: resolved
Blocked by: 16
Blocks: 17, 18, and the `/to-spec` handoff (the map's destination)

## Question

[Decide the test plan](16-test-plan.md) settled the decisions:
- the row schema, levels, context configurations and isolation rules;
- the fidelity list;
- the traceability gate.

It deliberately left the rows themselves to a fresh session. This ticket produces the canonical, machine-readable
test table that ticket 16's traceability gate parses.

## Inherited clause, word for word from ticket 16's original "Done when"

> Every in-scope control has a named test, the time-control and timing-assertion approaches are
> decided, and the Playwright question is answered either way.

The second and third clauses were discharged by ticket 16. The first is this ticket's.

## What to do

- Read each source list **in full**. Sources:
  - ticket 16's body amendments;
  - the owed-test sections of tickets 01–15 and 19–31;
  - Standard §5, PRD §Testing, `MFA_Core` §5, and Logging §5.
- Write one row per test into the asset path fixed by ticket 16's Answer §2, using the schema there. IDs:
  - carry a pillar prefix;
  - are never renumbered or reused;
  - are one per row, with exactly one pillar per row.
- Leave a `superseded by T-…` pointer in every source list that is transcribed.
- Apply ticket 16's supersessions and restatements. The list is in ticket 16's Answer §9.

## Done when

- **Exact per-source reconciliation**, recorded as a table: `source ticket/section → n tests → n IDs`, with every
  deduplication named (`09§R-5 ≡ 21:624 → T-…`). Approximate counts ("about 165–170") do not satisfy this.
- **Zero `KEY:` placeholders** remain in the table. The names now exist in ticket 09's amendment from ticket 16,
  so this is a substitution, not a naming job.
- **An ID has been minted** for every "ticket 16 owns a binding test" line created by ticket 16's amendments to
  01, 05, 06, 08, 09, 11, 14, 17, 18, 25 and 29.
- **Every §5 row is either a T-ID or cited to a register row in ticket 17.** There are no silent omissions.

## Inputs added after ticket 16 closed

Each of these needs a T-ID.

- **Body cap** (ticket 09's amendment from 16, item 3):
  - the filter-order assertion: per-IP budget filter, then body-cap filter, then Route C converter;
  - a chunked over-cap body with no `Content-Length` gets 400 `VALIDATION_FAILED`;
  - an over-cap body with an understated `Content-Length` gets 400 `VALIDATION_FAILED`;
  - an over-cap request still spends its per-IP token.
- **`/api/admin/roles/**` `denyAll()`** (ticket 11's amendment from 16, §5:425):
  - anonymous gets 401;
  - USER and ADMIN get 403 with `code` `ACCESS_DENIED`;
  - mutating methods carry a valid CSRF token;
  - the ADMIN holds no TOTP factor;
  - fixtures have `forcePasswordChange` clear.
- **Traceability gate fails closed** (ticket 16 Answer §2): one test for a missing or unreadable `test-plan.path` file and one for a file that parses zero rows. Both must fail the build. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RL-012, T-RL-013, T-RL-014, T-RL-015, T-ADM-019, T-ADM-020, T-BLD-007, T-BLD-008. Amend the table by ID, not this list.*

---

## Answer

**The table exists.** It has 349 rows, each with one stable ID, at [`docs/test-plan/test-plan.md`](../../../docs/test-plan/test-plan.md), the path ticket 16 §2 fixed. It uses the §2 schema: ID, pillar, control, assertion, level, context, isolation, clause, source and polarity. The audit trail is in [`test-plan/transcription/`](../test-plan/transcription/reconciliation.md): the staged rows, the dedup map, the standards mapping, an independent review and the reconciliation.

### How it was produced

1. **Extraction.** Every source ticket was read in full, and every item a ticket says is owed, handed over or "gets a T-ID" was staged in the §2 schema: **460 staged tests** from 01–16 and 19–32. Items a ticket explicitly calls not owed are listed per ticket under "Not transcribed", with the reason. That covers out-of-scope items, "cannot implement" items, acceptance checks, rehearsals and superseded items.
2. **Deduplication, 460 → 316.** 146 staged rows merged into the row of the ticket that owns the decision, and 6 were retired. Ticket 16 §9's supersessions and restatements were applied as stated. Every merge is named in the reconciliation's Deduplications section, in the form `S16-19 ≡ S27-03…S27-06 → T-OBS-002…`.
3. **Minted, 8 rows.** These are controls the map says are enforced but for which no ticket named a test. This ticket inherits "every in-scope control has a named test", so each got a row:
   - key-material shape;
   - refuse-to-start on missing secrets and origins;
   - the role YAML/table mismatch;
   - the session-miss window binding, which the seam rule requires;
   - `floor`/`lead_days` non-negative;
   - no `CompromisedPasswordChecker` bean;
   - no `WWW-Authenticate` on 401s;
   - audit-strategy position in the login composite (13:453, "no test fails").
4. **Standards and PRD, 33 new rows.** Every prescribed test was mapped: **122 items**. 56 were already covered, 41 needed a new or combined row, and 25 are register rows. **Nine of those register rows did not exist in ticket 17** and are now owed there (ticket 17's amendment from 32). *Consolidated into the register (ticket 33): R-ADM-012, R-ADM-013, R-ADM-014, R-AUD-023, R-AUD-025, R-AUD-026, R-MFA-007, R-OBS-002, R-STD-019. Amend the table by ID, not this list.*
5. **Review.** An independent pass over the finished table found:
   - 1 duplicate;
   - 3 rows contradicting later decisions: `rebind tokens` in the canary list, ticket 05's `/login` and `/csrf` paths, and a validator entry ticket 24 never listed;
   - 5 schema slips;
   - 2 enum codes missing from ticket 06.

   All were fixed before IDs were frozen, or handed to the owning ticket.

### Done-when, checked

- **Exact per-source reconciliation.** One table per source list is in the [reconciliation](../test-plan/transcription/reconciliation.md), plus the summary below. A staged test that merged into several rows (e.g. 16's factor-matrix restatements) contributes several IDs, so a ticket's IDs can outnumber its tests.

  | source | tests transcribed (retired) | IDs |
  |---|---|---|
  | 01 | 7 (0) | 7 |
  | 02 | 3 (0) | 3 |
  | 03 | 6 (0) | 6 |
  | 04 | 27 (4) | 23 |
  | 05 | 14 (0) | 15 |
  | 06 | 8 (0) | 8 |
  | 07 | 6 (0) | 6 |
  | 08 | 23 (0) | 23 |
  | 09 | 30 (0) | 29 |
  | 10 | 11 (0) | 11 |
  | 11 | 14 (0) | 14 |
  | 12 | 18 (0) | 18 |
  | 13 | 23 (0) | 24 |
  | 14 | 28 (0) | 28 |
  | 15 | 8 (0) | 11 |
  | 16 | 81 (1) | 98 |
  | 19 | 2 (0) | 3 |
  | 20 | 6 (0) | 6 |
  | 21 | 15 (1) | 14 |
  | 22 | 16 (0) | 16 |
  | 23 | 10 (0) | 10 |
  | 24 | 27 (0) | 26 |
  | 25 | 6 (0) | 6 |
  | 26 | 15 (0) | 15 |
  | 27 | 6 (0) | 6 |
  | 28 | 14 (0) | 16 |
  | 29 | 15 (0) | 15 |
  | 30 | 8 (0) | 8 |
  | 31 | 12 (0) | 12 |
  | 32 (inputs added after 16 closed) | 1 (0) | 1 |
  | 32 (minted) | 8 | 8 |
  | Standards and PRD test sections (new rows) | 33 | 33 |

  Tickets 17 and 18 owe no tests. Their amendments from 16 are blocking notes and register pointers. The six retirements:
  - 04's three generic-update items: no such endpoint exists (11:209–218; §5:473).
  - 04's role-hierarchy inheritance item: there is no `RoleHierarchy` bean (23:291).
  - 16:205's runner single-use token: ticket 28 removed the token (25:956).
  - 21:615's meter-harness trap: it is configuration that fails every meter assertion if wrong, so it self-detects.
- **Zero `KEY:` placeholders.** The build checked every row. Each key came from 09's amendment-from-16 table (09:1590–1612).
- **An ID for every "ticket 16 owns" line.** These came from 16's amendments to 01, 05, 06, 08, 09, 11, 14, 25 and 29:
  - 06:484's contract test, split into T-AUTH-012 for the fixtures and T-AUTH-011 for the backend bodies;
  - 09's ten binding tests, T-LCK-012 onward and the T-RL binding rows;
  - 11's six §5 rows;
  - 08's replay and cron rows;
  - 14's eleven-test delta;
  - 29's cron binding.

  17 and 18 carry none. The post-close inputs got these IDs:
  - body cap: T-RL-012 to T-RL-015;
  - `denyAll`: T-ADM-019 and T-ADM-020;
  - traceability fail-closed: T-BLD-007 and T-BLD-008.
- **Every §5 row is a T-ID or a ticket 17 register row.** This covers the Standard, the PRD, MFA_Core and Logging, 122 items with no silent omissions. It holds once ticket 17 writes the nine register rows it now owes.

### Superseded pointers

**200 source lists now end with `Superseded by the test-plan table (ticket 32): T-…`.** Four lists got no pointer, because nothing in them was transcribed: 09:1131, 13:794, 24:990 and 24:1058, all superseded by 28 test 9.

The pointers are **appended to each list's last line, never inserted as new lines**. Every `ticket:line` citation on this map, including the table's own source column, still points where it did. A first pass that inserted lines was reverted, and all 200 positions were verified against the original line numbers.

### Judgement calls a reader may disagree with

- **Out-of-cache context.** Tests needing an override no fixed context offers run in the out-of-cache harness (`restart`), as one or two `SpringApplicationBuilder` runs. These are the baggage probe, sampling 0.0, the cron-enabled test, FileStore stubs, dev-without-capture and exact admin counts. Tests that only bind or validate configuration are level U with `ApplicationContextRunner`. Ticket 16 §3 does not add a seventh named context. It already defines this harness as outside the cache.
- **Profile-scoped cookie test.** One test (T-SES-011) covers both profiles, per 20:173. It absorbs 05, 08 and 24's cookie rows, and overrides 24:573's "without refreshing a context".
- **Early inventories.** 04's admin acceptance list survives only where no later ticket covers it:
  - duplicate username or email;
  - actor ≠ subject, parameterised over role change, disable, delete and unlock, since ticket 11 does have an unlock endpoint (11:216);
  - the tombstone written in the same transaction;
  - the terminal `denyAll`.
- **Two assertions are hedged, because no ticket pins them:**
  - T-RL-004: the reset-request per-identifier refusal's response shape.
  - T-CRED-008: the error code for a wrong current password.

  `/do-work` must pin both.

### Amendments posted

- **06:** `FACTOR_ALREADY_ENROLLED` (409) and `FACTOR_DISABLED` (423) are missing from the §2 enum table. Both are lost hand-offs, from 23 and 14.
- **16:** the table's location, plus an erratum. 16:554's "rebind tokens" no longer exist.
- **17:** the T-ID source for `asserted-by-test` rows, and the nine owed register rows.
- **24:** the reset-link logger property is missing from the §9 validator table (13:632; 25:329).
