# 03: Audit logging seam and redaction policy

**What to build:** The structured audit-logging seam that every security-relevant event in the
app will emit through, plus the redaction policy that guarantees secrets never reach a log line.
Prefactoring, like ticket 01: the PRD requires audit lines for login, lockout, password reset
and every admin mutation, and retrofitting that across nine finished features is far worse than
putting the seam in now and having each feature ticket emit its own events as it lands.

No dedicated audit table is required — structured log lines are the deliverable.

**Blocked by:** 01.

**Status:** ready-for-agent

**IM8 controls:** `lm-4` Audit Logging; `lm-15` Structured Log Formatting; `lm-19` Log
Sanitisation. *ASVS: V7 Error Handling and Logging, V8 Data Protection.*

- [ ] A single audit-event abstraction exists that features call to record a security event;
      features do not hand-roll log statements
- [ ] Every audit event carries, at minimum: event type, timestamp in a consistent timezone-
      explicit format, outcome (success/failure), the acting principal, and the target where
      the event is about someone other than the actor
- [ ] Events are emitted as **structured** output (machine-parseable fields, not prose) so they
      can be shipped to a log aggregator without regex archaeology
- [ ] "Structured" is pinned to a **named schema**, not left as an assertion about the output:
      ECS via Spring Boot's `logging.structured.format.console=ecs`, or
      `logstash-logback-encoder`. `lm-15` is a Level 2 control, so an unverifiable claim here
      grades as Critical — the schema has to be nameable and greppable in config
- [ ] The audit field set is fixed and identical on **every** event: timestamp, level, event
      type, actor, target, outcome, source IP, and a session or correlation id. Same field
      names every time, or the aggregator cannot query across event types
- [ ] The event-type vocabulary is defined up front and covers everything the PRD's audit
      requirement lists: login success, login failure, lockout triggered, password reset
      requested, password reset completed, role change, account enable, account disable,
      account delete
- [ ] The vocabulary also covers **authorization failures**, which the PRD list omits even
      though `lm-4` names them as one of its three categories alongside authentication and data
      change: every 403 from an `/api/admin/**` endpoint emits an audit event with the actor,
      the target path and the outcome. A `USER` walking admin endpoints is the clearest
      privilege-escalation signal the system can produce, and today it is not recorded at all
- [ ] Session timeout terminations emit their own audit event, distinct from explicit logout —
      the two look identical in the log otherwise, and only one of them tells you a session was
      abandoned mid-use
- [ ] A redaction policy is enforced **in the seam, not by convention**: passwords, password
      hashes, reset tokens and session identifiers cannot appear in an audit line even if a
      caller passes them
- [ ] Redaction goes beyond passwords, because `lm-19` is also Level 2: email addresses and
      usernames are masked or omitted in **non-audit** application logs, while the audit log
      retains actor identity by design — that asymmetry is the point, so identity lives in one
      controlled, retained stream rather than scattered through debug output
- [ ] Any request/response logging redacts the `Cookie`, `Set-Cookie` and `Authorization`
      headers; a session identifier or a password-reset token is never logged in full anywhere,
      audit stream included — a logged reset token is a working account takeover
- [ ] Failure to log does not fail the business operation, but is itself surfaced
- [ ] Test: an audit event written with a password-like or token-like field emits with that
      value redacted
- [ ] Test: each defined event type emits with its required fields populated
- [ ] Test: every event the seam emits parses as JSON and carries the full required field set —
      no event ships with a missing or differently-named field
- [ ] Test: a password, a reset token and a session id are deliberately passed into logging and
      none of the three values appears anywhere in the captured log output
