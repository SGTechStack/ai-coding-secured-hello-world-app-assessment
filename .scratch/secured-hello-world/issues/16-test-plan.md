# 16 — Decide the test plan: which test proves which control

Type: grilling
Status: open
Blocked by: 09, 10, 11, 12, 13

## Question

For every security control on this map, what is the specific automated test that proves it works —
and what proves it *fails closed* when it should?

## Starting point

The PRD mandates integration tests for the security-critical paths and lists a minimum set: login
outcomes including identical generic errors, lockout and cooldown reset, IP throttling independent of
account lockout, session cookie rejected after logout, reset token single-use and expiry, reset
invalidating sessions, admin self-action guards, and a USER receiving 403 from `/api/admin/**`.

The App Standard §5 adds a much longer list across roles and authorization, sessions, CSRF, lockout
and rate limiting, reset, self-service change, user administration, data access, input validation,
security headers and CORS, enumeration, and logging.

## What to decide

- The mapping table: control → test → level (unit, Spring integration slice, full context, frontend).
  Every control from the standard's §5 that is in scope needs a row, and every row needs to name
  which PRD story or standard clause it discharges.
- **H2 only**, per the out-of-scope decision. Decide what that costs in fidelity and what cannot be
  asserted as a result — for instance anything depending on Postgres locking or type behaviour.
- **Time control.** Lockout cooldown, token expiry, and session timeouts all need controllable time.
  Decide the mechanism (inject `Clock`, and make it the production design rather than a test hack) and
  reject `Thread.sleep` explicitly.
- **How to test timing consistency** for the enumeration requirement. This is genuinely hard — wall
  clock assertions are flaky in CI. Decide whether to assert the *mechanism* (a dummy hash
  verification happens on the unknown-user path) rather than the wall-clock timing, and be honest in
  the plan that the mechanism is what is verified.
- **How to test the two rate limiters are independent** — the PRD's Story 3 requirement that an
  attacker cannot lock out a user from one IP. Decide the concrete scenario and how client IP is
  varied in a test.
- **How to test session invalidation** across Spring Session JDBC, including the replay of a captured
  cookie after logout and after password reset.
- **How to test audit logging** — appender capture, and assertions on structured fields plus the
  negative assertions that secrets never appear. The negative tests matter more than the positive.
- Frontend tests: Vitest + Testing Library scope, and what is deliberately not tested.
- Whether Playwright E2E earns its place. Currently unscoped; decide in or out here rather than
  leaving it ambiguous.
- Test data discipline: synthetic usernames and emails only, per the standard's guidance.
- Coverage expectations, and whether mutation testing (`mutation-testing` skill) is worth running on
  the security-critical classes — surviving mutants in a lockout counter are exactly the bugs that
  matter.

## Done when

Every in-scope control has a named test, the time-control and timing-assertion approaches are
decided, and the Playwright question is answered either way.
