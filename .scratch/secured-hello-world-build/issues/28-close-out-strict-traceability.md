# 28: Close out: strict traceability

**What to build:** The final step of the expand–contract for the traceability gate. The pending ledger from ticket 03 is deleted, so `verify` fails whenever any test-plan row lacks a test. Confirm that the generated audit catalogue covers every PRD event: login success and failure, lockout triggered and cleared, reset requested and completed, and role change, enable, disable and delete with actor and subject. Also confirm the standard's CSRF-rejection, failed-admin-attempt, session-start and startup rows. The narrow Playwright suite has about eight tests (REJ-057).

After this ticket, the remaining acceptance gates are procedural, not code: the manual WCAG 2.2 AA keyboard and screen-reader pass (R-FE-005) and `browser-test` against the PRD's acceptance criteria.

**Blocked by:** 01–27

**Status:** partial: 58 of the 68 remaining ledger rows are proven, two retired (T-CFG-017, T-ARCH-002) and seven
amended by the user's decision (T-AUTH-016, T-AUD-015, T-AUD-018, T-CFG-019, T-CFG-036, T-FE-011, T-HDR-003). The
last 8 rows are ticket 30's; the ledger is deleted once it merges.

- [ ] The pending ledger is gone, and `mvn verify` is green with every T-row cited by a test.
- [x] The catalogue snapshot lists every PRD and standard event above (T-AUD-014, T-AUD-007; the generated
      `docs/audit/log-inventory.md`, T-AUD-017).
- [ ] Both register renderings are regenerated and committed with no drift.
- [ ] `mvn -Pmutation` passes at 85% across the full security-decision scope.

**Left for ticket 30 (still on the ledger):** T-SES-029, T-AUD-002, T-AUD-026, T-AUD-032, T-AUD-035, T-AUD-038,
T-OBS-016, T-OBS-017.
