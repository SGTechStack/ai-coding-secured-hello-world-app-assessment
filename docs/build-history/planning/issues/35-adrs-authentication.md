# 35 — Write the authentication ADRs: passwords, hashing and credential tokens

Type: task
Status: resolved
Blocked by: 34
Blocks: 18

## Question

Write the nine ADRs that [ticket 34](34-adr-candidate-list.md) routed to this ticket. They go to `docs/adr/`, in
the `domain-modeling` skill's ADR format: **ADR-001 to ADR-009**.

*Narrowed by ticket 34.* Owner 35's full share was 28 ADRs, too many for one session. Ticket 34 split it three ways.
This ticket keeps passwords, hashing and credential tokens. Lockout, throttling and source keying moved to
[39](39-adrs-lockout-and-throttling.md), and the MFA factor moved to [40](40-adrs-mfa-factor.md).

The ID, title, merged sources, attached amendments and filter answer for each ADR are in
[`adr-routing/routing.md`](../adr-routing/routing.md) §2. The IDs and titles are already reserved in
`docs/adr/README.md`.

## Rules

- **One decision per ADR**, exactly as ticket 34 grouped it. Fold attached amendments into the text. Do not
  regroup. If a grouping looks wrong, amend 34's routing by ID and say why.
- **No-`NN:line` rule (17 Answer §0).** Each ADR stands alone:
  - context, the options weighed, the decision, and the consequences, in its own words;
  - primary-source citations, meaning the standard with section, ASVS ID with level, NIST section, CVE, and
    vendor docs with version;
  - no ticket, line, ticket-local ADR number or `.scratch/` path;
  - cross-references by `ADR-…`, `R-…`, `T-…` or `REJ-…` only.
- **Verification rule (map Notes).** Any external fact an ADR rests on must be checked against its primary source.
  Carry the citation into the ADR, because `research/` is deleted at handoff.
- **The routing's filter sentences are drafts.** The one-sentence filter answers in routing §2 were written from the
  sources' gists. Check them against the source text before reusing them.

## Done when

- ADR-001 to ADR-009 exist, and their `docs/adr/README.md` index rows move from `reserved` to their status.
- A grep of `docs/adr/` for `\b\d{2}:\d+`, `ticket \d`, `\d{2} ADR` and `.scratch` returns nothing for these files.

## Answer

Resolved September 2026. **ADR-001 to ADR-009 are written to `docs/adr/` as `0001-…md` to `0009-…md`, all
`accepted`, and their README index rows moved from `reserved` to linked `accepted`.** The done-when grep
(`\b\d{2}:\d+`, `ticket \d`, `\d{2} ADR`, `.scratch`, plus a bare `ticket`) returns nothing for the nine files, and
all 63 `ADR-`/`REJ-`/`R-`/`T-` IDs they cite resolve to a row in the README, the register or the test plan. Groupings
were kept exactly as routing §2 set them.

Primary sources re-checked this session rather than carried from tickets: NIST SP 800-63B-4 §3.1.1.1–§3.1.1.2 (final
text), the OWASP Password Storage Cheat Sheet, ASVS 5.0 V6 (levels for every ID cited), the Spring advisories for
CVE-2025-22228 and CVE-2025-22234, the Spring Security MFA reference (`FACTOR_OTT`, `oneTimeTokenLogin`), the WSTG-ATHN-03
unlock mechanisms, the zxcvbn4j 1.9.0 artifact on Maven Central, and the governing Standard and Questions file at
source. Content from external sources was rephrased for compliance with licensing restrictions.

### Three premises failed, and two of them change what the register says

1. **ADR-008 — the Standard does admit a self-service change endpoint.** §2 Happy Path step 13, §2 Decision Logic,
   §3.1 and §5 Self-Service Password Change Tests all provide one. 10 §8's "thirteenth defect" read §2:121 (which bars
   changes through *generic profile updates*) and §4:401 (which constrains *reset* endpoints) as forbidding it; neither
   does. The endpoint's existence is compliance, not deviation. What the ADR actually defends is the no-forced-change-
   exemption rule (R-STD-025, genuine) and the direct `matches()` check. **R-CRED-013's `deviation` / `fail` grade is
   wrong** and is owed to ticket 33 as an amendment. Routing §2's ADR-008 filter sentence amended in place.
2. **ADR-009 — the Standard is not silent.** §2 Decision Logic (line 132) says an admin-reset locked account "remains
   locked until the lock expires or the administrator explicitly unlocks it". Clearing the lock at redemption of an
   *admin-issued* token is therefore a deviation, argued in the ADR on the cap: unlock is not rebinding, so a literal
   reading would make a capped account unrecoverable by reset and break ADR-048's in-app route. **R-STD-024 ("undefined")
   understates this** — amendment owed to ticket 33. Routing's ADR-009 filter sentence amended.
3. **ADR-004 — "a peppered hash cannot be rotated" is true only of the keyed-hash variants.** NIST §3.1.1.2's SHOULD is
   "keyed hashing **or encryption**", and encrypting the bcrypt output under an environment key (the ADR-022 idiom) *can*
   rotate by decrypt-and-re-encrypt. 07's argument never reached it. The decision stands, but the encryption variant is
   now declined on weaker, stated grounds (DB-only threat already expensive; key loss bricks every account with only the
   offline runner as recovery outside `dev`; no HSM/TEE), with a reopening trigger. **This is a new argument drafted in
   this session and should be challenged as such.** R-CRED-004's rationale, if it rests on rotation alone, has the same
   gap.

### Two facts that were asserted and are now measured

- **BCrypt cost 12 = 208 ms per hash, 207 ms per verify** (cost 10: 52; 11: 104; 13: 416), on an i9-12900H / Windows 11
  / Temurin 21.0.6 / `spring-security-crypto` 7.0.6, 15 runs after warm-up. The binding figure is the change request's
  five operations, **≈1.04 s**, not 07's estimated 1.5–2 s. Recorded in ADR-001 with the caveat that a developer laptop
  is not the target host.
- **zxcvbn4j 1.9.0 scores**, run against the pinned artifact: `Password123!@#$` = 2 (07 said "1–2"), `my neighbour keeps
  unusual bees` = 4, `aaaaaaaaaaaaaaaaaaaa` = 0, `qwertyuiopasdfgh` = 1, `passwordpassword` = 0. One new finding:
  `SecuredHelloWorld2026!` scores **4, and still 3 with the service name as a user input**, so the estimator's inputs do
  not stop it — only the separate `CONTEXT_TERM` rule does. ADR-005 now says so; it is the evidence for keeping that rule.

### Owed elsewhere

- **Register (ticket 33 is resolved, so handed to [ticket 18](18-compliance-review-gate.md) as an input section):**
  R-CRED-013 regrade (finding 1); R-STD-024 reframe as a deviation (finding 2); R-CRED-004 rationale check (finding 3).
  The register is generated (ADR-069), so the fix goes to its source row.
- **Ticket 32 / test plan:** T-CRED-019 still lists "admin soft-delete" as a trigger; per ADR-007 deletion is the
  cascade, not a trigger (the test still passes, so this is wording only).

Temporary benchmark files were created in `%TEMP%` and deleted.
