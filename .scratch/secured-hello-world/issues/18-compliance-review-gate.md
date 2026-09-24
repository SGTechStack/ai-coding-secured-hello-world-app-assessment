# 18 — Compliance review gate

Type: task
Status: open
Blocked by: 17

## Question

Does the finished plan pass IM8, ARC, and App Standards review — and if not, what has to change
before anyone writes code?

This is the last ticket on the map. It is a gate, not a formality: a FAIL here reopens design tickets
rather than getting waved through with a note.

## How

Run against the completed spec and the deferral register:

1. **`policies`** — audits the document against IM8 and ARC. Note its own rule: scope decisions,
   security boundaries, and permission models are never auto-fixed, they are marked FAIL. Treat any
   FAIL as reopening the relevant design ticket.
2. **`spec-compliance`** — vets the spec against IM8 and ARC in the form those skills expect. The
   input format was established in "Extract the IM8 and ARC controls"; produce what they need rather
   than hoping the spec happens to fit.
3. **`spec-standards-check`** — checks the spec against applicable appfw standards, which is the
   direct check against `Standalone_User_Access_Control_Application_Standard.md`.
4. **`app-standards-review`** (in `App-Standards/.agent/skills/`) if it applies to a spec rather than
   to code.
5. **`pragmatic-reviewer`** — separates signal from noise across the findings, so genuine gaps are
   not buried under stylistic ones.

## Also record: the gates the build must pass

These cannot run now because there is no code, and they are deliverables *of* this map rather than
work on it. Write them into the spec as mandatory build-phase gates:

- `dependency-vuln-scan` and the Maven-bound OWASP Dependency-Check with its CVSS threshold
- `semgrep` on all changed code
- `spring-security-review`, `spring-web-review`, `spring-data-review`, `spring-logging-review`,
  `spring-test-review`
- `react-review`
- `im8-review` against the implementation, not just the spec
- `build-check` and `code-reviewer`
- `browser-test` against the PRD's acceptance criteria as the final acceptance gate
- `mutation-testing` on the security-critical classes, if "Decide the test plan" called for it

## Done when

Every applicable control is classified PASS, FAIL, DEFERRED, or N/A; every FAIL has either been fixed
or has reopened a ticket; the build-phase gate list is written into the spec; and the map has no open
tickets left — meaning the way to the destination is clear and `/to-spec` can run.
