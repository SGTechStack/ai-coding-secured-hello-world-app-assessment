# 17: Admin endpoint role enforcement

**What to build:** The authorization boundary around the admin module, built and tested
**before** anything sits behind it. A `USER` calling any admin endpoint gets 403; an
unauthenticated caller gets 401; only an `ADMIN` gets through. Doing this first means every
admin feature in tickets 18 to 21 is born protected instead of being protected in a follow-up
that might not happen.

Two additions beyond the PRD. First, the boundary is enforced **twice** — at the filter-chain URL
matcher and again with a method-level `@PreAuthorize` on the admin controller — so that neither
layer is a single point of failure. A URL matcher is easy to widen by accident with a path change;
an annotation is easy to forget on a new method. Each covers the other's failure mode, which is
what `as-7` asks for.

Second, **every denial is audited**. A non-admin probing admin endpoints is the clearest
privilege-escalation signal this application can produce, and right now it is unrecorded. Ticket
03 owns the audit seam; this ticket owns wiring the 403 into it.

One ordering interaction to get right, with ticket 22: the forced-password-change gate runs
**before** role authorization, so an admin who has not yet rotated the seed credential receives
403 with body code `PASSWORD_CHANGE_REQUIRED`, not a role denial. Both responses are 403 and the
body code is what distinguishes them — and the audit events must be distinguishable too, so a
reviewer reading the log can tell an escalation attempt from a pending credential rotation.

Covers PRD Story 8's negative criterion and the PRD's least-privilege requirement.

**Blocked by:** 16, 06.

**Status:** ready-for-agent

**IM8 controls:** `ac-1` Principle of Least Privilege; `as-7` Access Control Check Enforcement;
`lm-4` Audit Logging. *ASVS: V4.1 General Access Control, V4.2 Operation Level Access Control,
V4.3 Other Access Control Considerations, V7 Logging.*

- [ ] Every admin endpoint path requires the `ADMIN` role, enforced by the security framework at
      the path level so a newly added admin endpoint is protected **by default** rather than by
      the author remembering to annotate it
- [ ] The same requirement is enforced a second time at method level — a `@PreAuthorize` on the
      admin controller (or its class) — so the URL matcher and the annotation are **independent**
      checks and neither is a single point of failure
- [ ] Method-level security is actually switched on, not merely annotated: the enabling
      configuration is present in the app's own security config, since an unprocessed annotation
      is worse than none for giving false assurance
- [ ] An authenticated `USER` calling any admin path receives 403
- [ ] An unauthenticated caller receives 401, distinguished from the 403 a logged-in non-admin
      gets
- [ ] The role is read from the **server-side session principal**, never from a request header,
      body field, query parameter, or any other client-supplied state
- [ ] Changing a role in the database takes effect on the next authorization decision — a stale
      cached authority must not let a demoted admin keep acting as one
- [ ] Denied admin access is audited with the acting principal and the attempted operation
- [ ] **Every** 403 from `/api/admin/**` emits an audit event through the ticket 03 seam carrying
      actor, target path and outcome — no denial is silent, because an unrecorded probe of admin
      endpoints is the privilege-escalation signal going missing
- [ ] A role denial and a `PASSWORD_CHANGE_REQUIRED` denial (ticket 22) emit **distinguishable**
      audit event types, so the two kinds of 403 are not conflated in the log
- [ ] The audit event records the path attempted, not just "admin endpoint", so a reviewer can see
      which capability was being probed
- [ ] A placeholder admin endpoint exists to test the boundary against, even before ticket 18
      gives it real content
- [ ] The frontend hides admin navigation from non-admins as a convenience, while the backend
      remains the only actual control
- [ ] Test: a `USER` session receives 403 from an admin path
- [ ] Test: an anonymous caller receives 401 from an admin path
- [ ] Test: an `ADMIN` session is permitted
- [ ] Test: a client-supplied role claim in a header or body does not elevate a `USER`
- [ ] Test: a newly added admin path with no explicit annotation is still protected
- [ ] Test: the method-level check still denies a `USER` when the URL matcher is hypothetically
      bypassed — invoke the controller method directly through the security proxy, outside the
      filter chain, and assert access is denied
- [ ] Test: a `USER` 403 from an admin path produces an audit event with the actor, the requested
      path and a denied outcome
- [ ] Test: a gated admin's 403 carries body code `PASSWORD_CHANGE_REQUIRED` and a different audit
      event type from a role denial — the gate is evaluated before role authorization
