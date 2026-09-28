# Ticket 17 sizing gate — extraction spec

Purpose: mechanically inventory every owed item across `issues/*.md` so ticket 17 can count its real
population and decide granularity and split **before** any ADR is written. This is an inventory, not
authoring. Do not write ADRs, do not judge merit beyond the one `shape` column below.

## What to extract

Every claim that some item is owed to ticket 17 (register, ADR set, `CONTEXT.md`) or to ticket 25's
extracted table. Kinds:

| kind | meaning |
|---|---|
| `adr-new` | a new ADR file is owed |
| `adr-amend` | an edit to an ADR owed by another ticket (name the target ADR and its owner ticket) |
| `adr-reversed` | an owed ADR withdrawn or flipped by a later amendment |
| `adr-rejected` | a candidate explicitly rejected as failing the three-part test (keep these, the ticket requires rejections be visible) |
| `register` | a deferral-register row: a deviation, residual, N/A, not-built, fidelity item, or standards defect for the register |
| `glossary` | a `CONTEXT.md` term |
| `trigger` | a reopening trigger |
| `handover` | a bullet under a heading spelled exactly `### Handover items (ticket 25)` |

Sources to scan in each assigned file: numbered "ADRs owed" lists; prose ADR claims ("n ADRs owed",
"owes an ADR", "record as an ADR"); "Deviations owed as ADRs"; glossary sections; register / seam-register
/ ASVS-register sections; "Reopening triggers"; "Handover items (ticket 25)"; standards-defect lists marked
for the register; and **every `## Amendment from ticket N` / `## Inherited from ...` section**, which may add,
amend, reverse or withdraw items owned by *other* tickets.

## Output

Write one file: `.scratch/secured-hello-world/deferral-register/inventory/part-<GROUP>.md`, containing:

1. A table, one row per item:

   `| id | kind | owner | source | title / gist (≤25 words) | status | shape |`

   - `id`: `<owner ticket>-<kind abbrev>-<n>`, e.g. `09-A-3`, `09-M-1` (amend), `09-X-1` (reversed),
     `09-J-1` (rejected), `09-R-2`, `09-G-1`, `09-T-1`, `09-H-1`. Use the ticket's own numbering where it has one.
   - `owner`: the ticket that owes it (not necessarily the file it sits in — an amendment in 11 owed by 09 has owner 09).
   - `source`: `NN:line` or `NN:start–end`, exact line numbers in the file where the claim is made.
   - `status`: `live`, or `superseded by NN:line`, or `withdrawn by NN:line`, if any section you read changes it.
     If you see a supersession pointing at a file outside your group, record it anyway.
   - `shape` (ADR kinds only): your one-word read against the three-part test (hard to reverse, surprising
     without context, result of a real trade-off): `adr`, `register` (a deviation-with-residual that fails the
     test and belongs only in the register), or `borderline`. For non-ADR kinds leave blank.
2. A per-ticket tally: stated count (what the ticket itself says, e.g. "Fifteen ADRs owed") versus enumerated
   count (rows you found), per kind. Flag every mismatch with one line of explanation.
3. A short "cross-file effects" list: every item in your files that amends, reverses or supersedes something
   owed by a ticket outside your group, so the aggregator can apply it.

Rules: read the whole file, not only the headings. Cite line numbers from the file as it is now. Do not edit
any `issues/` file. Do not paraphrase beyond the 25-word gist. If an item is ambiguous between two kinds,
pick one and note the other in the gist.
