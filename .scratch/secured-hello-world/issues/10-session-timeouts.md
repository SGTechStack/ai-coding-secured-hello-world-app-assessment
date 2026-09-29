# 10: Idle and absolute session timeouts

**What to build:** Sessions expire on their own, so an unattended browser is not an indefinite
open door. Two independent limits: an **idle** timeout that ends a session after a period of
no activity, and an **absolute** timeout that ends it a fixed time after authentication no
matter how active the user has been. A user whose session expires is told to log in again
rather than seeing a broken page.

This control is not in the PRD's story list — it is an IM8 session-management requirement, and
the reason it is a ticket rather than a footnote.

**Blocked by:** 09.

**Status:** ready-for-agent

**IM8 controls:** `as-11` Session Management; `lm-4` Audit Logging.
*ASVS: V3.3 Session Termination.*

- [ ] An idle timeout is configured; a session with no activity for that period no longer
      authenticates
- [ ] An absolute timeout is configured; a session older than that period no longer
      authenticates **even if continuously active**, so an attacker holding a stolen cookie
      cannot keep it alive forever by polling
- [ ] The thresholds are **pinned to concrete defaults** — 30 minutes idle, 8 hours absolute —
      because `as-11` requires a *defined* threshold and the spec named no value, which leaves
      the control unverifiable
- [ ] Both durations come from configuration, not hardcoded literals, and their chosen values
      are documented with the reasoning
- [ ] Both values are externally configurable so the chosen threshold is **reviewable** at
      deploy time rather than buried as a literal in compiled code
- [ ] The **absolute** ceiling is the one that satisfies the control's wording — "after session
      exceeds defined hours" — and is treated as non-optional: an idle timeout alone never
      terminates a session an attacker keeps warm
- [ ] Expired sessions are removed from the session store rather than merely rejected, so the
      store does not grow without bound
- [ ] A session terminated by timeout emits an audit event that distinguishes **idle expiry**
      from **absolute expiry** from **explicit logout**, because the three mean different things
      to whoever is reading the log afterwards
- [ ] The frontend detects an expired session on its next call and routes the user to login
      with an explanation, rather than rendering an error or an empty page
- [ ] Test: a session idle past the idle timeout is rejected by a protected endpoint
- [ ] Test: a session kept active but older than the absolute timeout is rejected
- [ ] Test: a session is rejected once past the absolute ceiling **even when continuously
      active** throughout — this is the case an idle-only implementation gets wrong and passes
      anyway
- [ ] Test: a session within both limits still authenticates
