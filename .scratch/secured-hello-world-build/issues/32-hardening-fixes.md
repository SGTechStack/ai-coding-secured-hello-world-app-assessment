# 32: Hardening fixes: audit actions, admin contention, invite/registration edges, dev-only .env

**What to build:** Four loose ends found by the reviews of tickets 22 to 31.

- **Audit actions stay inside the closed `event.action` enum.** `Log_Schema.md`'s `event.action` is a closed enum
  (R-STD-004). Rows written as `totp-verify`, `totp-decrypt`, `password-reset-request` (and any other value outside
  the set) move to the enum value that fits, as ticket 22 did for the admin reads (`user-administration`), unless the
  enum lacks a legitimately needed value. A guard test checks every `AuditEvent`'s action against the closed set. The
  log inventory is regenerated.
- **Heavy admin contention is retryable, not a 500.** A guarded admin change whose lock set (`AuthenticableAdmins`
  `lockForChange`) times out answers a retryable response with an integer `Retry-After`, changes nothing and is
  audited like the other refusals. The SPA's admin pages show a "busy, try again" message. A contention test in
  `ctx-lockhold` proves it.
- **Registration and invite edge cases (ADR-007; ADR-032).**
  - An admin invite whose username or email belongs to a *lapsed* self-registered pending registration frees it (the
    same cleanup and `PENDING_REGISTRATION_LAPSED` audit row a registration writes) and proceeds; a live pending
    self-registration still refuses with `USER_EXISTS`.
  - An invite whose activation token has expired lapses like a self-registration, so the invitee's own
    registration (or another address's registration of its username) is no longer blocked forever. Decision recorded
    in ADR-007. An administrator's re-invite keeps working.
  - An invite and a registration racing on one identifier end in a proper response (201/400 for the invite, 202/400
    for the registration), never a 500.
- **`.env` is read in `dev` only.** `optional:file:./.env[.properties]` is imported under the `dev` profile only;
  `optional:configtree:/run/secrets/` stays for every profile, and still wins over `.env` in `dev` (T-CFG-021).

**Blocked by:** 22, 23, 29

**Status:** done

- [x] Every `AuditEvent` action is a value of the Log_Schema closed `event.action` enum, checked by a guard test; the log inventory is regenerated.
- [x] A guarded admin change whose lock set times out answers a retryable problem with an integer `Retry-After`, writes nothing and writes a refusal audit row; proven by a contention test in `ctx-lockhold`.
- [x] The SPA's admin pages show a "busy, try again" message for that response (Vitest).
- [x] An invite of a lapsed self-registration's username or email frees it with the lapse audit row and succeeds; a live one is still `USER_EXISTS`.
- [x] An expired invite lapses: the invitee's own registration of the address and another address's registration of its username both proceed; re-invite still works. Recorded in ADR-007.
- [x] Invite-versus-registration races on one identifier never answer 500 (test).
- [x] `.env` is imported in `dev` only; outside `dev` a `.env` file is ignored, in `dev` it is read, and a mounted secret still wins over it.
- [x] README / backend README updated where behaviour changes; new test-plan rows added and proven.
