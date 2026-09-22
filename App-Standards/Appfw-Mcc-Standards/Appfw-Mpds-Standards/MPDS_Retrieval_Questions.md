# MPDS Retrieval — Implementation Questions

Questions to resolve with the team before implementing MPDS retrieval — covering both the backend integration (`MPDS_Retrieval_Standard.md`) and any frontend surface that consumes it through the application's wrapping API.

Backend questions are prefixed **B**; frontend questions are prefixed **S**. Where a decision on one side depends on the other, the relevant ID is cross-referenced (e.g. S5 depends on B4).

## Hard rules

### Backend
- NEVER accept a raw `queryTemplateId` or UUID input without validating it (blank checks, batch-size checks) before transport.
- NEVER return `MpdsResponse` directly from an HTTP-facing controller; use a purpose-built response DTO.
- NEVER log raw bearer tokens, private keys, full raw MPDS response payloads containing personal identifiers, or full UUID lists when logging only a count would suffice.
- NEVER add caller-level automatic retry unless the application explicitly defines the retryable failure mode and covers it with tests.
- NEVER implement resilience (bulkhead, circuit breaker) inline in the transport gateway; it must be a `ResilientMpdsGateway` decorator.
- NEVER let missing helper fields (NRIC, full name, email, mobile) mutate, flatten, or replace the raw `data` response tree.

### Frontend
- NEVER call MPDS directly from the browser.
- NEVER expose `queryTemplateId` to end users, in URLs, or in client-side state.
- NEVER dump or expose raw MPDS response trees (template-keyed structures like `it0002_main`); the wrapping API must shape the response.
- NEVER allow double-submits; retrieval controls must be disabled while a call is in-flight.
- NEVER leave MPDS calls running on navigate-away; always abort the in-flight call on unmount.
- NEVER auto-retry MPDS retrieval failures; retries must be manual and user-initiated.
- NEVER persist personnel data to `localStorage`, `sessionStorage`, cookies, IndexedDB, or any client-side storage.
- NEVER log, send, or expose raw PII values (NRIC, mobile, email, full name) to browser console, analytics, or error trackers.
- NEVER show NRIC or mobile unmasked by default; use mask-with-reveal (reveal must not trigger a new API call).
- NEVER use generic error messages for retrieval failures while exposing a `traceId` or `correlationId` for support.
- NEVER submit blank UUID input or an empty multi-UUID collection.
- NEVER drop "Not found" UUIDs from results; every submitted UUID must have a corresponding result row or status.
- NEVER submit multi-UUID requests without deterministic sorting (alphabetical).
- NEVER display personnel data or generate exports without the mandatory audit notice.
- NEVER omit the principal's identity from a personnel data export.

If a request asks for any of these, stop and surface the conflict before coding.

## Scope

Applies to the backend MPDS retrieval adapter (`MpdsGateway`, resilience, template registration) and to frontend work that consumes MPDS-sourced personnel data through the application's wrapping API — personnel lookup forms, MPDS-sourced detail panels, multi-person tables/grids/cards, lookup loading/timeout UX, manual retry controls, personnel-data exports, missing-field rendering, and any screen that displays sensitive PII sourced from MPDS.

Does not apply to unrelated frontend work, audit dashboards that read application logs rather than MPDS, or the shared MCC auth foundation itself.

## Decision flow

1. **Enforce all Hard Rules above without exception; do not ask the developer for permission to apply them.**
2. Match the feature to the relevant B/S questions below.
3. Ask all matching questions in one pass with the **Default** pre-filled and the **Context** as the reason.
4. If the user overrides a default, ask that question's **If overriding** follow-ups before coding.
5. Stop for hard-rule conflicts or backend contract changes; update the API/spec/ADR before coding assumptions.
6. Record confirmed answers in the plan, spec, or ADR.

---

## Backend questions

### B1. Template ID resolution

**Question:** How should the application store and resolve the two registered MTM query template IDs (single-UUID and multiple-UUID) at call time?

**Default:** Bind them as `@ConfigurationProperties` values, sourced from environment-specific config alongside `spring.security.eds.mcc.mpds.url`.

**Context:** *(See MPDS Retrieval Standard §2.0 design choice: Template Registration Prerequisite)* The standard only requires that both IDs be available as `queryTemplateId` at call time — it does not prescribe the storage mechanism. A hardcoded constant is simplest for a single-tenant app; a database-backed registry is only needed if templates vary per tenant or request.

**If overriding (database-backed registry):**
- Is the registry keyed by tenant, request type, or both?
- What happens on a registry lookup miss — fail the request, or fall back to a default template?
- Is the registry cached, and if so, how is a template-ID change (re-registration on MTM) propagated without a restart?

---

### B2. Resilience tuning (bulkhead & circuit breaker)

**Question:** Should the bulkhead (`spring.security.eds.mcc.mpds.resilience.bulkhead-max-concurrent-calls`, default: 10) or the circuit breaker defaults (50% failure-rate threshold, 5 minimum calls, 30s wait-duration, 10 sliding-window, 3 half-open probes) be tuned for this application's traffic profile?

**Default:** Keep both sets of defaults unless load testing, MPDS's agreed concurrency capacity for this `clientId`, or a traffic/latency profile that differs significantly from a typical caller indicates otherwise.

**Context:** *(See MPDS Retrieval Standard §2.5.1–§2.5.2 design choices: Bulkhead sizing, Circuit breaker tuning)* Bulkhead concurrency should leave headroom on the servlet thread pool and stay within what MPDS has agreed to sustain for this `clientId`. Circuit breaker sliding-window/wait-duration should match this caller's traffic volume and its tolerance for brief MPDS blips.

**If overriding:**
- What is the application's servlet thread pool size and expected request volume per minute (typical and peak), and has MPDS's service owner confirmed a concurrency capacity for this `clientId`?
- What is the application's tolerance for a false-positive circuit open (e.g. does a brief circuit-open block a critical user-facing flow)?

---

### B3. Retry opt-in

**Question:** Does this integration need automatic retry on transient MPDS failures, on top of the bulkhead and circuit breaker?

**Default:** No — transient failures propagate immediately to the caller as `MpdsRetrievalException`.

**Context:** *(See MPDS Retrieval Standard §3.2 design choice: Retry)* Retry is optional and, if added, must be layered inside the existing `ResilientMpdsGateway` decorator, scoped to the same non-retryable failure modes (validation, circuit-open, bulkhead-full).

**If overriding:**
- What is the acceptable added latency per call from retry attempts (each retry adds to total response time)?
- Does the application have a downstream batch-retry or queue-based recovery path that would make synchronous retry redundant?

---

### B4. Max batch size

**Question:** What should `spring.security.eds.mcc.mpds.max-batch-size` be set to (default: 100)?

**Default:** Keep the default (100) unless the application's UI/UX batch needs or MPDS's agreed per-request cardinality for this `clientId` indicate otherwise.

**Context:** *(See MPDS Retrieval Standard §6.4 design choice: Max batch size)* This value is a starting point, not a fixed limit. The frontend's batch-size validation (S5) must match this value exactly.

**If overriding:**
- Has MPDS's service owner confirmed the maximum cardinality they'll accept per request for this `clientId`?
- Does the frontend's batch-size validation (S5) need to be updated to match?

---

## Frontend questions

### S1. Caching duration

**Question:** Should retrieved personnel data be cached in-memory on the frontend?

**Default:** Cache only for the duration of the current page session; invalidate on logout or explicit refresh.

**Context:** Balances UX speed with security — in-memory caching avoids unnecessary MPDS load while ensuring data is purged when the session ends.

**If overriding:**
- How long counts as "session" — until tab close, until logout, or until N minutes idle?
- Should refreshing one record refresh related records (e.g. other rows showing the same UUID)?
- Are there fields that should always fetch fresh and never use cache (e.g. mobile)?

---

### S2. Missing field rendering

**Question:** How should fields missing from the response be rendered?

**Default:** Mark unavailable/null fields with `—`.

**Context:** Consistent "missing" state prevents users from thinking the app is broken or the data is still loading. Missing fields return `null` from the backend by design (Standard §2.3) rather than being omitted or erroring.

**If overriding:**
- Are there fields where missing data should trigger a warning that the record is incomplete?
- Should missing fields be flagged differently based on their importance (e.g. email vs. height)?

---

### S3. Audit notice wording and placement

**Question:** What exact wording and placement should the audit notice use?

**Default:** Text: "This lookup is recorded for audit purposes." Placement: small text below the search/submit button.

**Context:** Transparency sets user expectations and complies with data-handling policies. This is a frontend-only notice; it does not affect the backend audit event emitted per Standard §3.3.

**If overriding:**
- Should single lookup and bulk lookup screens use different audit notice copy?
- Does the notice need to link to a data-handling policy?

---

### S4. Bulk PII export approval

**Question:** Has this screen been approved for bulk PII export?

**Default:** No — hide all export controls unless the feature has explicit project approval.

**Context:** Bulk export is a high-risk operation that requires separate compliance sign-off, independent of the standard's `single`/`multiple` query cardinality.

**If overriding:**
- Which export formats should be allowed, and what is the maximum number of rows per export?
- Should the export trigger a separate, higher-severity audit event distinct from the lookup audit?
- (Confirmation dialog wording, filename convention, and other cosmetic details belong in the feature spec, not this question.)

---

### S5. Frontend batch-size validation

**Question:** What is the maximum number of UUIDs allowed per submission on the frontend?

**Default:** Match the backend's configured max-batch-size (B4) — 100 by default.

**Context:** The backend enforces this limit before transport (Standard §2.4 point 4); the frontend MUST also enforce it client-side to give immediate user feedback rather than a round-trip rejection.

**If overriding:**
- If the backend maximum (B4) is lower than expected, should the UI show a prominent pre-input note about the limit, or only surface the error when the user exceeds it?
- Should the 'Submit' button be disabled entirely until the count is valid, or allowed to click then show a validation error?
- If the standard's alphabetical sorting (Standard §2.2 point 3) must be bypassed for a specific functional requirement, what is the alternative deterministic property to use for auditing and testing?

---

### S6. Audit notice persistence

**Question:** How should the audit notice be displayed across multiple visits?

**Default:** Always visible (sticky or footer).

**Context:** The Hard Rule requiring the notice whenever personnel data is displayed means hiding it after the first visit would be a compliance violation.

**If overriding:**
- If overriding to a less prominent "collapsible" notice after the first visit, how is "first visit" defined — per session, per browser, or per user account?
- **Standard constraint:** the notice may be collapsed or minimized after acknowledgement, but a persistent indicator (e.g. an "Audited" icon or footer link) must remain visible. How will this persistent indicator be implemented?
