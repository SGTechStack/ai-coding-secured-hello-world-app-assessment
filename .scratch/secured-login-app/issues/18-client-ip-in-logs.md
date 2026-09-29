# 18 — Client IP in logs

Type: grilling
Status: resolved
Blocked by: —
Map: [Secured Login App](../map.md)
Validated: approved — [validation record](../handoff/validation.md)

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

## Answer

Resolved by grilling, three rounds, all questions accepted as recommended.

**`source.ip` is logged in cleartext, on six security-event classes only, sourced solely from `getRemoteAddr()`, and never on request-lifecycle lines or in MDC.**

### The contradiction was mis-stated, and that changed the question

The brief framed this as a three-way contradiction requiring one binding clause to be broken. Reading every occurrence, it is mostly a **scope distinction**, and the brief's own characterisation of the "for" side is factually wrong.

**Two of the three "against" cites are scoped to request-lifecycle logging, not to logging generally.** `Structured_Logging_Application_Standard.md:52` sits under `#### 1. API Request Handling` → *Log Request Metadata*; `:359` is the §5 checklist item "**Request logs** exclude client IP addresses, query parameters, authentication headers, and request/response bodies". `Recipes/Enriching_Logs_With_MDC.md:94` is scoped to **MDC enrichment**, which is why `:98` rejects `MDCInsertingServletFilter` — that filter puts IP on *every line*. Only `:230`, the bare bullet in the §3.3 must-not list, is unscoped.

**The "for" side is narrower than the brief claimed.** The brief asserted "Every one of the nine templated auth events in that recipe carries `source.ip`." **It does not.** Only four do — authN success (`Logging_AuthN_And_AuthZ_Events.md:68`), lockout (`:86`), authN failure (`:99`), logout (`:167`). These carry no `source.ip` at all: session-start (`:216-220`), session-end (`:248-252`), every MFA event (`:291-324`), authorisation success (`:359-363`), and **authorisation denial** (`:438-449`). The recipe's own Key Takeaway says so in as many words: "Include `source.ip` on **authentication and logout events**" (`:490`). So `:37`'s "always include `source.ip` on every event" is a local overstatement inside §4, contradicted by the templates beneath it.

**The two recipes also contradict each other.** `Centralising_Audit_Logging_With_A_Typed_Module.md` carries `source.ip` on `accessDenied(...)` (`:150`) where the AuthN recipe omits it, and on `profileRead(...)` (`:189`) — a plain business read — under its broad takeaway "`source.ip` on every security event" (`:321`). **05 ruled that module recommended, not mandatory.**

**Therefore: the standard itself never mandates `source.ip` anywhere.** §3.4's audit-context clause (`:255`) requires "who performed the action (`user.id` UUID), what action was performed, what resource was affected, when it occurred, and whether it succeeded or failed" — client IP is not in that list. Under 05's governing finding (normative force lives in the standard, not the recipes), the mandate is recipe-level only. This is a deliberate choice, not a clause to obey.

### The decision

**1. Scope split (`Q1a`).** `source.ip` appears on **security/audit events only**, routed to the separate audit appender 05 established. It appears on **no** request-start or request-end line and **never enters MDC**. This is the only reading under which no clause is violated: `:52`/`:359` and `MDC:94`/`:98` govern request logs and MDC and are followed; the recipe mandate governs security events and is followed. Consequence accepted openly: the audit appender's output is thereby classified higher than the application log, which is a real obligation on the integrator, not a free win.

**2. Cleartext, not hashed or masked (`Q4`).** `:236` ("if any sensitive value must be logged for correlation, mask or hash it") does not fire, because its trigger is a sensitive value being logged *against* a prohibition, and under the scope split we are inside the mandate's scope. Both recipes show cleartext and the typed module's test asserts it (`:292`); `Log_Schema.md:86` documents the field with a cleartext example (`203.0.113.25`). HMAC was rejected on two independent grounds: it defeats the field's purpose (a responder cannot block, geolocate, or correlate a hashed address against network logs), and `:278`'s "system-wide salt or HMAC" forces either a never-rotating long-lived secret — which 15 would inherit — or rotation that severs the cross-incident correlation the field exists for. Last-octet masking is strictly worse than both: a /24 holds many hosts so it still fails throttle investigability, while yielding no real privacy gain since the throttle bucket is per-exact-IP regardless.

**3. Six event classes (`Q5`).** Since the recipes disagree and the standard is silent, the set is ours:

| Event | Carries `source.ip` | Source |
| --- | --- | --- |
| Authentication success | yes | `WebAuthenticationDetails.getRemoteAddress()` |
| Authentication failure | yes | `WebAuthenticationDetails.getRemoteAddress()` |
| Account lockout | yes | `WebAuthenticationDetails.getRemoteAddress()` |
| Logout | yes | `request.getRemoteAddr()` |
| Authorisation denial | yes | `request.getRemoteAddr()` |
| **IP-throttle rejection** | yes | `request.getRemoteAddr()` |
| Session start / session end | no | — |
| Password change / reset request / reset completion | no | — |
| Privileged user-administration events | no | — |
| Registration | no | — |
| Every other audit event | no | — |

The recipe-narrow four fail the PRD: Story 3's IP throttle (`prd/assessment-prd.md:54`, test enumerated at `:156`) is the one control keyed on IP, and its audit line without the IP cannot answer which address was throttled; `Structured_Logging_Application_Standard.md:249` independently mandates auditing "repeated input validation failures, which may indicate brute force or automated attack attempts". Authorisation denial joins as the other unattributed-attacker class, siding with `Typed:150` over `AuthN:438-449`. The typed module's broad reading was **not** adopted: `source.ip` on `profileRead`-style business events turns the audit log into a per-user movement history with no incident-response justification.

**4. `X-Forwarded-For` is not trusted, at all (`Q2`).** `ServletRequest.getRemoteAddr()` / `WebAuthenticationDetails.getRemoteAddress()` is the sole source. 01 fixed topology as single-instance with no gateway or reverse proxy, so a client-supplied `X-Forwarded-For` is attacker-controlled: honouring it would let an attacker rotate the header to evade the throttle entirely and forge arbitrary addresses into the audit trail. Both recipes already do exactly this — `resolveClientIp` reads `details.getRemoteAddress()` (`AuthN:121-126`), the audit module uses `request.getRemoteAddr()` (`Typed:131`) — neither consults a forwarding header. **This decision has a documented expiry**, recorded for 15: introducing any proxy or load balancer makes `getRemoteAddr()` return the proxy's address, silently degrading both the throttle and the audit trail to a single bucket.

**5. The value is stored verbatim, and omitted when absent (`Q6`).** No IPv6 normalization: `getRemoteAddr()` yields `0:0:0:0:0:0:0:1` rather than `::1` in local dev, and it is stored as returned, because the throttle counter key and the audit value must be the same string to correlate (see 7 below), and normalization is correctness we would own for no operational gain. For events with no request context, the field is **omitted entirely** — the recipe's literal `"unavailable"` sentinel (`AuthN:121-126`) is **not** adopted, because `Log_Schema.md:86` types `source.ip` as an IP address and a non-IP string in an `ip`-typed field breaks pipeline indexing; this is the same class of silent-drop bug 05 flagged for `error_code`. No log-injection sanitization is required on this field, and that is true **only because** `X-Forwarded-For` was rejected: `getRemoteAddr()` derives from the socket, not from attacker-controlled input.

**6. `:230` is recorded as an interpretation, with the literal reading written down beside it (`Q7`).** An interpretation that only ever cites itself is indistinguishable from a deviation in denial, so the record carries three parts: the scope argument quoting `:52` and `:359`; the counter-reading stated plainly, that `:230` may be read as absolute and a reviewer is entitled to read a must-not list literally; and the reason it was rejected — under the literal reading `:249`'s own mandate to audit brute-force indicators becomes unsatisfiable, making the standard self-defeating rather than merely strict. A reviewer who disagrees has everything needed to overrule this in one place.

**7. No change to 07's persistence (`Q3`).** The IP-throttle counter keys on the cleartext `getRemoteAddr()` value, in memory, per 02. Its lifetime is the throttle window — shorter retention than the audit log's — so the counter is not the sensitive-data long pole and this ticket imposes no new persistence obligation.

### Clause-by-clause status

| Clause | Status |
| --- | --- |
| `Std:52`, `Std:359` — request logs exclude client IP | **Followed** |
| `MDC:94`, `MDC:98` — no IP in MDC; `MDCInsertingServletFilter` rejected | **Followed** |
| `Std:255` — audit context (who/what/resource/when/outcome) | **Followed**; IP is additive, not a substitute |
| `AuthN:490` — `source.ip` on authentication and logout events | **Followed** |
| `Std:230` — §3.3 must-not list, "Client IP addresses" | **Interpreted** as the roll-up of `:52`/`:359`; literal reading recorded and rejected (see 6) |
| `AuthN:37` — "always include `source.ip` on every event" | **Deviated**: six classes, not all events. Contradicted by its own templates and by `AuthN:490` |
| `AuthN:438-449` — authz-denial template omits `source.ip` | **Deviated**: added, siding with `Typed:150` |
| `AuthN:121-126` — `"unavailable"` sentinel | **Deviated**: field omitted instead (see 5) |
| `Typed:321` — "every security event", incl. `profileRead` | **Not adopted**; module is non-binding per 05 |

Four genuine deviations, recorded unhedged. `Std:236` and `Std:278` (mask/hash) are **not engaged** — no sensitive value is logged against a prohibition, so the escape hatch is unused rather than declined.

### The record's home (`Q8`)

An **ADR** in `docs/adr/`, referenced from 15 and 17. This clears the domain-modeling bar on all three counts: hard to reverse (the log classification and the integrator's retention obligation both hang off it); surprising without context (a reader finding client IPs in logs will go to `Std:230` and conclude it is a bug); and a real trade-off with named alternatives. It is also the first decision on this map a reviewer *outside the effort* must find without reading a wayfinder ticket — `.scratch/` is planning scratch, an ADR is the repo's durable record.

Neither `docs/adr/` nor `CONTEXT.md` exists in this repo yet, so this creates the directory. **The ADR file is deliberately not written here** — it waits on 15's module layout, and 15 or 17 lands it. Its required content, specified so the writer does not re-derive it:

- **Title:** Client IP is logged in cleartext on security events only.
- **Context:** the scope-split argument with `Std:52`, `Std:359`, `MDC:94`, `MDC:98` quoted; the recipe mandate at `AuthN:490` and `Typed:321`; the finding that `Std:255` never names client IP, so the mandate is recipe-level only.
- **Decision:** items 1-5 above, including the six-event table.
- **Alternatives rejected:** omit-everywhere (breaks `Std:249` and PRD Story 3); HMAC under `Std:278` (key rotation severs correlation; a never-rotating secret lands on 15); last-octet masking (fails investigability, no privacy gain).
- **Consequences:** the audit appender is classified above the application log, an obligation on the integrator; the four deviations above; and the `X-Forwarded-For` expiry — any proxy introduction silently collapses both throttle and audit trail to one bucket.

The `X-Forwarded-For` expiry note *also* goes to 15's deployment-assumptions list beside the HTTPS gap, because it is operational rather than architectural.

### Citation corrections

- This ticket's brief cited `:276` for hashed PII; the clause is at **`Structured_Logging_Application_Standard.md:278`**.
- [12 — Audit and logging contract](12-audit-and-logging-contract.md) cites `Std:257` for the "what resource was affected" clause; it is at **`:255`**.
