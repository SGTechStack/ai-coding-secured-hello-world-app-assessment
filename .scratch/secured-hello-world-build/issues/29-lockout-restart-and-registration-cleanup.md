# 29: Lockout across restarts, atomic counting at cost 12, and registration lapse cleanup

**What to build:** Two lockout rows still owed on the ledger, and the loose ends of the pending-registration lapse (ADR-032 amendment of 2026-09-29).

- **Lockout survives a restart (T-LCK-007).** In the restart harness, lock an account in the first boot, close it, and boot again on the same H2 file (`RestartHarness.onDatabase`). The second boot refuses the correct password with the uniform 401 `AUTHENTICATION_FAILED` until the clock passes `locked_until`, then admits it. The harness shares one forward-only clock across boots, so the lock can be outlived without sleeping.
- **Atomic counting at the production cost (T-LCK-008).** In `ctx-nondev`, with the production configuration and BCrypt cost 12, ten wrong-password logins released from one `CountDownLatch` leave the account at the threshold count and locked, not under-counted (R-DATA-014). No production application context is refreshed (ADR-067).
- **Lapse cleanup where ADR-032 puts it.** A self-registered pending registration lapses 24 hours after its last registration. The next registration of its username from another address deletes it without a tombstone. Every registration purges expired username holds. An administrator's pending invite (an admin-issued activation token, ADR-007 amendment) never lapses and is never removed this way. No scheduler is added: the spec places the cleanup on the next registration.
- **The deletion is audited.** Deleting a lapsed pending registration writes its own audit row, catalogue row 48, naming the deleted account in `user.id` (there is no actor: the registrant is anonymous). The log inventory is regenerated.
- **No deadlock across lapses.** Two registrations that each take the other's lapsed username used to lock the two account rows in opposite orders, so H2 rolled one back as a deadlock (or, undetected, it waited out the 1 s lock timeout) and it got a 500. Reproduce it in a test first, then fix it so each registration answers 202 or 400 `USERNAME_UNAVAILABLE`, never 500 (T-CRED-027).

**Blocked by:** 12, 14, 25

**Status:** done

- [x] In the restart harness, an account locked in the first boot is refused its correct password with the uniform 401 in the second boot, and admitted once the shared clock passes `locked_until` (T-LCK-007).
- [x] In `ctx-nondev` at BCrypt cost 12, ten parallel wrong-password logins leave the counter at the threshold and the account locked (T-LCK-008).
- [x] A lapsed self-registered pending registration is deleted by the next registration of its username from another address, with no tombstone; expired holds are purged; an administrator's invite never lapses (T-CRED-026).
- [x] That deletion writes an audit row naming the deleted account, and the log inventory is regenerated.
- [x] Two registrations that each take the other's lapsed username both answer without a 500; a test reproduced the 500 first (T-CRED-027).
- [x] Other orphans found in the area are listed in the report, not built.
