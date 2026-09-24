# 15 — Run the threat model against the design

Type: task
Status: open
Blocked by: 08, 09, 10, 11, 12, 20

## Question

What does a STRIDE threat model over the decided design surface that the decisions missed?

Run after the design decisions land but **before** the spec is finalised, so findings can still
change the design cheaply. That ordering is the entire point — a threat model after the build is an
audit, not a design tool.

## How

Use the `owasp-threat-modeling` skill. Build the data flow diagram from the decided design, not from
the PRD: SPA origin, API origin, session store, H2, the stubbed email transport, and the trust
boundaries between them.

Apply STRIDE across at least these flows:

- Anonymous → login (credential stuffing, brute force, enumeration, timing side channels, the
  interaction of the two rate limiters)
- Anonymous → registration (enumeration via the generic response, verification token guessing,
  mass account creation, notification-based harassment of existing owners)
- Anonymous → password reset request and redemption (token guessing, token leakage via logs or
  referrer, the reset-to-takeover chain, race between two concurrent redemptions)
- Authenticated user → protected endpoints (session fixation, session theft via XSS, CSRF given the
  session-bound synchronizer token, privilege escalation)
- Authenticated user → self-service password change (current-password brute force through the change
  endpoint, which is an authenticated bypass of the login rate limiter — check it is also limited)
- Admin → user management (self-action guard bypass, IDOR on target user IDs, mass-disable as
  denial of service, tombstone abuse, role escalation)
- Scheduled and background paths, if any remain once hygiene jobs are out of scope
- Log and audit flow (log injection via username or email fields, PII leakage, secret leakage)

## Report

For each threat: the affected flow, STRIDE category, likelihood and impact, whether the current
design mitigates it, and the residual risk. Separate findings into:

1. **Design changes required** — these graduate into new tickets or reopen existing ones. Say which.
2. **Build-phase controls** — requirements to carry into the spec.
3. **Accepted risks** — with written justification, feeding "Produce the deferral register and ADR
   set". The known ones going in are: local HTTP, stubbed email, no MFA, no durable audit store,
   single-instance in-memory rate limiting, and enumeration via any path that survived.

## Done when

The threat model artifact exists and is linked here, and every "design change required" finding has
become a ticket rather than a note.
