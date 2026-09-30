# 28: Close out: strict traceability

**What to build:** The final step of the expand–contract for the traceability gate. The pending ledger from ticket 03 is deleted, so `verify` fails whenever any test-plan row lacks a test. Confirm that the generated audit catalogue covers every PRD event: login success and failure, lockout triggered and cleared, reset requested and completed, and role change, enable, disable and delete with actor and subject. Also confirm the standard's CSRF-rejection, failed-admin-attempt, session-start and startup rows. The narrow Playwright suite has about eight tests (REJ-057).

After this ticket, the remaining acceptance gates are procedural, not code: the manual WCAG 2.2 AA keyboard and screen-reader pass (R-FE-005) and `browser-test` against the PRD's acceptance criteria.

**Blocked by:** 01–27

**Status:** partial: 52 of the 68 remaining ledger rows are proven; 16 await a decision (a row that needs new
product behaviour, or whose text conflicts with the built design), so the ledger is not yet deleted.

- [ ] The pending ledger is gone, and `mvn verify` is green with every T-row cited by a test.
- [x] The catalogue snapshot lists every PRD and standard event above (T-AUD-014, T-AUD-007; the generated
      `docs/audit/log-inventory.md`, T-AUD-017).
- [ ] Both register renderings are regenerated and committed with no drift.
- [ ] `mvn -Pmutation` passes at 85% across the full security-decision scope.

**Waiting on a decision (still on the ledger):**
- Unbuilt audit rows (session-end row 10/11 with `UNKNOWN_OR_EXPIRED` and `DUPLICATE_SESSION_COOKIE`, tier-2 rows 12,
  14 and 35, rows 16, 17 and 20): T-SES-029, T-AUD-015, T-AUD-026, T-AUD-035, T-AUD-038.
- Unspecified values or bounds: T-AUD-002 (`error.category`, `error.follow_up_action`), T-AUD-032 (`bytes_per_row`
  bound), T-OBS-017 (`F_base` bound), T-OBS-016 (`app.db.data-dir` and its gauges, R-OBS-019).
- Row text conflicts with the built design: T-AUD-018 (the login parse error is never logged), T-CFG-017 (no TOTP
  `AuthenticationProvider`, ADR-026), T-CFG-019 (the read duration is the absolute lifetime by construction),
  T-CFG-036 (`lead_days` exists nowhere), T-FE-011 and T-HDR-003 (a single OTP field, no `input-otp` slots),
  T-ARCH-002 (the shared contexts send from loopback on purpose, with raised budgets).
