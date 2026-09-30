# 30: Missing audit rows, error classification and the observability bounds

**What to build:** The audit rows and bounds that ticket 28 found owed but unbuilt, so their test-plan rows can leave
the pending ledger.

- **Session-ending rows (R-AUD-003; ADR-019).** Row 9 (`ABSOLUTE_TIMEOUT`) from the absolute-lifetime filter; row 10
  (`CONCURRENT_EVICTION`) at displacement, inside the login composite, for the displaced session's account; row 11, a
  tier-1 keyed row, for a presented session id that does not resolve (`UNKNOWN_OR_EXPIRED`) and for more than one
  session cookie on a request (`DUPLICATE_SESSION_COOKIE`, R-SES-007). Row 11 costs no extra session lookup.
- **Tier-2 rows (ADR-019).** Row 12 (`INSUFFICIENT_ROLE`) on a signed-in caller's 403, row 14 (`FACTOR_MISSING` or
  `FACTOR_EXPIRED`) on the admin surface's 412, and row 35 on the profile self-read, each keyed on `user.id` and held
  to the distinct-user cap.
- **The anonymous credential rows that T-AUD-026 names.** Row 16 (registration accepted, `NEW_ACCOUNT` with the new
  `user.id`, or `EXISTING_ADDRESS` with none; ADR-032), row 17 (`USERNAME_UNAVAILABLE`, no identity) and row 20 (a
  failed activation or reset redemption, `TOKEN_UNKNOWN`, `TOKEN_EXPIRED` or `TOKEN_CONSUMED`, no identity), each
  written after its transaction has ended. The endpoint registry's audit dispositions follow (T-ARCH-005).
- **Error classification (T-AUD-002).** The ERROR line for an exception that escapes to the error dispatch carries
  `error.code`, `error.category` and `error.follow_up_action` beside `error.type` and `error.stack_trace`. The closed
  sets are recorded in the spec and the register: `error.category` is `validation`, `authentication`,
  `authorization`, `rate-limit`, `conflict` or `server`, from the `ErrorCode` family; `error.follow_up_action` is
  `none`, `retry-later`, `re-authenticate` or `contact-admin`, per code.
- **Measured bounds.** `bytes_per_row` (T-AUD-032) and `F_base` (T-OBS-017) are measured from the real application,
  pinned conservatively with the measurement method in the register, and enforced by tests.
- **The data directory (T-OBS-016; R-OBS-019).** `app.db.data-dir` is a validated property that must contain the
  datasource file; the shed check's volume, the H2 file-size gauge and the anonymous-row gauge all read it, so there
  is one source of the data directory.
- The log inventory is regenerated; each proven row leaves the pending ledger.

**Blocked by:** 08, 09, 19, 26, 28

**Status:** done

- [x] A fabricated session cookie on `/actuator/health` costs one session query and yields one row 11
      `UNKNOWN_OR_EXPIRED` (T-SES-029); two session cookies yield row 11 `DUPLICATE_SESSION_COOKIE` (T-AUD-038).
- [x] A second sign-in that displaces the first writes row 10 at displacement, and the session-start step's row
      carries the pre-rotation `session.hash` (T-AUD-015).
- [x] N+1 distinct users producing rows 12, 14 and 35 in one window yield N keyed rows and one truncation row
      (T-AUD-035).
- [x] Rows 2 (unresolved), 5, 6, 11, 16 (`EXISTING_ADDRESS`), 17 and 20 never carry `user.id`, even with one in the
      MDC (T-AUD-026).
- [x] An exception escaping to `/error` is one ERROR line with `error.code`, `error.category`,
      `error.follow_up_action`, `error.type` and `error.stack_trace` (T-AUD-002); the closed sets are in the spec and
      the register.
- [x] The widest audit row at the capped URI length is under the pinned `bytes_per_row` (T-AUD-032).
- [x] The H2 file at P = 100 is under the pinned `F_base` (T-OBS-017).
- [x] `app.db.data-dir` contains the datasource file, the file-size gauge equals the `.mv.db` length, and the
      anonymous-row gauge survives a GC (T-OBS-016).
- [x] Catalogue rows no source requires are listed in the report, not built.
