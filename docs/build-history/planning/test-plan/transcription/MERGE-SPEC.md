# Ticket 32 merge spec (kept as the transcription audit trail)

Inputs: `work/32/stage-A.md` … `stage-J.md` (460 staged rows, format in `STAGING-SPEC.md`). Output:
`work/32/dedup.md`. A script (`work/32/build.py`) turns staged rows + `dedup.md` into the canonical table, mints IDs,
and computes the reconciliation. So `dedup.md` must be exact and machine-parseable.

## Output format (three sections, pipe tables, exactly these headers)

```
## Map
| key | action | target | reason |
## Edits
| key | column | value | reason |
## Minted
| key | pillar | control | assertion | level | context | isolation | clause | source | polarity | dup | note |
```

- **Map**: every staged key from every stage file appears **exactly once**.
  - `keep`: becomes a canonical row. `target` empty.
  - `merge`: same test as the canonical key(s) in `target` (comma-separated; a target must itself be `keep` or minted).
    The script adds this key's `source` refs to every target. `reason` names the equivalence in the form the
    reconciliation will print, e.g. `09§R-5 ≡ 21:627` or `16 §9 duplicate`.
  - `retire`: not a test (superseded, vacuous, N/A by a later decision, or a fixture precondition). `target` is the
    decision line that retires it (`ticket:line`) or `17-register` if it must become a ticket 17 register row.
    `reason` states why in one sentence.
- **Edits**: overrides on `keep` rows only. `column` ∈ `pillar control assertion level context isolation clause polarity`.
  Use when the canonical row's field is wrong or when a merged twin carries the better statement (restated assertion,
  correct context, missing ASVS level). Do not use `|` in values.
- **Minted**: new rows for in-scope controls that the map states are enforced but for which **no ticket names a
  test** (ticket 32 inherits "every in-scope control has a named test"). Keys `S32-02` upward. `source` is the line of
  the control; `note` starts with `minted by 32:`. Mint only where the control is a decided, in-scope, application-
  enforced behaviour; never for deployer obligations, acceptance checks, out-of-scope or deferred items.

## Canonical choice

Prefer, in order: the row in the ticket that **owns** the control's decision (e.g. 28 for runner tests, 29 for session
rows, 31 for keying, 27 for trace restart, 30 for bootstrap, 09 for limiter/lockout, 08 for session replay, 06 for
the envelope, 11 for admin, 13 for audit content, 14 for frontend, 24 for validator entries, 26 for miss budget and
truncation) over ticket 16's restatement of it, and over early inventories (01, 02, 03, 04). Then prefer the most
restated/precise assertion; pull a better twin's wording in via `Edits`.

## Rulings already made (apply them; do not re-decide)

1. **Runner token rows are vacuous.** Ticket 28 made the runner read the password from stdin; it mints no token
   (25:956, 25:897–905, 09:1501). Retire `S16-13` (target `25:956`, reason: no runner token exists; ASVS 6.4.1 (L1)
   single-use limb has no subject) and merge `S16-14` into the canonical "runner emits no secret" row (28 test 9,
   `S28-10`). Likewise `S13-22`, `S25-06`, `S16-33` merge into `S28-10`.
2. **`matches()` count absorbs the UserCache check.** 16 §9: 09 §R row 5 becomes the count, and 21:624 is the same
   test. 16 §5 says the count catches a cache hit. So `S09-11`, `S21-14`, `S04-26` merge into `S09-10`.
3. **Envelope producers**: canonical rows are `S06-02` … `S06-07` (06 owns them). `S16-02` merges into
   `S06-03,S06-04,S06-05,S06-06`; `S16-03` into `S06-07`; `S16-01` into `S06-01`.
4. **Trace restart**: canonical rows are ticket 27's `S27-03` … `S27-06` (four cases, different setups). `S15-04` and
   `S16-19` merge into `S27-03,S27-04,S27-05,S27-06`.
5. **Factor matrix** (16 §9): one test. Canonical `S11-08`; merge `S15-01`, `S16-16`, `S23-10`. **Tier-2 lock order**:
   canonical `S23-09`; merge `S15-02`, `S16-17`. Pillar per 16 §2 (factor matrix is MFA; pick one pillar for the lock
   order and state it).
6. **Runner tests**: canonical is ticket 28's `S28-*`; merge `S16-24`…`S16-35`, `S15-05`, `S16-20` accordingly (16's
   row 5 argument-gating ≡ 28 test 8). 28 test 12 (`S28-13`) and 24's three validator rows (`S24-21..23`) and `S16-36`:
   16 §9 says 28 test 12 duplicates 24's entries — keep 24's three per-entry rows (24:552 mandates one assertion per
   entry) and merge `S28-13` and `S16-36` into `S24-21,S24-22,S24-23`.
7. **Bootstrap (30)**: canonical `S30-*`; merge `S16-42`…`S16-49`. Reserved-name refusal: canonical `S25-03`, merge
   `S16-15`.
8. **Ticket 29**: canonical `S29-*`; merge `S16-37`…`S16-40`, `S12-18`. Cron: canonical binding `S29-14` (merge
   `S16-69`); dedicated cron-enabled test canonical `S29-15` (merge `S16-65`, `S08-23`).
9. **Early inventories (01–04)**: every `S01`–`S04` row must end as `merge` onto a later owning row, or `retire` citing
   the later decision that removed it (e.g. no unlock endpoint 11:483; no generic update endpoint §5:473 at 11:209–218;
   no RoleHierarchy bean 23:291; recipe reset-by-generated-password replaced by tokens), or `keep` only if no later
   ticket covers it and it is still valid under the later decisions. Read ticket 11 and others where needed.
10. **Contexts outside the fixed set.** 16 §3 fixes `ctx-default`, `ctx-port`, `ctx-locktimeout`, `ctx-nondev`, the
    out-of-cache restart harness (`restart`) and `runner`. A test that needs a property override or stub no fixed context
    has (baggage probe, sampling 0.0, cron enabled, FileStore stubs, expected-to-fail refresh) runs in the out-of-cache
    harness: set context `restart` (one or two `SpringApplicationBuilder` runs outside the cache) — or level `U` with an
    `ApplicationContextRunner` where it only needs configuration binding/validation. Apply via `Edits`. Production-value
    bindings stay `ctx-nondev` (ticket 16 is later than 24:573 on this).
11. **Isolation** `none` is permitted for U/A/B/F and pure binding rows; `own-DB` rows running in `restart` are fine.
12. Distinct tests that merely share a topic stay separate (e.g. per-IP 429 vs per-account 429; SameSite unit P-level
    Set-Cookie vs E-level delivery; CSP header/meta under preview vs zero-violation count; dev cookie name vs non-dev
    binding may be one profile-scoped test per 20:173 — pick one and merge the rest).

## Process

Read `STAGING-SPEC.md`, then every stage file **in full** (paging). Build the map pillar by pillar, looking for
cross-file twins by control, not by key. Read source tickets (`issues/NN-*.md`) at the cited lines whenever an
equivalence or retirement is not obvious — the map's rule is that a remembered decision is an unverified fact. Check
each "Problems" note the stage agents raised (they are in the "note" cells and "Not transcribed" tables) and resolve
it in `Map`/`Edits`/`Minted`. Candidate gaps to judge for minting (mint only if the control is decided, in scope and
application-enforced, and no row covers it): 24:356–361/24:391 key-material validation; 24:412–416/24:497–499/24:509
refuse-to-start on missing secrets, `app.origins.api` cross-check, blank issuer; 26:511 session-miss window binding
(seam rule); 29:198 floor/lead_days non-negative; 07:270 `CompromisedPasswordChecker` not registered as a bean;
06 §9 no `WWW-Authenticate` on 401; 13:457 audit-strategy ordering "no test fails"; SetupMFA (16 §8 says it gets new
tests; 14/22/23 name its behaviours); 25:329 vs 24 §9 reset-link logger contradiction (decide what is tested).

Finish with a self-check: count keys in Map = 460 (every staged key once; list any key you could not place), every
`merge` target is `keep` or minted, no two `keep` rows are the same test. Return: counts of keep/merge/retire/minted,
and a short list of judgement calls the resolver should report.
