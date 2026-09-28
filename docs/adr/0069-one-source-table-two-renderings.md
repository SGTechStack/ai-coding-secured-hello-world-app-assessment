---
status: accepted
---

# ADR-069: One generated source table renders both the register and the handover document, gated for drift in `verify`

The deferral register (the compliance view) and the operational handover document (the operator's view) are two
renderings of **one** extracted source table. A single JUnit test in the Maven `verify` phase regenerates both and
fails when a committed rendering differs. The default would be two hand-maintained documents. We do not do that
because they share rows, and the drift between them is exactly what several binding requirements punish.

## Context

Every obligation a deployer or operator must discharge is also a compliance verdict: it names a requirement, its
level, and whether the application enforces any part of it. The two documents answer different questions about the
same row:

- **Compliance view:** requirement ID with its level, verdict, deviation and residual.
- **Operational view:** what to do, in what order, and how a reader proves it was done.

Kept by hand, the two copies diverge the first time a verdict changes and only one of them is edited.

The binding reason for generating them is not house style. OWASP ASVS 5.0 makes the application's documentation the
reference point for several requirements:

| ID | Level | What the documentation is the reference point for |
|---|---|---|
| 6.3.1 | L1 | controls against credential stuffing and brute force are "implemented according to the application's security documentation" |
| 6.1.2 | L2 | the context-specific word list is documented |
| 6.2.11 | L2 | that documented list is actually used |
| 16.2.3 | L2 | logs go only to the documented destinations |
| 16.3.3 | L2 | the security events the documentation defines are emitted |

6.3.1 is scoped to credential-stuffing and brute-force controls, which makes it the implementation twin of 6.1.1
(L1), the requirement that such documentation exist. It is not a general drift rule, so at L1 it binds the lockout
and throttling rows specifically. ASVS 13.2.4 and 13.2.5 (L2) are **not** members of this family. They
require an allowlist to exist and reference no documentation, and they are cited only as 13.1.1's enforcement
companions.

## Decision

- **One source table.** Each row carries a `responsibility`, a `status` and, unless nobody is responsible, a
  `priority`, plus an acceptance check. The schema itself is specified in the spec (REJ-075).
- **Two renderings from that table.** Each compliance row carries an anchor into its operational procedure, so the
  register is never split across artefacts a reviewer has to reassemble. The anchor comes free, because both files
  are generated.
- **One drift gate:** a single JUnit test run by Maven Failsafe regenerates both renderings and fails when either
  committed file differs (T-BLD-006). It is deliberately **not** also a plugin execution, because two
  implementations of one comparison can disagree.
- **Failsafe and Surefire are pinned at exactly 3.6.0.** From 3.6.0, `-DskipTests` no longer skips Failsafe
  (apache/maven-surefire#3371, SUREFIRE-823). The only remaining bypasses are `-DskipITs` and `-Dmaven.test.skip`.

## Considered options

- **Two hand-maintained documents.** The default. Rejected for the reason above.
- **One document serving both audiences.** A compliance reader and an operator read in different orders: by
  requirement, and by deployment sequence. One of them would always be reading against the grain.
- **Generated renderings without a gate.** A generated inventory with nothing forcing a regeneration is a
  transcription with extra steps. There is no CI pipeline, so the gate has to live in `verify`, on the same
  precedent as OWASP Dependency-Check.

## Consequences

- **Any Surefire or Failsafe version bump reopens this ADR.** Below 3.6.0, `-DskipTests` bypasses the gate again.
- Releasing with `-DskipITs` or `-Dmaven.test.skip` bypasses this gate, the traceability gate (ADR-068) and the
  reconciliation tests. The handover document makes that a release rule, because the application cannot enforce it.
- Editing a rendering by hand fails the build. Edits go to the source table.
