# 13: PasswordService and self-service password change

**What to build:**

- **`PasswordService`** is the single path that sets any password (ADR-005). It normalises to NFC, then runs these checks in order:
  - `MIN_LENGTH` (15 code points);
  - `MAX_BYTES` (72 UTF-8 bytes);
  - `BLOCKLISTED`: a version-pinned breach slice plus the context word list;
  - `CONTEXT_TERM`;
  - `TOO_WEAK`: zxcvbn4j 1.9.0 score below 3;
  - `HISTORY_REUSE`: the last three hashes.

  It then encodes with BCrypt cost 12 behind `DelegatingPasswordEncoder` with no pepper, and writes history. Rejections are 400 `PASSWORD_REJECTED` with the `rule`.
- **`PATCH /api/profile/password`** always requires the current password. It follows ADR-008's six steps: verify, set, invalidate reset tokens, end other sessions, rotate the id, notify. Notification is not built. The current session is kept.
- **`SessionTerminationService`** owns every call that ends sessions, dispatched after commit (ADR-037; ADR-039).
- **Rate limit:** add the `PATCH /api/profile/password` source row (10 / 1 per 6 s).
- **SPA:** the change-password page, with a zxcvbn-ts strength meter (indicative only), one message per rejection rule, the byte limit counted client-side, and paste and password managers allowed (R-FE-001).

**Blocked by:** 10, 11

**Status:** done

- [x] Each rule rejects its case with its own `rule` value, in the stated order. A 15+ code-point passphrase passes.
- [x] Reusing any of the last three passwords gets `HISTORY_REUSE`.
- [x] A wrong current password is refused and changes nothing.
- [x] After a change, the user's other sessions are gone, the current session survives with a new id, and outstanding reset tokens are invalid.
- [x] An ArchUnit rule allows only `PasswordService` to write the credential column.
- [x] The policy meets 85% mutation score.
