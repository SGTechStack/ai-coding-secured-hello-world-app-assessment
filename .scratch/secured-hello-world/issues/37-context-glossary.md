# 37 — Write `CONTEXT.md`

Type: task
Status: resolved
Blocked by: —
Blocks: 18

## Question

What does each term this map pinned down mean? This ticket writes `CONTEXT.md`, which is ticket 17's
Deliverable 3. It is a glossary only, with no implementation detail.

## Inputs

- The inventory's `glossary` rows in [`deferral-register/inventory/`](../deferral-register/inventory/): 79 live
  and 2 withdrawn.
- Ticket 17's own term list at 17:74–78.
- The `domain-modeling` skill's format.

## What to do

- **Resolve collisions by reading both sources.** The known collisions:

  | Term | Collision |
  |---|---|
  | *rebinding* | Defined in both 09 (09:668, 09:1403) and 14 (14:619) |
  | *factor freshness* | Owed by 08, referenced from 23 |
  | *client IP* | Renamed *source key* by 31 |
  | *lockout*, *disable* | Amended in place by 09 §R |

  Where two tickets mean different things by one word, keep both. Name them apart and give each a line saying
  which is which.
- Drop the two withdrawn candidates.
- **No-`NN:line` rule (17 Answer §0).** Definitions stand alone:
  - no ticket or line citations;
  - cross-references by `ADR-…`, `R-…` or `T-…` only, where those exist.

  The `term → source` reconciliation stays in this ticket's Answer, in `.scratch/`.

## Done when

- Every live glossary row is defined, merged or dropped, and the reason is recorded in the reconciliation.
- Every collision is resolved in writing.
- A grep of `CONTEXT.md` for `\b\d{2}:\d+`, `ticket \d` and `.scratch` returns nothing.

---

## Answer

**[`CONTEXT.md`](../../../CONTEXT.md) is written at the repo root: 81 entries in nine groups.** The inventory held 81
glossary rows, 79 live and 2 withdrawn. It is a coincidence that the entry count matches the row count. Of the rows:
- five were merged into another entry;
- four were split into several entries;
- one live row was dropped, along with the two withdrawn rows.

Four entries were added where a collision needed a second word.

The grep in "Done when" returns nothing. A second check confirms all 81 entries parse as `**Term**:` headings.

**How the definitions were checked.** Each definition follows the **latest** text: the resolution plus every
`Amendment from ticket N` section that touches the term.
- **Read directly this session:** 08, 09 (glossary, §R.1–R.8 and the amendments from 16, 30 and 31), 10, 11, 12, 14,
  16, 20, 23 (§§1, 8, 9 and the amendments from 09 and 25), 29 and 31.
- **Read through four `context-gatherer` passes, one per cluster:** 13, 21, 24, 25, 26 and 27, plus the 08/23
  freshness collision.

Those passes cited line numbers, and a spot check against the direct reads agreed.

**What the glossary leaves out on purpose.** Tunable constants stay out, because a glossary that repeats them goes
stale when they change. That covers ladder rungs, budgets, TTLs, `k`, `N_max` and `W`. Three numbers stay because they
are part of what the word means:
- **100** is on both the cap counter and tier-2 disable. The shared number is the point of 23-G-6.
- **30 days** is on the forced-change credential.
- **Ten minutes** is on the re-verification window.

Class names, property keys, table and column names stay out as well, per the `domain-modeling` rule.

### 1. Collisions, resolved

| Word | Senses found | Resolution |
|---|---|---|
| *rebinding* | 09:668 and 09:1111–1116: destroy the credential and bind a new one. This is the NIST §3.2.2 exit from a disable, on the password or the factor. 14:283 and 14:345: the admin factor reset that is the only exit from tier 2. | One umbrella entry, **Rebinding**, plus **Password rebinding** and **Factor rebinding**. 14's sense is the factor case. 09's sense is the umbrella, since §R.3 counts 23's admin `DELETE .../totp` as rebinding. The password instance follows ticket 30's correction: the runner mints nothing, and the operator supplies the password. |
| *factor freshness* | 08:329–336 and 08:477: the factor's age from its grant instant. 23 defers to 08 at 23:934 and puts two policies on the same clock. Admin mutations use a 10-minute bound. Admin reads use a bound equal to the session lifetime, a type guard rather than a time limit (23 §4, which amends the "unbounded" wording at 23:24, 19:185 and 11:20). | One meaning, so **Factor freshness** stays 08's. The policy words 23 relies on get their own entries, **Re-verification window** and **Step-up**, so the definition carries no policy. |
| *client IP* | 09:677–678: "the resolved address the limiter keys on". 31 §1 and 09:1531–1535 move every per-source key to the source key. | Both kept, as input and output. **Client IP** is the resolved address, and it is no longer "what the limiter keys on", which was 09's stale clause. **Source key** is the value derived from it. 31 did not edit 09's glossary line, so this is the reconciliation of 09:677 against 09:1531. |
| *lockout*, *disable* | 09 §R amended both in place, at 09:666–667 and 09:668–674. *Disable* lost its "administrative or policy-driven" clause, which leaves it the same thing as *authenticator disable* (09:675–677). Five "disabled" senses exist on the map: admin account disable, policy disable, authenticator disable, tier-2 disable, and the framework's enabled-and-activated composition. | **Lockout** uses 09's final text: auto-lifting and escalating. The rungs are left out. **Disable** merges into **Authenticator disable**, whose _Avoid_ line bans the bare word. **Account disable** takes the admin sense. **Tier-2 disable** is the factor instance. The framework composition is not a domain word: **Account disable** states that an account signs in only when it is activated and not disabled. |
| *tier 1 / tier 2* (found) | 23 §5: factor lockout levels. 26:284–310: audit-volume keying classes, split by key space. | **Tier-1 lock** and **Tier-2 disable** keep 23's sense. 26's pair becomes **Keying tier**. Each entry carries an _Avoid_ on the bare "tier N". |
| *discriminator* (found) | 13:414–434: the audit field that tells operations apart. 13:756 and 21:406–420: the alert-class test. 14:305–312: the SPA branching on whether a 429 carries `factor`. | **Event discriminator** is 13's sense. **Alert class** is added for the second. 14's sense is a client branch and not a domain word, so it gets no entry, and the bare word is banned. |
| *pin* (found) | 29 §2: an anonymous session's fixed expiry. 31:152–158 and 09:1545–1549: a lockout-set entry's expiry from first insertion. | **Pinned expiry** is scoped to anonymous sessions, and the bare "pin" is banned. The lockout-set sense is not owed a term. |
| *authentication pathway* (found) | 10:579–585: every route to a credential or a session, seven of them. 23:682–697 and 23:1072–1085: routes that grant a factor, three of them, with redemption expressly excluded. | Both kept. **Authentication pathway** takes 23's sense, which is the later one and the one ASVS 6.1.3 and 6.3.4 are graded against. **Credential-setting route** covers the rest of 10's seven, plus the operator rebind. |

### 2. Reconciliation, row by row

Disposition: **D** defined under the same name, **R** defined under a new name, **M** merged into another entry,
**S** split into several entries, **X** dropped.

| Row | Term | → `CONTEXT.md` | Disp. | Reason |
|---|---|---|---|---|
| 08-G-1 | auth instant | Auth instant | D | |
| 08-G-2 | superseded session | Superseded session | D | |
| 08-G-3 | factor freshness | Factor freshness (+ Re-verification window, Step-up) | D | Collision §1 |
| 09-G-1 | Lockout | Lockout | D | Final §R text: escalating duration, rungs left out |
| 09-G-2 | Throttle | Throttle | D | Key is the source key, per 31 |
| 09-G-3 | Disable | Authenticator disable | M | Same thing once §R withdrew the administrative clause (§1) |
| 09-G-4 | Authenticator disable | Authenticator disable | D | |
| 09-G-5 | Observation window | Observation window | D | |
| 09-G-6 | Client IP | Client IP | D | Re-scoped to input only (§1) |
| 09-G-7 | cap counter | Cap counter | D | Carries 23-G-6's counting contrast |
| 09-G-8 | rebinding | Rebinding, Password rebinding | S | Collision §1 |
| 09-G-9 | cardinality axis | Cardinality axis | D | Keyed on the source key (09:1531). Refuses non-members only (reading B, 09 amendment from 31 item 5) |
| 09-G-10 | authenticable | Authenticable | D | Five terms (09:1242–1245, 11:816–818) |
| 09-G-11 | reconciliation sweep | Reconciliation sweep | D | Now owned by 08 for all five triggers. The term is unchanged |
| 10-G-1 | pending registration | Pending registration | D | Also absorbs the "unverified" half of 17-G-9 |
| 10-G-2 | activation token | Activation token | D | |
| 10-G-3 | invite token | Invite token | D | Not a separate type (10:271–274) |
| 10-G-4 | credential token | Credential token | D | "Two types" follows the built set (12's check constraint). The issued-recovery-code type in 10's amendment from 25 is **not built**: its route does not exist (16 §7, 25:865) |
| 10-G-5 | domain-separated token hash | Domain-separated token hash | D | |
| 10-G-6 | link origin | Link origin | D | |
| 10-G-7 | authentication pathway | Authentication pathway, Credential-setting route | S | Collision §1 |
| 11-G-1 | Deleted-user tombstone | Deleted-user tombstone | D | Also absorbs 17-G-5 |
| 11-G-2 | Canonical identifier | Canonical identifier | D | Username rejects, email transforms (11 amendment from 12, item 6) |
| 11-G-3 | Forced-change credential | Forced-change credential | D | Four triggers: seed and re-enable (after 10), tier-2 disable (09 §R.9), operator rebind (10 amendment from 30). 30-day expiry for seed and re-enable only |
| 11-G-4 | Enrolled admin | Enrolled admin | D | Predicate is activated and enrolled (10). Enrolment is row existence (12) |
| 12-G-1 | Seam register | Seam register | D | |
| 12-G-2 | Blinding key | Blinding key | D | Forward-only versioning (24) changes no meaning |
| 13-G-1 | audit row | Audit row | D | A keyed row stands for many occurrences (26) |
| 13-G-2 | discriminator | Event discriminator (+ Alert class) | R | Collision §1 |
| 13-G-3 | reason family | Reason family | D | Same vocabulary as 06's sealed per-family reasons (06:450–455) |
| 13-G-4 | identity-absent failure ratio | Identity-absent failure ratio | D | Per source key (13 amendment from 31) |
| 13-G-5 | pre-handler row | Pre-handler row | D | |
| 14-G-1 | belief versus authority | Belief versus authority | D | |
| 14-G-2 | step-up queue | Step-up queue | D | |
| 14-G-3 | terminal factor state | Terminal factor state | D | |
| 14-G-4 | rebinding | Factor rebinding | R | Collision §1 |
| 14-G-5 | one-time secret display | One-time secret display | D | Covers 14 §14's "one-time token display" and 23's enrolment secret |
| 14-G-6 | visual-integrity degradation | Visual-integrity degradation | D | |
| 16-G-1 | canary secret | Canary secret | D | "Rebind tokens" left out: ticket 32's erratum on 16 |
| 16-G-2 | isolation class | Isolation class | D | Final four values (16:558). 16:422's `own-context` is superseded |
| 16-G-3 | context configuration | Context configuration | D | |
| 17-G-1 | Managed user | Subject (+ Actor) | M | Never pinned as a term. The map's words are subject and actor (11:230–236). Actor is added as the counterpart |
| 17-G-2 | Current user | — | X | Never pinned. The map says actor for the admin case and self-read for `GET /api/profile`, and neither needs its own entry beyond Actor |
| 17-G-3 | Role definition | Role definition | D | 11 §Role model. Distinguished from role assignment |
| 17-G-4 | Authorization matrix | Authorization matrix | D | 11 §Authorization matrix |
| 17-G-5 | Tombstone | Deleted-user tombstone | M | Same concept |
| 17-G-6 | Failed login counter | Failed-login counter | D | The windowed counter only. The cap counter stays separate, because 09 §R.4 named the two apart on purpose |
| 17-G-7 | Account lockout | Lockout | M | Same concept. "Account lockout" is an _Avoid_ |
| 17-G-8 | Absolute session timeout | Absolute session lifetime | R | The map's word is lifetime. "Timeout" is an _Avoid_ |
| 17-G-9 | Unverified vs disabled | Pending registration, Account disable | S | "Unverified" is a pending registration. The five "disabled" senses are resolved in §1 |
| 20-G-1 | same-site vs cross-origin | Same-site | R | One entry carries the contrast |
| 20-G-2 | document context vs API origin | Document context, API origin | S | Two nouns, each needed alone |
| 21-G-1 | per-event alert | Per-event alert | D | |
| 21-G-2 | rate-above | Rate-above | D | |
| 21-G-3 | rate-below | Rate-below | D | |
| 21-G-4 | transition-keyed row | Transition-keyed row | D | |
| 21-G-5 | observability boundary | Observability boundary | D | No defining sentence in 21. Built from 21:52–60 and 21:552–563 |
| 23-G-1 | pending enrolment | Pending enrolment | D | |
| 23-G-2 | tier-1 lock | Tier-1 lock | D | |
| 23-G-3 | tier-2 disable | Tier-2 disable | D | Also forces a password change (23 amendment from 09) |
| 23-G-4 | enrolment binding | Enrolment binding | D | No defining sentence in 23. Built from 23:225–232 and 23:881 |
| 23-G-5 | context prefix | Context prefix | D | |
| 23-G-6 | same 100, two counting rules | Cap counter, Tier-2 disable | M | A note, not a term. Stated in both entries, as 23:1008–1009 asks |
| 24-G-1 | prohibited configuration | Prohibited configuration | D | Not tied to one validator. Some entries are enforced elsewhere (24:884) |
| 25-G-1 | acceptance check | Acceptance check | D | |
| 25-G-2 | declared vacancy | Declared vacancy | D | |
| 26-G-1 | keyed row | Keyed row | D | |
| 26-G-2 | truncation row | Truncation row | D | |
| 26-G-3 | Tier 1 / Tier 2 | Keying tier | R | Collision §1 |
| 26-G-4 | session-store miss | Session-store miss | D | |
| 26-G-5 | miss budget | Miss budget | D | |
| 26-G-6 | keying window | Keying window | D | |
| 26-G-7 | untracked source | Untracked source | D | |
| 27-G-1 | trace restart | Trace restart | D | |
| 28-G-1 | planned-outage recovery | — | X | Withdrawn at source (28:445) |
| 28-G-2 | plan/apply | — | X | Withdrawn at source (28:446) |
| 29-G-1 | anonymous session | Anonymous session | D | |
| 29-G-2 | pin / pinned expiry | Pinned expiry | D | Collision §1 |
| 29-G-3 | shedding / shed episode | Shed episode | D | |
| 29-G-4 | reserve | Reserve | D | |
| 31-G-1 | source key | Source key | D | |

**Four entries correspond to no row:** Actor, Re-verification window, Step-up and Alert class. Each one comes from a
collision in §1, or from the managed-user merge.

**Tally, counted mechanically from the table:** 81 rows.

| Disposition | Rows |
|---|---|
| D | 64 |
| R | 5 |
| M | 5 |
| S | 4 |
| X | 3 |

The 79 live rows break down as 64 + 5 + 5 + 4 + 1. The one live X is 17-G-2; the other two X rows are the withdrawn
28-G-1 and 28-G-2.

**The 81 entries** are:
- 69 from the D and R rows;
- 7 new names from the splits;
- Subject, from the M rows;
- the 4 entries above that correspond to no row.

### 3. Findings

- **Three collisions beyond the four this ticket listed:** *tier*, *discriminator* and *pin*. A fourth,
  *authentication pathway*, had also never been reconciled: 10 counts seven routes and 23 counts three.
- **Two of 17's sketch terms were never pinned by any ticket:** *managed user* and *current user*. The sketch predates
  the map, so this is not a gap.
- **10's amendment from 25 says the credential token table "gains a fourth type"**, but only three types are ever
  named, and the recovery-code route is not built. `CONTEXT.md` follows the built set. The spec should not inherit the
  word "fourth".

### Handover items (ticket 25)

None. A glossary creates no deployer or operator obligation.

### Amendments made to other files

- `CONTEXT.md` created at the repo root.
- `map.md`: Decisions-so-far pointer.

Status: resolved.
