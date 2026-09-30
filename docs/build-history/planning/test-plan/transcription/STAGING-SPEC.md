# Ticket 32 staging spec (kept as the transcription audit trail)

Ticket 32 transcribes every owed test on the map into one canonical table. The decisions governing the table are in
`issues/16-test-plan.md` §Answer (§2 table/IDs, §3 levels/contexts, §5 isolation, §9 supersessions). This file tells
an extraction agent exactly what to produce. **Do not edit any source ticket.** Line numbers must stay stable until
the merge; pointers are inserted in a later phase.

## What counts as a test

- Read every assigned file **in full** (use offset/limit paging; do not rely on grep alone). A test is anything a
  ticket says is owed, owned, handed, required or "gets a test/T-ID": numbered test lists, "Owed … tests" sections,
  "Ticket 16 owns …" lines, "binding test" lines, "assert …" rows in handover tables, amendments "from ticket 16".
- One row per test. A numbered or bulleted item in a test list is one test. A parameterised or matrix-generated test
  is **one** row (say so in the assertion). Sub-bullets that are fixture preconditions or facets of one scenario stay
  inside that row. Split only where the source itself says they are separate tests, or where one item has a
  positive and a separately-stated negative case the source frames as two tests.
- A "binding test" asserts an effective configuration value off configuration, never a hard-coded number. One per
  key when the source says "one per key".
- Items the source explicitly says are **not** owed, out of scope, deferred, "cannot implement", "acceptance check,
  not a test", or superseded go in `## Not transcribed` with the reason and line. They are not rows.
- Where the same test is listed twice **inside your assigned files**, emit one row and put every source ref in the
  `source` cell. Where you suspect it duplicates a test in a file you were **not** assigned, still emit the row and
  write the suspected twin in the `dup` cell (e.g. `≡? 21:624`). The merge resolves cross-file duplicates.
- Apply ticket 16 Answer §9 supersessions/restatements when they touch your files (state the restated assertion,
  not the superseded one, and note it).
- Replace any `KEY:<name>` placeholder with the real property key from ticket 09's "Amendment from ticket 16" table
  (09:1590–1612). Never emit `KEY:`.

## Row format (Markdown pipe table, exactly these 12 columns, in this order)

`| key | pillar | control | assertion | level | context | isolation | clause | source | polarity | dup | note |`

- **key**: `S<ticket>-<nn>` in reading order, e.g. `S09-01`. Ticket 16 body: `S16-nn`. Ticket 32's own inputs:
  `S32-nn`. Standards: `STD-nn`, `PRD-nn`, `MFA-nn`, `LOG-nn`.
- **pillar**: exactly one of `AUTH SES CSRF LCK RL CRED ADM MFA AUD HDR CFG RUN OBS FE E2E BLD ARCH`, chosen by the
  **control proved**, not by where the test runs. Guidance: AUTH login/envelope/enumeration/uniformity; SES session
  lifecycle/invalidation/cookies; CSRF; LCK per-account lockout and ladder/tier-2; RL rate limiters/budgets/body cap/
  source keying; CRED registration, activation, reset, password change, password policy/hashing; ADM admin module,
  guard, roles, bootstrap; MFA TOTP/factor authorities/step-up (the factor matrix is MFA); AUD audit/log content and
  canaries/secret-absence; HDR security headers/CSP/CORS; CFG configuration binding, profiles, secrets, startup
  validators; RUN rebinding runner / out-of-band tool; OBS metrics/health/tracing; FE frontend behaviour (Vitest);
  E2E browser-only controls (Playwright); BLD build gates and artefact inspection; ARCH structural rules with no other
  home (e.g. clock bans). A binding test's pillar is the pillar of the control whose constant it binds.
- **control**: short name of the control (≤ 12 words).
- **assertion**: what the test asserts, specific enough to write it; one sentence or two. No `|` characters (use
  `/` or "or").
- **level**: one of `U C P R B A F E` (unit; full-context MockMvc; full context on real port; runner process; build
  artefact; ArchUnit; Vitest; Playwright). Use P wherever MockMvc bypasses Tomcat (RemoteIpValve, raw Set-Cookie,
  Tomcat meters, trace-header wrapper, duplicate cookies, header budget).
- **context**: `ctx-default`, `ctx-port`, `ctx-locktimeout`, `ctx-nondev`, `restart`, `runner`; for non-Spring
  levels `none` (U), `archunit` (A), `build` (B), `vitest` (F), `playwright` (E). Production-value binding tests use
  `ctx-nondev`. Races at BCrypt cost 12 use `ctx-nondev`. Lock-timeout waits use `ctx-locktimeout`.
- **isolation**: `keyed` (fresh fixture usernames/source keys), `delta` (asserts a before/after delta on a global
  count), `own-DB`, `merged` (the one merged ordered test on the shared `unparseable` bucket), or `none` (no shared
  mutable state: U, A, B, F, pure config binding).
- **clause**: what it discharges, `;`-separated. Standard as `Std §5:<line>` or `Std §x.y`; PRD as `PRD Story n` or
  `PRD §Testing`; ASVS **always with level**: `ASVS 6.3.8 (L3)`; IM8 as `IM8 ac-2`; threat rows `TM-04`; else the
  ticket decision, e.g. `09 §R`. Never an ASVS id without its level.
- **source**: `ticket:line` of the item (1-based line of the item's first line), multiple refs `;`-separated, e.g.
  `09:1622; 16:296`.
- **polarity**: `pos` or `neg` (neg = asserts refusal, absence, fail-closed, or that something does not happen).
- **dup**: empty, or suspected cross-file twin `≡? <ticket:line>`.
- **note**: anything the merge needs (supersession applied, restatement, KEY substituted, split rationale). May be
  empty.

## Output file layout

Write `work/32/stage-<group>.md` with exactly these sections:

```
## Lists
| list | file | heading or first words | first line | last line | n tests | keys |
## Rows
<the 12-column table>
## Not transcribed
| file:line | item | reason |
## Counts
| source ticket/section | n tests | keys |
```

- `## Lists` has one line per **source list** (a contiguous owed-test list/section/table/amendment), `list` id like
  `L09-a`. `last line` is the last line of that list, where a `superseded by` pointer will later be appended. A lone
  "Ticket 16 owns a test" sentence is its own list.
- `## Counts` must sum exactly to the number of rows (a row with several source refs is counted once, under its first
  source, and the other refs noted).

Return to the caller: the output path, total rows, and any problems (ambiguous items, contradictions between
sources, `KEY:` you could not resolve).
