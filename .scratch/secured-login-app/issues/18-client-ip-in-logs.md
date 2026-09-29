# 18 — Client IP in logs

Type: grilling
Status: open
Blocked by: —
Map: [Secured Login App](../map.md)

## Question

Is the client IP address logged, and if so in what form?

Surfaced by [05 — Which logging standards actually bind a two-process app](05-logging-standards-applicability.md). This is a **three-way contradiction inside the binding set**, with a PRD story riding on the outcome, so it cannot be resolved by the authority order alone — both sides are the Appfw standards.

**Against logging it.** `Structured_Logging_Application_Standard.md:230` lists "Client IP addresses" in the §3.3 **must-not-be-logged** list, alongside credentials and request bodies. `Recipes/Enriching_Logs_With_MDC.md:94` repeats it as a warning, and `:98` rejects Logback's built-in `MDCInsertingServletFilter` **specifically because** it populates client IP (`req.remoteHost`, `req.xForwardedFor`).

**For logging it.** `Recipes/Logging_AuthN_And_AuthZ_Events.md:37` is unambiguous: "**Always include `source.ip` on every event as it is required for audit and incident investigation.**" `Log_Schema.md` defines `source.ip` as a first-class field. Every one of the nine templated auth events in that recipe carries it.

**Why it matters beyond the contradiction.** The PRD requires IP-based throttling independent of per-account lockout (`prd/assessment-prd.md`, Story 4 / NFRs; the test is enumerated at `:150`). An IP-throttling control whose events do not record the IP is not investigable — the audit line cannot answer which address was throttled. `Structured_Logging_Application_Standard.md:249` independently mandates auditing "repeated input validation failures, which may indicate brute force or automated attack attempts", which is what the throttle detects.

Related: for a failed login against an unknown username, 05 established that **`trace.id` is the only permitted subject field** — `user.id` must be omitted (`Logging_AuthN_And_AuthZ_Events.md:31`), a hashed username is forbidden (`:31`), and `session.hash` is defined by `Log_Schema.md:106` but prohibited by `Structured_Logging_Application_Standard_Questions.md:94` "in any form, including hashed". So if the IP goes too, a brute-force campaign is auditable only as an unattributable count.

**The escape hatch the standard itself offers.** `Structured_Logging_Application_Standard.md:236`: "If any sensitive value must be logged for correlation, **mask or hash it**", and `:276` permits hashed PII "only if secured via a system-wide salt or HMAC".

Settle:

- Whether `source.ip` is logged in cleartext, HMAC'd/hashed, masked (e.g. last octet), or omitted — and whether the answer differs by event class (all events, versus only lockout/throttle/authz-denial).
- If hashed: where the system-wide salt or HMAC key lives, which interacts with [15 — Tech baseline and module structure](15-tech-baseline-and-module-structure.md)'s secrets handling, and whether a rotating key destroys the cross-incident correlation the field exists for.
- Whether the throttling counter's own key (the IP) is therefore stored somewhere the log is not — and if so, that is a persistence question for [07 — Lockout and IP throttling](07-lockout-and-ip-throttling.md) to carry.
- The recorded deviation. Whichever way this goes, one binding clause is knowingly not followed; name it explicitly with its line number so [17 — Handoff to delivery pipeline](17-handoff-to-delivery-pipeline.md) and any IM8 review inherit a deliberate, documented exception rather than a silent gap.
- Whether `X-Forwarded-For` is trusted at all. Topology is single-instance with no gateway ([01 — Context, topology and API surface](01-context-topology-and-api-surface.md)), so a client-supplied `X-Forwarded-For` is spoofable and would let an attacker evade the throttle; `ServletRequest.getRemoteAddr()` is the only trustworthy source here.

Blocks [12 — Audit and logging contract](12-audit-and-logging-contract.md), whose field schema cannot be written until this is fixed. Interacts with [07 — Lockout and IP throttling](07-lockout-and-ip-throttling.md).
