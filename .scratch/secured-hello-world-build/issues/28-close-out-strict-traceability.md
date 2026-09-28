# 28: Close out: strict traceability

**What to build:** The final step of the expand–contract for the traceability gate. The pending ledger from ticket 03 is deleted, so `verify` fails whenever any test-plan row lacks a test. Confirm that the generated audit catalogue covers every PRD event: login success and failure, lockout triggered and cleared, reset requested and completed, and role change, enable, disable and delete with actor and subject. Also confirm the standard's CSRF-rejection, failed-admin-attempt, session-start and startup rows. The narrow Playwright suite has about eight tests (REJ-057).

After this ticket, the remaining acceptance gates are procedural, not code: the manual WCAG 2.2 AA keyboard and screen-reader pass (R-FE-005) and `browser-test` against the PRD's acceptance criteria.

**Blocked by:** 01–27

**Status:** ready-for-agent

- [ ] The pending ledger is gone, and `mvn verify` is green with every T-row cited by a test.
- [ ] The catalogue snapshot lists every PRD and standard event above.
- [ ] Both register renderings are regenerated and committed with no drift.
- [ ] `mvn -Pmutation` passes at 85% across the full security-decision scope.
