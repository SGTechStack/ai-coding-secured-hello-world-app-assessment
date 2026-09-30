# 17 — Produce the deferral register and ADR set

Type: task
Status: resolved (graduated 33–38, the split; see "Answer")
Blocked by: 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32

## Question

What are we deliberately *not* doing, why, and which decisions need an ADR so a future reader
understands them?

This ticket makes the gaps visible. An assessor reading the PRD and the App Standard will notice
every omission; the difference between a considered plan and a careless one is whether the omissions
are named and justified.

## Deliverable 1 — the deferral register

One row per deviation: what was required, which document required it, what we did instead, why, and
the residual risk. Known entries going in, to be completed from how the tickets actually resolved:

**Deviations from the App Standard:**

- Account hygiene scheduled jobs — 90-day inactivity disablement, 180-day role revocation, ShedLock
  serialisation. Deferred as operational lifecycle controls orthogonal to the PRD's auth scope. The
  largest single deferral. *Consolidated into the register (ticket 33): R-ADM-011. Amend the table by ID, not this list.*
- BCrypt instead of the preferred Argon2id or scrypt. *Consolidated into the register (ticket 33): R-CRED-017. Amend the table by ID, not this list.*
- Password history, composition rules, or lockout duration, if the currency research found the
  standard behind current guidance and we followed current guidance instead.
- Role-based authorization matrix, if "Decide the admin module" deferred it.
- Durable 90-day audit retention, if "Build the audit event catalogue" concluded log lines cannot
  satisfy it without infrastructure. *Consolidated into the register (ticket 33): R-AUD-022. Amend the table by ID, not this list.*
- OWASP Dependency-Check as a *CI* gate — implemented as a Maven verify-phase gate instead, since no
  pipeline is in scope. *Consolidated into the register (ticket 33): R-BLD-007. Amend the table by ID, not this list.*
- TLS on the login endpoint, for local development. *Consolidated into the register (ticket 33): R-OPS-003. Amend the table by ID, not this list.*

**Deviations from the PRD:**

- Registration no longer returns a clear username/email conflict error, so **Story 1's third
  acceptance criterion is not met**. State this in exactly those terms — it is the most visible
  deviation and must not look like an oversight. *Consolidated into the register (ticket 33): R-CRED-018. Amend the table by ID, not this list.*
- Lockout duration changed from 15 to 20 minutes, if that is how it resolved. *Consolidated into the register (ticket 33): R-LCK-007. Amend the table by ID, not this list.*
- Additions beyond PRD scope that we chose to build: concurrent session limit, idle and absolute
  timeouts, password history, self-service password change, email verification, soft-delete
  tombstones, admin unlock endpoint. *Consolidated into the register (ticket 33): R-AUTH-002. Amend the table by ID, not this list.*

**Accepted risks from the threat model**, carried over from "Run the threat model against the design".

## Deliverable 2 — the ADR set

`docs/adr/` in the target repo, using the `domain-modeling` skill's ADR format. Only for decisions
that are hard to reverse, surprising without context, *and* the result of a real trade-off.

> ⚠️ **The seven below are a sketch from before any ticket resolved, not the population.** The map's index lines
> reference **132 "ADRs owed"** across 14 resolved tickets with four still open — but that is a claim count that
> mixes new ADRs, amendments to existing ADRs, and Deliverable 1 register rows, and it says nothing about file
> granularity. **The true number is not knowable without the extraction described under "Sizing" at the foot of
> this file. Read that before claiming this ticket, and do not size the work off either 7 or 132.**

Sketch set, written before the map was walked:

1. Session cookies over JWT — the PRD chose this; the ADR records why, including that JWT logout
   needs a blacklist which reintroduces the statefulness JWT was meant to avoid. *Consolidated into the ADR routing (ticket 34): ADR-029. Amend by ID, not this list.*
2. Spring Session JDBC — driven by the requirement to invalidate all of a user's sessions. *Consolidated into the ADR routing (ticket 34): ADR-030. Amend by ID, not this list.*
3. BCrypt over Argon2id — a deviation from the standard's preference, so it needs the trade-off
   written down. *Consolidated into the ADR routing (ticket 34): ADR-001. Amend by ID, not this list.*
4. Dual rate limiting — resolving a direct contradiction between the PRD and the standard, where the
   standard also contradicts itself. *Consolidated into the ADR routing (ticket 34): ADR-010. Amend by ID, not this list.*
5. Enumeration-resistant registration at the cost of a PRD acceptance criterion. *Consolidated into the ADR routing (ticket 34): ADR-032. Amend by ID, not this list.*
6. Flyway over `ddl-auto`, with vendor-neutral SQL against H2. *Consolidated into the ADR routing (ticket 34): REJ-003. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-DATA-001. Amend the table by ID, not this list.*
7. Session-bound synchronizer CSRF token, and the same-site deployment constraint it imposes. *Consolidated into the ADR routing (ticket 34): ADR-036. Amend by ID, not this list.*

Reject any candidate that fails the three-part test rather than padding the set.

## Deliverable 3 — `CONTEXT.md`

The glossary built up across the map: managed user, current user, role definition, authorization
matrix, tombstone, failed login counter, account lockout, absolute session timeout, unverified vs
disabled, and whatever else got pinned down. Glossary only — no implementation detail.

## Inherited from ticket 06 — five ADRs and one artifact pointer

[Decide the API error envelope and the enumeration-safe response contract](06-error-envelope-and-enumeration-contract.md)
owes this register five ADRs:

1. **RFC 9457 envelope**, deviating from the login recipe's `sendError` / `BasicErrorController` posture.
   Justification: the recipe set emits three mutually incompatible shapes, and Boot's default body has no code
   field, so it cannot be the "stable, machine-readable error body" §3.2 line 244 demands. *Consolidated into the ADR routing (ticket 34): ADR-031. Amend by ID, not this list.*
2. **`sendError` prohibited; one `ProblemDetailWriter` across all four producers.** Deviation from the
   prescribed login failure handler. *Consolidated into the ADR routing (ticket 34): ADR-031. Amend by ID, not this list.*
3. **Self-registration returns a uniform 202 plus an activation token**, deviating from PRD Story 1's "clear
   validation error"; `USER_EXISTS` narrowed to admin-initiated creation. *Consolidated into the ADR routing (ticket 34): ADR-032. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-CRED-018. Amend the table by ID, not this list.*
4. **MFA `detail` prose-matching dropped** — clients branch on `code`, not on the mandated English string
   `"User Details not found."`. Deviation from MFA_Core §3.2's Org Standard rule. *Consolidated into the ADR routing (ticket 34): REJ-092. Amend by ID, not this list.*
5. **`account locked` reclassified as a log reason, never a wire code** — the written resolution of §3.2's
   self-cancelling bullet, which names the code *and* requires the response be indistinguishable from an
   invalid-credential response. *Consolidated into the ADR routing (ticket 34): ADR-033. Amend by ID, not this list.*

Also inherited: the register should point at **`docs/api/error-contract.md`** plus the closed `code` enum rather
than restating the contract. That doc is the artifact satisfying §5's "conform to the documented schema" — a
schema the standard requires and never supplies. *Consolidated into the register (ticket 33): R-AUTH-003. Amend the table by ID, not this list.*

## Done when

The register accounts for every known deviation, the ADRs exist and each passes the three-part test,
`CONTEXT.md` is written, and nothing in the register is phrased so vaguely that a reader could not
tell whether it was a decision or an accident.

---

## Sizing: deliberately deferred, with a claim-time gate

**This ticket is almost certainly larger than one agent session, and the decision on how to split it is
deliberately not taken yet.** Recorded here rather than acted on, because the split axis depends on the final ADR
population and four tickets are still open — pre-slicing it now would be choosing a shape for a set we cannot yet
see, which is the same mistake as pre-slicing fog into tickets.

**State of play at the time of writing** (after ticket 09's re-resolution by the ticket 21 reopening):

- The map's index lines reference **132 "ADRs owed" across 14 resolved tickets**, individually between five and
  seventeen. **Treat that as a claim count, not a file count**, for four reasons that the extraction below has to
  separate:
  1. **It is partly self-reported prose.** Only nine tickets carry numbered ADR lists, totalling **83 enumerated
     items**; tickets 13, 21, 23, 24 and ticket 09's §R state theirs in prose. The two figures are not measuring
     the same thing.
  2. **It mixes new ADRs with amendments to existing ones.** Ticket 09's §R is explicit — "Amended: 1, 2, 6.
     Reversed: 3. New, nine" — so its "thirteen" is nine new plus four edits to files that already exist. The map
     line says thirteen.
  3. **It mixes ADRs with Deliverable 1 register rows.** Tickets 09 and 10 title their sections "**Deviations
     owed as ADRs**", merging the two vocabularies at source. A deviation with a residual belongs in the
     register; only the subset passing the three-part test also needs an ADR. Sampling ticket 09's ten gives
     roughly four ADR-shaped, four register rows and two borderline — but ticket 12's seventeen are far more
     decision-shaped, so the ratio is not uniform and must not be assumed.
  4. **Granularity is an open choice, and it moves the count by a factor of two or three.** Nothing forces one
     file per owed bullet: ticket 09's ten could be one ADR on lockout-and-throttling semantics, or four, or ten.
     **That decision belongs to this ticket and should be made before the split, not during it**, because the
     split axis and the file count are the same question asked twice.
- **Still open, all four on the frontier:** 14 (prototype), 15 (task), 16 (grilling), 25 (grilling). Two of those
  are grilling tickets, and every grilling ticket resolved so far has produced between five and seventeen ADRs —
  so the population should be expected to grow materially, not marginally.
- **Only ticket 06 has written its ADR list into this file.** Thirteen other resolved tickets carry theirs in
  their own text. That is not a defect to fix here and now: consolidating them *is* Deliverable 2, and doing it
  before the frontier clears would be work redone.

**The gate, because this ticket becomes takeable at the exact moment the last of those four resolves — and
whoever claims it then would otherwise be claiming a ticket nobody has sized:**

> **Before starting work: count the actual ADR population, then decide whether this ticket splits and on what
> axis.** Do not begin writing ADRs against the sketch above. If it splits, do it as the first act, not halfway
> through.

**Candidate split axes, unranked and unchosen** — recorded only so the decision starts from a list rather than a
blank page: by pillar (authentication / session / admin / data / observability / configuration); register
separate from ADR set separate from `CONTEXT.md`, which is already how the three deliverables divide; or by
source ticket, which has the advantage that each resolved ticket already states its own ADRs in its own words.

**One design note that is true now and makes the split decision cheap whenever it is taken: generate the
inventory, do not transcribe it.** Ticket 13 set the precedent — its ASVS 16.1.1 inventory is generated from the
audit event enum "as a snapshot so the document cannot drift from the code". The same argument applies with more
force here, because the source is 130-plus entries spread across seventeen files: a hand-transcribed register is
stale the first time any ticket is amended, and this map amends resolved tickets routinely (ticket 09 alone
carries amendments from four other tickets plus a full reopening). So this ticket's **first deliverable is a
mechanically extracted inventory of every "ADRs owed", "register entries", "glossary terms" and "reopening
trigger" claim across `issues/`** — which is also the count that settles the split.

**Two things that must survive the split, whatever axis is chosen:**

1. **The three-part test still rejects candidates.** A split raises the chance that a sub-ticket pads its set to
   look complete. The instruction "reject any candidate that fails the three-part test rather than padding the
   set" applies per sub-ticket, and the rejections should be recorded, because a reader cannot otherwise tell a
   rejected candidate from an overlooked one.
2. **Ticket 18 is blocked by all of it.** The compliance review gate reads the register as a whole, so a split
   must not leave the register itself split across artifacts a reviewer has to reassemble. If the axis is by
   pillar, the register stays single and only the ADR authoring divides.

---

## Inherited from ticket 25 — the register is now a rendering, and the sizing gate has its answer

[Decide the contents and owner of the operational handover document](25-operational-handover-document.md) is
resolved, which closes the last of the four tickets this file's sizing note was waiting on **and changes what
Deliverable 1 is**.

**The register and the handover document are two renderings of one generated source.** An exhaustive extraction
found **47 verdicts** resting on the handover document or on deployer action, and both artefacts are populated from
that same set. So Deliverable 1 is no longer a document to write: it is the **compliance rendering** (requirement
ID, level, verdict, deviation, residual) of a mechanically extracted table, with the handover document as the
**operational rendering** (what to do, in what order, how to prove it). This is the concrete form of this file's own
instruction — "generate the inventory, do not transcribe it" — and it is now the shape rather than an aspiration.

Three constraints that land on this ticket:

1. **The extractor runs in the Maven `verify` phase and fails when the committed renderings differ from regenerated
   output.** A generated inventory with no gate is a transcription with extra steps, and ticket 24 established there
   is no CI — `npm run build` runs on a developer's machine. The precedent is OWASP Dependency-Check, bound to
   `verify` for exactly this reason. Without the gate the documentation-conformance requirements below bite anyway. *Consolidated into the register (ticket 33): R-BLD-006. Amend the table by ID, not this list.*
2. **The compliance rendering carries a per-row anchor into the operational rendering's procedure.** This is how
   this file's own constraint survives the split — "a split must not leave the register itself split across
   artifacts a reviewer has to reassemble." It is generated, so the column is free.
3. **The row schema is fixed, and ticket 25 §4 supplies four worked rows as the spike.** `responsibility`
   (`application` | `deployer` | `shared` | `none`), `status` scoped **explicitly to the application's own
   enforcement** (`enforced` | `enforced-elsewhere-cited` | `asserted-by-test` | `procedural` | `unmitigated`), and
   `priority` (`blocking` | `required` | `recommended`) **absent iff `responsibility` is `none`**. Every row carries
   an acceptance check or the sentinel *"none possible — attests a named role is filled and its holder reachable."*
   The rendering carries a **deployment-assumption header**, because `priority`'s absence is conditional on no real *Consolidated into the register (ticket 33): R-OPS-005. Amend the table by ID, not this list.*
   deployment existing and ticket 18 would otherwise read an empty cell as an omission.

**The sizing gate's first question is answered: the population is 47 rows, not 7 and not 132.** The 132 figure was a
claim count mixing new ADRs, amendments and register rows; the extraction is the count, and it is mechanical to
re-derive. Granularity remains this ticket's choice, but it is now a choice over a known set.

**Two additions to the drift family, and one deliberate exclusion.** The requirements that fail when this
documentation and the code disagree are **6.3.1 (L1)**, **6.1.2 (L2)**, **6.2.11 (L2)**, **16.2.3 (L2)** and
**16.3.3 (L2)** — all five make documentation the reference point in their own text. **13.2.4 / 13.2.5 (L2) are
cited as ASVS 13.1.1's enforcement companions, not as family members**: they require an allowlist to exist and
reference no documentation at all, so counting them lets a reviewer checking them find no documentation dependency
and conclude the family was padded. 13.2.6 and 13.3.4 are **L3** and support rather than bind. *Consolidated into the register (ticket 33): R-BLD-006. Amend the table by ID, not this list.*

**One adopted-reading note the register must carry.** Ticket 25 adopted **Reading B** of NIST §4.2.1 — that an
"application-specific method" must still fall inside one of the four enumerated recovery classes, so §4.2.1 and
§4.2.2.1 are in a general/specific relationship rather than in contradiction. Confidence ~70%; the alternative
reading **would have produced a compliance route** for the shell break-glass path. Record the adopted reading and
that fact in one sentence, because the next reader will otherwise find §4.2.1's "alternative methods" clause and
read a considered omission as an apparent one — which is this file's own standard for the register. *Consolidated into the register (ticket 33): R-CRED-019. Amend the table by ID, not this list.*

**Two ASVS citation-hygiene rules for every row.** ASVS 5.0 uses **no RFC 2119 modals** — every requirement reads
"Verify that…" — so rows must not back-translate ASVS into SHALL. And **ASVS V6.5's prose cites NIST 800-63-3**, so
ASVS references must not be cross-walked onto 63B-4 section numbers. *Consolidated into the register (ticket 33): R-STD-035. Amend the table by ID, not this list.*

---

## Inherited from ticket 15 (threat model) — three new blockers, the accepted-risk set reconciled, and one row you were promised that is wrong

**Your blockers grew by three:** [26](26-unbudgeted-routes-and-audit-volume.md),
[27](27-inbound-trace-context.md), [28](28-out-of-band-privileged-channels.md). Each owes ADRs and register
rows, so your sizing gate has three more grilling tickets to count before it runs. That is unwelcome and it is
the honest wiring: two of the three carry a High.

### 1. Where ticket 15's output actually goes — not here, mostly

Ticket 25 settled that its document and your register are **two renderings of one extracted table**, gated in the
Maven `verify` phase. So a threat model's accepted risks are mostly deployer obligations, and writing them as
prose here would produce rows nobody can generate. Ticket 15 therefore declares them under
`### Handover items (ticket 25)` in its own `## Answer`, in the three-field schema, with an acceptance check or
the sentinel on every one. **Six rows.** Your extractor picks them up with the rest; nothing about them is
transcribed here.

What lands here instead is the reconciliation below and the ADR list.

### 2. The accepted-risk set your sketch named, reconciled against how the map resolved

Ticket 15's brief listed six "known ones going in". One of them is now **false**, and saying so matters because
your register is read by ticket 18 and a stale row reads as an unnoticed gap:

| Named going in | Verdict now |
|---|---|
| Local HTTP | **Stands**, in the sharper form ticket 25 already carries: nobody is nominated to terminate TLS. as-10 (2 \| 2) and dp-3. *Consolidated into the register (ticket 33): R-OPS-003. Amend the table by ID, not this list.* |
| Stubbed email | **Stands, and is two rows, not one** — total takeover from log read in `dev`, and **no channel at all** outside it. Ticket 09 §R's amendment to ticket 10 established these are the same design seen from two profiles and both are true. *Consolidated into the register (ticket 33): R-CRED-020, R-CRED-021. Amend the table by ID, not this list.* |
| **No MFA** | **Withdrawn.** Ticket 19 put TOTP in scope for the admin surface. What survives is *no MFA for regular users*, which is on the map's **Out of scope** list as a scoping decision, not a residual. Do not carry it as an accepted risk — it would be the register claiming a gap the map closed. |
| No durable audit store | **Stands** as ASVS 16.4.2 (L2) **F** and 16.4.3 (L2) **F**, both with deployer obligations and no compensating control, already inherited from ticket 13. Note for ticket 18: **V16 contains no L1 requirement at all**, so neither bears on the declared L1 claim. *Consolidated into the register (ticket 33): R-AUD-013. Amend the table by ID, not this list.* |
| Single-instance in-memory rate limiting | **Stands**, and TM-07 adds the half nobody had stated: every limiter is per-key, so aggregate CPU and thread occupancy are unbounded across sources. Ticket 09 already conceded IP rotation makes that free. *Consolidated into the register (ticket 33): R-RL-005. Amend the table by ID, not this list.* |
| Enumeration via any surviving path | **Stands, narrowed to one axis**: `USERNAME_UNAVAILABLE` at registration, ASVS 6.3.8 (L3) deliberately failed, bounded at 5/min per source IP. Every other enumeration channel on the map is closed or is a log-reader oracle with a stated price. *Consolidated into the register (ticket 33): R-AUTH-001, R-CRED-018. Amend the table by ID, not this list.* |

Three added, all argued in [the report](../threat-model/report.md) §3.3: **TM-08** flat ADMIN with no separation
of duties, **TM-09** the tombstone's missing role, and the mass permanent-lockout residual carried unchanged from *Consolidated into the register (ticket 33): R-ADM-009, R-ADM-010. Amend the table by ID, not this list.*
ticket 09 §R.2 after ticket 15 stress-tested its arithmetic as its brief instructed. The arithmetic holds:
`3600 ÷ 100 = 36` is right, the ladder is genuinely throughput-neutral, and the 7×-defeated-by-rotation pricing
is honest rather than flattering. *Consolidated into the register (ticket 33): R-LCK-005. Amend the table by ID, not this list.*

### 3. ADRs owed (four new, two amended)

Deliberately stated as two figures, per the convention your own sizing note asks for — four new files, two edits
to files that already exist.

**New:**

1. **The threat model is an artefact of this map, not of the build.** Three Threat Dragon diagrams plus a report,
   run *before* the spec so findings could still change the design cheaply. Records what the model is for and
   what it deliberately does not restate — that inherited threats are marked `Mitigated` with the owning ticket
   cited, so the `Open` set is exactly what the decisions missed. Ticket 25 §7 refused pm-6's diagram on the
   grounds that this work exists; the ADR is what makes that refusal checkable. *Consolidated into the ADR routing (ticket 34): ADR-064. Amend by ID, not this list.*
2. **`daily` is an attacker-influenceable input, and audit volume is bounded by rule rather than by table.**
   Whatever ticket 26 decides, the decision is hard to reverse (it changes the limiter's contract from an
   allowlist to a property) and surprising without context (it puts a bucket in front of `/api/admin/**`, which
   ticket 09 deliberately left unthrottled for a stated reason). *Consolidated into the ADR routing (ticket 34): ADR-017. Amend by ID, not this list.*
3. **Inbound trace context**, per ticket 27. Passes the three-part test whichever way it goes: continuing an
   attacker-chosen trace and continuing none are both defensible, and the choice decides whether ticket 13's
   correlation join key is a server-side fact. *(Resolved by [ticket 27](27-inbound-trace-context.md) §8. The
   title is "Restart inbound trace context at the application boundary". It must carry the reopening triggers
   in the ADR itself: an upstream tracing gateway; the SPA sending `traceparent`, whose tripwire sits at ticket 05
   `allowedHeaders`; and an outbound call to a traced service. Ticket 27 also owes one glossary term, **Trace
   restart**, and no register lines.)* *Consolidated into the ADR routing (ticket 34): ADR-063. Amend by ID, not this list.*
4. **The rebinding token's channel**, per ticket 28 — hard to reverse, surprising (it will read as excessive
   caution about a `println`), and a genuine trade between three mechanisms with different failure modes. *Dropped in the ADR routing (ticket 34): superseded; see its routing §4.*

**Amended:**

5. **Ticket 23's factor-composition ADR** gains the layering asymmetry: abandoning
   `@EnableMultiFactorAuthentication` was right, *and* it left the factor gate single-layered while the role gate
   is double-layered. A reader of the existing ADR would conclude the annotation bought nothing. *Consolidated into the ADR routing (ticket 34): ADR-026 (attached amendment). Amend by ID, not this list.*
6. **Ticket 11's two-admin-invariant ADR** gains the third and fourth non-mutation channels. Its §R.3 already
   demoted the invariant to monitorable against the NIST cap; the out-of-band channels are the same demotion
   again, and the ADR should say "guarded on mutation paths, monitored everywhere" rather than implying a guard. *Consolidated into the ADR routing (ticket 34): ADR-048 (attached amendment). Amend by ID, not this list.*

**Rejected, recorded because your brief asks for rejections to be visible rather than silent:** an ADR for
`GET /api/hello`'s matrix row (TM-05). It fails all three parts — trivially reversible, unsurprising, and no
trade-off existed. It is a one-line amendment to ticket 11 and nothing more. *Consolidated into the ADR routing (ticket 34): REJ-055. Amend by ID, not this list.*

### 4. Two reopening triggers

- **A mail transport entering scope** already appears on ticket 25's list for the good reason. Ticket 15 attaches
  the bad one: the §4.2.2.2 recovery-code route, activated while the `dev` stub logs what it would send, inverts
  ticket 10 §12's containment from partial to **total** for administrators. Both halves of that trigger belong in
  the register, or a reader sees only the compliance improvement. *Consolidated into the register (ticket 33): R-CRED-026. Amend the table by ID, not this list.*
- **Any inline script in the production document** — ticket 20's existing trigger — now also invalidates ticket
  23's reason for declining re-authentication on enrolment, per ticket 20's own amendment. Carried here because
  it is the clearest case on the map of one trigger with two unrelated dependents. *Consolidated into the register (ticket 33): R-HDR-003. Amend the table by ID, not this list.*

---

## Inputs from ticket 28

[Ticket 28](28-out-of-band-privileged-channels.md) §12.

- **Five new ADRs:** the offline process model; credential in, nothing out; the plan/apply digest; runner-mode
  preconditions; the audit shape.
- **Two amended ADRs:** ticket 09 §R.3's four-constraints ADR, and ticket 24's eleventh prohibited-configuration
  entry.
- **Seven register lines:**
  1. ASVS 6.4.6 (L3), a withdrawal of a volunteered pass, scoped to the runner path.
  2. A PDPA trade: a raw staff identifier is retained, deliberately unresolvable.
  3. The stale-copy residual, `procedural`.
  4. The mass-lockout RTO, uncosted.
  5. The post-commit window, detectable rather than closed.
  6. The JDK-22 console behaviour change, bounded.
  7. 16.2.3 (L2): destination added.

State **6.4.6** exactly as it is written there. Grading it "failed" against an L1 target is the ticket-09
inversion pointed the other way.

~~**Blocked on ticket 30** as well, since the sole-admin bootstrap decision may change ADR 13 and ticket 11's
bootstrap ADR.~~ Discharged: see "Inputs from ticket 30" below.

---

## Inputs from ticket 29

[Ticket 29](29-anonymous-session-row-growth.md) §11 owes:

- **Five new ADRs and one amended ADR.** The amended one is ticket 21's session-gauge decline, now reversed.
- **Four glossary terms:** anonymous session, pinned expiry, shed episode, reserve.
- **Five register entries.**
- **Three reopening triggers.**

The ADR most likely to be misread is ADR 4, the `N_max` cap. It is recorded as a **deviation from ADR 3's own
rationale**, because it denies new logins at about 196 sources regardless of disk size.

Your blockers grow by one: [ticket 31](31-ipv6-source-keying.md).

## Amendment from ticket 31 (IPv6 source keying)

Ticket 31 is resolved; your blocker list shrinks by one. It contributes **two new ADRs and three amended**, stated as
two figures per `map.md`'s counting note. It also contributes **one glossary term** (source key), **eleven register
entries**, including the ladder constants as a **named owed input** from ticket 09, and **five reopening triggers**.
All are at [31 §11](31-ipv6-source-keying.md).

## Inputs from ticket 30 (sole-admin bootstrap premise)

Ticket 30 is resolved, which discharges the block at 17:340–341; your blocker list shrinks by one. **One new ADR and
two amended**, stated as two figures per `map.md`'s counting note, plus one register change. All are argued at
[30 §3–§6](30-sole-admin-bootstrap-premise.md); the text below is what you need to write them.

- **ADR 12 (ticket 11's bootstrap ADR), amended.** "One admin seeded" becomes "one admin seeded, **conditional on a
  second enrolled admin being invited before go-live**". Pending invites do not count. The condition is a handover
  item, deliberately not a gate. Seeding two was rejected because it adds two env-supplied standing credentials and
  does not close the targeted-cap path. A standing unassigned emergency account was rejected as a shared credential *Consolidated into the register (ticket 33): R-ADM-017. Amend the table by ID, not this list.*
  that would undo ticket 28 §8's attribution.
- **ADR 13, amended. Final wording:** three recovery routes keyed on ticket 11's single `authenticable` predicate
  (11:816–818), cited rather than paraphrased.
  1. Lockout, either axis → auto-expiry (password ladder 20/40/60; TOTP tier 1 at 20 minutes).
  2. Forgotten password, single-account cap, lost phone or tier-2 TOTP disable, when **another `authenticable` admin
     exists** → in-app admin password reset or factor reset. **If that admin is locked out, route 2 waits on route 1
     and does not fall through to route 3.** *Consolidated into the register (ticket 33): R-ADM-019. Amend the table by ID, not this list.*
  3. **No `authenticable` admin other than the subject** (sole admin, targeted cap on every admin, zero admins) →
     ticket 28's runner, as a planned outage, **pass-with-note pending first rehearsal**. A red rehearsal grades ASVS
     6.1.1 (L1) F and reopens ticket 28.
- **New ADR: factor reset is exempt from the two-admin count.** The invariant (≥ 2 enabled and enrolled, under the
  pessimistic lock) now covers **disable, demote and delete**. `actor ≠ subject` still applies to factor reset.
  **Argument, who can reverse it:** disable and demote are reversible, but only by another enrolled admin, which at
  two admins is the actor who removed B. After a factor reset, B restores the count alone by re-enrolling
  (23:621–623). The invariant's purpose, a second admin able to act independently of the first, survives only the
  factor reset, so exempting only that path is not an erosion. Zero is unreachable on the path, since the actor is
  always another enrolled admin. **Rejected alternative:** going live at three admins costs more staff and gives no
  added bound (11:907–908).
- **Register, TM-08 line at 17:263, widened.** At exactly two enrolled admins, A can now take B over completely
  (password reset plus factor reset, and A may re-enrol as B). Before, A got the password but not the factor. At three
  or more admins the path was already open. **Detectors:** ticket 13 row 18 `ADMIN_RESET` and row 33 `totp-remove`.
  If the deployer enables OTLP export, the authenticable-admins gauge (< 2) also fires; it is non-discriminating and
  silent at three or more admins. *Consolidated into the register (ticket 33): R-ADM-009. Amend the table by ID, not this list.*
- **No glossary terms.**

---

## Amendment from ticket 16 (test plan)

Everything below is extracted from ticket 16's `## Answer`. This is the pointer, not a transcription.

1. **Blocked by [32](32-test-plan-table-transcription.md) too.** `asserted-by-test` rows cite T-IDs, and those exist only once ticket 32 has transcribed the table.
2. **"Not built" deferrals** (ticket 16 Answer §6b) are register rows, not test limitations:
   - the §5 hygiene rows (90-day inactivity, 180-day role revocation, scheduler serialisation, batch-job session kill); *Consolidated into the register (ticket 33): R-ADM-011. Amend the table by ID, not this list.*
   - 90-day audit retention; *Consolidated into the register (ticket 33): R-AUD-022. Amend the table by ID, not this list.*
   - owner notifications; *Consolidated into the register (ticket 33): R-CRED-022. Amend the table by ID, not this list.*
   - Dependency-Check as a CI gate (it is bound to `verify` instead). *Consolidated into the register (ticket 33): R-BLD-007. Amend the table by ID, not this list.*
3. **The fidelity list** (Answer §6a) gives each item a register row with status `procedural` or `unmitigated`:
   - H2 locking and types; *Consolidated into the register (ticket 33): R-DATA-014. Amend the table by ID, not this list.*
   - TLS, HSTS behaviour and `__Host-` over HTTPS; *Consolidated into the register (ticket 33): R-CFG-004. Amend the table by ID, not this list.*
   - multi-instance consistency (§5:453); *Consolidated into the register (ticket 33): R-RL-006. Amend the table by ID, not this list.*
   - the deployer's launch command; *Consolidated into the register (ticket 33): R-BLD-008. Amend the table by ID, not this list.*
   - wall-clock timing; *Consolidated into the register (ticket 33): R-AUTH-004. Amend the table by ID, not this list.*
   - WebKit/Safari. *Consolidated into the register (ticket 33): R-FE-006. Amend the table by ID, not this list.*
4. **ASVS 6.3.8 (L3) is "verified by mechanism".** The evidence is a `matches()` call count, not a timing measurement, so the downgrade from timing to counting shows in the register. *Consolidated into the register (ticket 33): R-AUTH-004. Amend the table by ID, not this list.*
5. **The Failsafe pin as a residual.** Surefire and Failsafe are pinned at exactly 3.6.0. Under that pin, `-DskipTests` does not skip the drift, traceability and reconciliation tests, but `-DskipITs` and `-Dmaven.test.skip` do, and those two are the accepted bypasses. **Any version bump reopens this.** On versions before 3.6.0, `-DskipTests` bypasses them too. *Consolidated into the register (ticket 33): R-BLD-005, R-BLD-009. Amend the table by ID, not this list.*
6. **Owed inputs discharged.** Ticket 09's constants now have keys (ticket 09's amendment from ticket 16), so no seam-rule row for them remains owed.

---

## Amendment from ticket 32 (test-plan table transcription)

The table now exists at [`docs/test-plan/test-plan.md`](../../../docs/test-plan/test-plan.md): 349 rows with IDs `T-<PILLAR>-nnn`. `asserted-by-test` register rows cite those IDs. The per-row mapping for every prescribed test in Standard §5, the PRD's testing section, MFA_Core §5 and Logging §5 is in the [reconciliation](../test-plan/transcription/reconciliation.md), under "Standards and PRD test sections".

Ticket 32's done-condition says every prescribed §5 test is either a T-ID or a register row here. Nineteen items resolve to rows this ticket already has (17:184, 17:261, 17:409–412, 17:416). **Nine register rows do not exist yet.** They are owed here:

1. **Std §5:452, redemption half.** `/api/password-reset/confirm` is limited per IP only. The account is unknowable before the token lookup (09:737; 10:147). Status: deviation. Record the per-IP budget in its place. *Consolidated into the register (ticket 33): R-STD-019. Amend the table by ID, not this list.*
2. **Std §5:472.** Admin create issues an invite token instead of a flagged generated password, so no forced-change flag or 30-day grace is set at creation (10:446; 11:625). Status: deviation. *Consolidated into the register (ticket 33): R-ADM-012. Amend the table by ID, not this list.*
3. **Std §5:473.** N/A by construction: there is no generic update endpoint and no lock route (11:966; 16:563). `USERNAME_CHANGE_NOT_ALLOWED` was removed (06:481). *Consolidated into the register (ticket 33): R-ADM-013. Amend the table by ID, not this list.*
4. **Std §5:494, batch half.** N/A: batch reset was declined (11:232) and `BATCH_TOO_LARGE` was removed (06:481; 16:564). *Consolidated into the register (ticket 33): R-ADM-014. Amend the table by ID, not this list.*
5. **MFA_Core §5.1 PIN tests (MFA §5:388–392, five items).** N/A: only TOTP is built (22:97), which MFA §4.1 sanctions (22:633). *Consolidated into the register (ticket 33): R-MFA-007. Amend the table by ID, not this list.*
6. **Logging §5:352–353.** Trace and MDC propagation across async boundaries is N/A, since nothing on the map is `@Async` (03:280 is conditional). Reopening trigger: an executor is added. *Consolidated into the register (ticket 33): R-AUD-023, R-AUD-024. Amend the table by ID, not this list.*
7. **Logging §5:355.** Outbound correlation-ID injection is N/A, since there are no outbound calls (27:221). Reopening trigger: an outbound call (27:308). *Consolidated into the register (ticket 33): R-OBS-002, R-OBS-018. Amend the table by ID, not this list.*
8. **Logging §5:358.** No general request log is built. The access log is off (27:252), and audit rows carry only `url.path` and `http.request.method`. Status: not built. *Consolidated into the register (ticket 33): R-AUD-025. Amend the table by ID, not this list.*
9. **Logging §5:374.** Logging under load is not exercised. Appenders are synchronous to stdout and a local file (03:333), so drop-by-design is absent but throughput is unmeasured. Status: fidelity item, alongside 16 §6(a). *Consolidated into the register (ticket 33): R-AUD-026. Amend the table by ID, not this list.*

Also for this ticket: ticket 32 **retired the runner single-use-token row** (16:205, ASVS 6.4.1 (L1)), because ticket 28 removed the token (25:956). No register row is needed for it. The single-use limb has no subject.

---

## Sizing gate — result (run at claim time, 2026-09-28)

The gate ran before anything was authored. The extraction was mechanical: seven parallel passes over all 32
tickets, read in full, to one spec ([EXTRACTION-SPEC](../deferral-register/EXTRACTION-SPEC.md)). The output is
[`deferral-register/inventory/part-A…G.md`](../deferral-register/inventory/). Each row is cited to `NN:line` and
carries a liveness status and, for ADR kinds, a read against the three-part test.

### Raw counts (855 rows, before de-duplication)

| kind | live | superseded / withdrawn |
|---|---|---|
| adr-new | 198 | 4 |
| adr-amend | 91 | 4 |
| adr-reversed | 2 | 6 |
| adr-rejected | 6 | — |
| register | 325 | 22 |
| glossary | 79 | 2 |
| trigger | 64 | 1 |
| handover (exact heading only) | 49 | 2 |

Live `adr-new` against the three-part test: **118 `adr`, 68 `borderline`, 12 `register`**. These are raw figures.
Known duplicates include: 06's five listed in both 06 and 17; 17's seven-item sketch, mostly covered by resolved
tickets; the BCrypt cluster (02-A-3 / 04-A-2 / 07-A-1 / 17-A-3); 13-A-a = 13-A-3; 15-A-3 = 27-A-1; and 04-A-1,
superseded by 20-A-1. After those, the `shape` column reads about 100 decision-shaped and about 60 borderline.
**Treat 90–110 ADR files as an upper bound, not a forecast.** `shape` is a quick first read by seven independent
passes, and the spec told them not to judge merit beyond that one column. Many decisions are one ADR amended
several times. Ticket 11's ADR 13 alone is amended five times (11-M-2, -3, -4, -5, -14). The register has
**about 280–300 unique live rows**, also before cross-file effects are applied.

### Premises of this file that failed

1. **"The population is 47 rows" (17:207) is false.** Ticket 25's 47 (25:455, 25:483) are the verdicts *resting on
   the handover document or deployer action*. That is the `responsibility ∈ {deployer, shared}` slice of the
   register. The register as a whole also holds standards defects, N/A-by-construction rows, fidelity items and
   application-enforced deviations with `responsibility: none` or `application`. Ticket 25's own schema has a value
   for exactly those rows. The register is roughly six times larger than 47.
   **The handover population is not 47 either.** The 47 was counted before 15, 16 and 26–31 declared their 49
   heading items. Ticket 25 also counts "nine obligations with no owner anywhere" separately from the 47 (25:457).
   So the handover population is 47 + 9 + the later heading items, minus overlaps. Nobody has computed that figure
   yet.
2. **132 was an undercount, not an overcount.** The worry at 17:109–125 was that 132 overstated the population. Raw
   live new-ADR claims are 198, because tickets routinely owe ADRs in prose outside their own numbered lists. Ticket
   07 states 7 and owes 10. Ticket 13 states 9 and owes 11.
3. **Items routed to ticket 25 outside the exact heading are pre-rule, not lost.** *(Corrected in this session. The
   first draft said the rule was "mostly unobeyed", which was wrong.)* The rule binds prospectively. Ticket 25
   states at 25:832–838 that every ticket resolved before it is ticket 25's own back-fill debt, and that "the
   remaining ten tickets already have sections in this file". Tickets 10, 11, 20, 21 and 23 predate the rule.
   Every ticket resolved under it (15, 16, 26–31) uses the exact heading. The one exception is 14, which the map
   named as bound, routed its items in prose, and was captured by ticket 25's post-resolution section (25:808–831).
   So the roughly 25 non-heading items the extraction flagged are **de-duplicated against ticket 25's own
   sections**, not added on top of them.
4. **Standards-defect numbering collides across at least three tickets.** Ticket 10 runs seventh to fourteenth
   (10:122–493). Ticket 11 runs seven to twelve (11:487). Ticket 24 calls its defect the "eighth" (24:151). Ticket
   08 has fourth to sixth, and ticket 22 has a "third defective control", but those two do not collide. The
   register renumbers every defect in one global sequence and trusts no ticket's ordinal.

## Answer

**This ticket resolves as its own sizing decision. It splits into six child tickets, 33–38, and authors
nothing.** The gate at 17:147–150 asked for a count, then a split decision, and for the split to be the first act.
The three deliverables and every inherited section above now belong to the children. Each child cites this file
for its inputs, so none of that content is restated there.

### 0. What survives the map, and the rule that follows

`.scratch/` is the wayfinder tracker and is deleted after `/to-spec` runs. What survives is the code, `docs/`
(ADRs, register and handover renderings, test plan, error contract, threat model) and `CONTEXT.md`, plus the spec
`/to-spec` writes while `.scratch/` still exists. Two rules follow.

- **Route every decision by destination (§1). An ADR is only one destination among several.**
- **The no-`NN:line` rule.** Nothing under `docs/` or in `CONTEXT.md` may cite a ticket, a `NN:line`, a
  ticket-local ADR number ("08 ADR 6"), or any `.scratch/` path. Each artefact carries its own rationale and
  cites **primary sources**: the standard with section, ASVS ID with level, NIST section, CVE ID, and vendor
  documentation with version and URL. Cross-references inside `docs/` use surviving IDs (`ADR-nnn`, `R-…`, `T-…`).
  The rule covers:
  - every ADR, register row, handover row and glossary entry;
  - ticket 34's rejection log;
  - the rationale text of every ticket 33 row;
  - `docs/test-plan/test-plan.md`. Its `source` column is `ticket:line` on all 349 rows, and 273 of its `clause`
    cells carry ticket-local references such as "08 §5" or "21 ADR 7".

  Reconciliation tables (`source → IDs`) are scaffolding. They stay in `.scratch/` and die with it.

**What happens to the other `.scratch/` assets:**

| Asset | Fate | Why |
|---|---|---|
| `threat-model/` (Threat Dragon JSON + report) | **Moves to `docs/threat-model/`**, de-scratched by ticket 38 | It is evidence in its own right. Ticket 25 refused IM8 pm-6's diagram because this work exists, and the threat-model ADR records it as a deliberate artefact. Deleting it would falsify both. |
| `research/` (verification assets) | **Dies.** Each ADR and register row carries the primary-source citations it rests on. | These are session-shaped argument trails, full of reversals. Moving them would ship a second, un-gated copy of every rationale. The verification rule needs the *fact* checked against a primary source, and the citation carries that forward. |
| `test-plan/transcription/` | **Dies** | It is the audit trail of a completed transcription. The table it produced survives. |
| `deferral-register/inventory/` | **Dies** | It is the extraction behind this gate. Tickets 33, 34 and 37 consume it. |

### 1. Routing by destination, and one decision per ADR (user decision)

Ticket 34 routes every live ADR-kind candidate to **one or more** destinations. A deviation can both need a
register row and pass the ADR filter.

| Destination | Takes |
|---|---|
| **Register row** (ticket 33) | Every deviation from a standard **or from the PRD**, and every residual, N/A, not-built or fidelity item. |
| **Test-plan row plus rationale** | Invariants that look wrong but are not, such as `alwaysPerformAdditionalChecksOnUser = true` or the `CompromisedPasswordChecker` not being a bean. These go in a new `rationale` column on `test-plan.md`, a sentence that stands alone. There is no code to comment yet, and the test fails where an ADR would sit silent. |
| **Handover row** (ticket 33) | Anything a deployer or operator must do. |
| **Spec** | What the system does: endpoints, codes, table shapes. `/to-spec` reads it from the tickets before `.scratch/` is deleted. |
| **ADR** | Only what passes the filter. |

**The ADR filter is one question: would a maintainer reading the code and the spec plausibly undo this?** If yes,
it gets an ADR. The three-part test still stands behind the filter, but the question is what makes the cut.

- **One decision per ADR.** A decision is the set of owed bullets that would be reversed together. The BCrypt
  cluster is one ADR. Ticket 11's ADR 13 is one ADR, and its five amendments fold into its text.
- **Amendments never become files.**
- **Pillar-sized ADRs are option B, which was rejected.**
- **There is no target count.** Ticket 34 reports the number the filter produces and does not aim for one. The
  earlier "90–110" figure is withdrawn. It was a sum of first-read `shape` columns, not a forecast.

**The rejection log.** Every candidate the filter turns away gets one line in `docs/adr/README.md`. The line
states where the decision went instead and gives the reason in words that stand on their own. It does not go in
the register. Ticket 25's row schema (17:199–205) gives a rejected candidate no requirement ID, `status` or
acceptance check, so ticket 18 would grade it as a gap. The log satisfies 17:168–171's "rejections recorded".

**PRD deviations: register rows always, ADRs only through the filter.** The map's conflict rule said "every
deviation from the PRD is recorded as an ADR". That is amended in `map.md`, for three reasons:

- A PRD deviation has exactly the register's shape. The requirement ID is the PRD story and acceptance criterion,
  and the row states what we did, why, and the residual.
- Ticket 18 grades completeness from the register.
- The ADR set would otherwise carry deviations no maintainer would undo, such as every beyond-PRD addition.

Most PRD deviations still pass the filter. A maintainer holding the PRD would plausibly "fix" Story 1 AC3 back,
or put the 15-minute lockout back. The rule only stops the ones that don't pass from becoming files.

### 2. The split

| # | Title | Type | Blocked by |
|---|---|---|---|
| [33](33-canonical-register-and-handover-table.md) | Build the canonical register and handover table | task (AFK) | 34 *(corrected after the split: 34 routes deviations to the register, and 33 consumes them)* |
| [34](34-adr-candidate-list.md) | Route the ADR candidates and write the rejection log | task (AFK) | — |
| [35](35-adrs-authentication.md) | Write the authentication ADRs | task | 34 |
| [36](36-adrs-platform.md) | Write the platform ADRs | task | 34 |
| [37](37-context-glossary.md) | Write `CONTEXT.md` | task | — |
| [38](38-de-scratch-docs.md) | Make `docs/` stand alone | task (AFK) | 33, 34 |

Why this shape:

- **Consolidation is two tickets.** A single pass would apply every cross-file effect, fill ticket 25's schema for
  about 290 rows (the job 25:748–751 pushed to "ticket 17's re-split"), and route about 260 ADR candidates. That
  is the sizing mistake this gate exists to catch.
- **ADR writing is two tickets.** They split by source ticket, so every decision has exactly one owner:
  - **35, authentication:** 02, 04, 07, 09, 10, 19, 22, 23, 26 and 31.
  - **36, platform:** 03, 05, 06, 08, 11, 12, 13, 14, 15, 16, 20, 21, 24, 25, 27, 28, 29 and 30, plus the two
    sketch ADRs no resolved ticket owns (cookies over JWT, Spring Session JDBC).

  A decision drawn from both sides goes to the side that owns its earliest *resolved* statement, and ticket 34
  records the owner. If 34's count makes either ticket too large for one session, 34 splits it as its last act.
- **The ADR tickets block only on 34.** They need the routing and nothing from the register.
- **37 is unblocked now.** It works from the inventory's glossary rows.
- **38 blocks on 33 and 34**, because it rewrites ticket-local references into surviving IDs.
- **The register stays one artefact** (17:172–173). Only ticket 33 writes it.
- **Ticket 18 is blocked by 33–38** in place of 17.

### 3. Generate, don't transcribe: the table becomes the source of truth

Ticket 32 set the precedent. Once its table existed, every transcribed source list gained "*Superseded by the
test-plan table (ticket 32): T-…. Amend the table by ID, not this list.*" Tickets 33 and 34 do the same:

- When a source section is consumed, it gains an appended pointer: "*Consolidated into the register (ticket 33):
  R-…. Amend the table by ID, not this list.*" For ADR candidates the pointer reads ADR-… and names ticket 34.
- Pointers are appended to existing lines, as ticket 32's were, so every `NN:line` citation on the map still
  resolves.

**These pointers are a bridge, not a record.** They live on `.scratch/` lines and stop sessions working from
stale ticket text while the map is live. After handoff they are worth nothing, which is why the surviving
artefacts carry their own rationale under §0's rule.

### 4. Corrections recorded against this file's own earlier text

These are all in § "Premises of this file that failed" above:

- 47 is the handover slice, not the register population.
- The handover population is itself more than 47.
- Non-heading items bound for ticket 25 are pre-rule back-fill, already largely carried in ticket 25's sections.
- Defect ordinals collide across 10, 11 and 24.

### Handover items (ticket 25)

None. This ticket decides a split. It creates no deployer or operator obligation.

### Amendments made to other files

- `map.md`: the conflict rule (PRD deviations become register rows, and ADRs only through the filter), the
  no-`NN:line` rule, and the Decisions-so-far pointer.
- Ticket 18: blocked by 33–38.

Status: resolved.
