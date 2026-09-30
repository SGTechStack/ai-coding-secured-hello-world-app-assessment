# 43 — Write the platform ADRs: test harness, threat model, handover design and recovery runner

Type: task
Status: resolved
Blocked by: 34
Blocks: 18

## Question

Write the eleven ADRs that [ticket 34](34-adr-candidate-list.md) routed to this ticket. They go to `docs/adr/`, in
the `domain-modeling` skill's ADR format: **ADR-064 to ADR-074**. That covers the following:

- the threat model as a plan artefact;
- four test-harness decisions;
- the generated register/handover table;
- break-glass and the recovery address;
- the recovery runner's process model, output and plan/apply digest.

*Split from [36](36-adrs-platform.md) by ticket 34.* It is owner 36's work, by 17 Answer §2's source split. The one
exception is ADR-072, argued in routing §2.

ADR-064 must stay consistent with `docs/threat-model/` once ticket 38 moves the model there.

The ID, title, merged sources, attached amendments and filter answer for each ADR are in
[`adr-routing/routing.md`](../adr-routing/routing.md) §2.

## Rules

Same as [35](35-adrs-authentication.md) §Rules.

## Done when

- ADR-064 to ADR-074 exist, and their `docs/adr/README.md` index rows move from `reserved`.
- The grep in 35's Done-when returns nothing for these files.

---

## Answer

**ADR-064 to ADR-074 are written in `docs/adr/` as `0064-…md` to `0074-…md`, each `status: accepted`, and their
index rows in `docs/adr/README.md` now link the files and read `accepted`.** One decision per ADR, exactly as
routing §2 grouped them. Attached amendments are folded into the text: 25-M-1 into ADR-069; 09-M-6 and 28-M-1 into
ADR-072. No regrouping was needed, so routing §2 is not amended. The Done-when grep (`\b\d{2}:\d+`, `ticket \d`,
`\d{2} ADR`, `.scratch`, plus `TM-\d` as a self-imposed extra) returns nothing for the eleven files.

**File convention.** It matches the `NNNN-slug.md` plus `status` front-matter shape that ticket 36 is already using
for 0029–0036. It is written into the README's index note, so 35 and 39–42 inherit it rather than re-derive it.

### What verification changed (map Notes rule)

Each fact below was checked at a primary source this session, and its citation is carried in the ADR:

- **ADR-066: the routing's "production design" framing hid a real cost.** Caffeine's default `Ticker` is
  `System.nanoTime()`, which is **monotonic** (Caffeine `Ticker.java`, `systemTicker()`). So a clock-built ticker is
  a genuine regression from Caffeine's default on an NTP step back. It is not parity. Bucket4j's default *is*
  wall-clock milliseconds (bucket4j.com 8.14.0 §2.2.5–2.2.6), so parity holds there. Ticket 16 had recorded "losing
  monotonicity is a property of both the default and our adapter", which is true for Bucket4j only. The ADR states
  both halves. `FactorGrantedAuthority.Builder.build()` defaulting `issuedAt` to `Instant.now()` is confirmed in the
  7.0.x source.
- **ADR-064 carries a consequence nobody had written.** `im8-review`'s pm-6 FAIL condition says "up-to-date"
  documentation and includes an agent-assessed *data-flow accuracy against code* check. So a plan-time model is
  pm-6 evidence only while it still describes the system. The ADR adds an **update trigger** (boundary, entity,
  store, privileged channel, or cross-boundary flow changes). It also records that drift found against the built
  code is reported against the model, not papered over.
- **ADR-065's reason is now a vendor citation, not an assertion.** Spring Boot's reference ("Auto-configured Spring
  MVC Tests") says `@WebMvcTest` does not scan regular `@Component` or `@ConfigurationProperties` beans. So the
  refresh-phase validator alone is absent from every slice. **No T-row enforces the ban**, which is why it is an
  ADR. An ArchUnit row banning `@WebMvcTest`/`@*Test` slice annotations on `@Proves`-annotated classes would make it
  enforced. Offered to ticket 18 as a cheap tightening, not added here, because amendments to the table are by ID
  and this ticket writes ADRs.
- **ADR-070: NIST §4.2 (recovery, methods, §4.2.1.1–4.2.1.3, §4.2.2.1–4.2.2.2) and §3.1.3.1's email exemption were
  re-read on pages.nist.gov.** All of ticket 25's quotations hold. One supporting argument from ticket 25 was
  **dropped** rather than carried: the §4.4 "backup authenticator SHALL be a password or a physical authenticator"
  contrast. It was not re-verified this session, and the argument stands without it.
- **ADR-069: the ASVS 5.0 IDs and levels were re-read at source** (V6, V13, V16). I deliberately left 6.1.1 out of
  the documentation-as-reference-point table: it is the requirement *that* documentation exist, which is why ticket
  25 left it out too. A draft of this ADR had added it, and that was caught before recording.
- **ADR-072, ADR-073 and ADR-074 rest on ticket 28's verification asset** (H2 Features and Advanced, JDK-8308591,
  Boot externalised configuration, ASVS 6.4.1 and 6.4.6, CWE-214 and CWE-532, Logback `FileAppender`). The primary
  facts are restated with their sources inside the ADRs, because `research/` dies at handoff. The ADRs cite the
  mass-lockout scale as "hundreds of accounts", not "240". Ticket 31 corrected the neighbouring 240-track figure to
  228, and I could not confirm which of the two a batch cap should carry. The cap's *existence* and its
  whole-input check are what the ADR needs.

### Routing filter sentences, checked against source

All eleven hold. One is sharpened. ADR-064's "a maintainer would delete a pre-build model as stale" is true, and the
stronger reason is pm-6's "up-to-date" clause (above), which makes *un-updated* as much a failure as *deleted*.

### Cross-references left for other tickets

- **33 (register):** the ADRs name residuals in words, not by `R-` ID, because no `R-<PILLAR>-nnn` IDs exist yet. When
  33 mints them, the rows it should link back are:
  - ADR-064: none;
  - ADR-066: the Caffeine monotonicity residual (new, above);
  - ADR-070: the §4.2.1 adopted-reading note, and §4.2.3 failed;
  - ADR-071: the password/mailbox correlation, and the three-axis address residual;
  - ADR-072: the uncosted mass-lockout RTO;
  - ADR-073: the scoped 6.4.6 (L3) withdrawal, and the JDK-22 console change;
  - ADR-074: the stale-copy residual.

  Adding the `R-` IDs into the ADR text afterwards is an in-place edit, not a new ADR.
- **38 (de-scratch):** ADR-064 references `docs/threat-model/`, the post-move path, and describes the model at the
  level ticket 38 must preserve: three diagrams, six boundaries, fourteen threats, inherited threats `Mitigated`,
  and not yet opened in Threat Dragon. If 38's de-scratching changes any of those counts, ADR-064 must change with
  it.

### Handover items (ticket 25)

None new. Every deployer or operator obligation these ADRs mention is already declared under this heading by
tickets 16, 25 and 28: the Failsafe pin and bypass flags, a failed `verify` as a release blocker, the rehearsal and
its recurrence, the named runner operator, and mail transport as the recovery prerequisite.

Status: resolved.
