# 25: Audit log conformance verification

**What to build:** Verification that the audit trail the PRD requires is actually complete, and
that nothing sensitive leaked into it along the way. Every feature ticket emitted its own events
through the ticket 03 seam; this ticket checks the **set** rather than the individuals, because
"each feature logged something" and "the required audit trail exists" are different claims and
only the second one is the requirement.

The required set is wider than the PRD's list. `lm-4` also wants authorization failures and
session terminations recorded, and the two IM8-driven tickets each add events of their own: the
forced-password-change gate in ticket 22 and the inactivity sweep in ticket 24. Beyond presence,
two further checks: `lm-15` requires every event to parse as JSON against one fixed field set —
an inconsistent schema makes the log unqueryable, which defeats the point of having it — and
`lm-19` requires the absence of credential material be **asserted**, not eyeballed.

This is verification, not new behaviour. Any gap it finds is fixed here.

**Blocked by:** 09, 11, 12, 14, 19, 20, 21, 22, 24.

**Status:** ready-for-agent

**IM8 controls:** `lm-4` Audit Logging; `lm-15` Structured Log Formatting; `lm-19` Log
Sanitisation. *ASVS: V7 Error Handling and Logging, V8 Data Protection.*

- [ ] Every event the PRD's audit requirement names is verified present: login success, login
      failure, lockout triggered, password reset requested, password reset completed, role
      change, account enable, account disable, account delete
- [ ] The events IM8 requires beyond the PRD's list are verified present too: an authorization
      failure event for every 403 on an admin endpoint (ticket 17), with actor and attempted path
- [ ] Session timeout terminations are recorded and **distinguish idle expiry from absolute
      expiry** (ticket 10) — one indicates an abandoned session, the other a long-lived one, and a
      single undifferentiated "session ended" event cannot support either interpretation
- [ ] Forced-password-change events are verified present in both forms: the gate being *required*
      at login and the change being *completed* (ticket 22), so a seeded credential's rotation is
      traceable end to end
- [ ] The inactivity deactivation event (ticket 24) is verified present, carrying reason
      `INACTIVITY` and a **system actor** rather than an empty or fabricated human principal
- [ ] Every admin mutation event carries **both actor and target** — the PRD calls this out
      specifically and it is the part most often half-implemented
- [ ] Every event carries a timestamp in a consistent, timezone-explicit format, so events from
      different components can be correlated into a single timeline
- [ ] A negative test proves **no** password, password hash, reset token, or session identifier
      appears anywhere in the log output produced by exercising the full set of flows
- [ ] The absence is established **by assertion over captured log output**, never by manual
      inspection: the test captures the appender's output and asserts no submitted password value,
      no full session identifier, and no plaintext reset token appears in it
- [ ] The dev-profile reset link from ticket 13 is explicitly in scope: under the **prod profile**
      the test asserts the link and its token are absent from all log output, since that stub is
      the one place a live token is deliberately printed
- [ ] Partial-value redaction is checked, not just whole-value absence — a truncated or masked
      session id is acceptable, a full one is a failure wherever it appears
- [ ] Events are parseable as structured data by a log aggregator without per-event regex
- [ ] Every audit event **parses as JSON** and carries the full fixed ECS field set: timestamp,
      level, event type, actor, target, outcome, source IP, and session or correlation id. A
      missing field in any single event is a failure, not a warning — an inconsistent schema makes
      the log unqueryable, which is the whole reason `lm-15` asks for a fixed set
- [ ] The field set is asserted against a single declared list, so adding an event type without
      its required fields fails the conformance test rather than passing unnoticed
- [ ] Log output does not become an unbounded disk-consumption vector under a sustained failed-
      login flood — worth checking now that tickets 11 and 12 exist
- [ ] Any event the PRD requires that turns out to be missing is added in this ticket rather than
      noted as a gap
- [ ] The verification is an **automated test** that will fail if a future change drops an event,
      not a one-off manual inspection
- [ ] Test: an end-to-end run exercising register, login, failure, lockout, throttle, reset
      request, reset confirm, logout and all four admin mutations emits the complete required
      event set
- [ ] Test: the same run extended with a `USER` hitting an admin path, an idle and an absolute
      session expiry, a forced password change, and an inactivity sweep emits those events too
- [ ] Test: every event captured in that run parses as JSON and carries all required ECS fields
- [ ] Test: the same run's log output contains no credential or token material
- [ ] Test: under the prod profile, the dev reset-link log line is absent entirely
