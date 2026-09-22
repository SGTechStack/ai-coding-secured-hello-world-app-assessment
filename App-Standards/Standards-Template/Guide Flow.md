# Application Standards Template

❗ Do Not

❌ Do not describe the library’s internals, classes, or file layout.

❌ Do not say “this is not implemented” — instead, state what must be implemented in future apps.

❌ Do not invent functionality.

❌ Do not reference optional features unless the code enforces or directly supports them.

✅ Do

✅ Use only Markdown — no XML, HTML, or angle brackets.

✅ Describe observable behaviors as enforceable design rules.

✅ Reference applicable standards (e.g., OWASP, NIST, RFCs, ISO/IEC) only when directly supported by the code.

✅ Separate Base-level standards from Org-level policies.

📄 Output Format: [CAPABILITY NAME] Application Standard

1. Overview

Purpose (1 paragraph): What this capability enables (e.g., “Adds a second authentication factor to protect sensitive operations.”)

Scope: What kinds of systems should follow this standard.

Definitions: 5–7 key behavioral concepts (e.g., “challenge ID”, “principal”, “delivery mechanism”).

2. Standard Flow

Step-by-step happy path behavior.

Step-by-step failure paths with rejection, retry, fallback rules.

Any decision logic enforced in the implementation.

✅ Optional: If the flow is clean and fully traceable, include a PlantUML sequence diagram. Only do this if:

No steps are guessed or invented.

Initiators and responders are clearly modeled in code.

3. Best Practices & Contracts

Split into two sections under each subsection:

✅ Base Standard (e.g., NIST, RFC, OWASP-aligned)

🏢 Org Standard (company-wide rule or policy enforced in the library)

3.1 Inputs / Outputs

Required and optional inputs

Validation logic (e.g., format checks, enum matching)

Outputs and side effects (persistence, tokens issued, state cleared)

3.2 Error Contract

Common failure categories (input validation, missing state, expired session, etc.)

Retryability (retryable vs terminal errors)

HTTP status codes or transport mapping (if observable)

3.3 Audit Contract

When audit events MUST be emitted

What fields each event MUST include (actor, timestamp, target, etc.)

If no audit is present, clearly state that audit must be implemented by integrators

3.4 Logging Contract

✅ Structured log fields: correlation ID, principal ID, source IP, etc.

✅ Required log levels (info on success, warn on failure)

🏢 Include logging conventions for batch jobs, interface transactions, or domain-specific events — if present in code.

❌ Prohibited log content: sensitive fields (codes, secrets, raw credentials)

3.5 Security Contract

✅ Enforced constraints: identity required, roles checked, token formats

✅ TTL or expiry logic

✅ Use of secure storage (e.g., secrets encrypted with KMS)

✅ Reference any applicable standard (e.g., RFC 6238 for TOTP, NIST 800-63B for OTP length)

4. Architectural Design

Describe design choices visible in the implementation.

Split into relevant groups only if observable, such as:

Runtime Context: Where is the identity context read from?

State Model: Ephemeral? Durable store? Redis?

Synchronous vs Async: Does the flow block the user?

Separation of Concerns: Are responsibilities decoupled? (e.g., generation ≠ delivery)

External Assumptions: Any reliance on KMS, queues, auth gateways?

Interoperability: Any use of standardized formats or URI schemes (e.g., otpauth://)

Cross-Cutting Behavior: Logging, exception handling, interceptors, AOP — without naming the library/framework

Each observation should be labeled as:

Enforced Constraint — strictly implemented in code

Design Choice — implementation pattern

Assumption — requires external integration

5. Reimplementation Recipes

📝 Output this as a separate Markdown file, titled:

# [CAPABILITY NAME] – Reimplementation Recipes


Each recipe must:

Teach the developer how to reimplement a feature without using the old library

Use step-by-step structure like a Baeldung guide

Include real code blocks (Java, YAML, pseudo-code)

Stay focused: One recipe = One feature

Only include features present in the codebase

Examples:

✅ Generate a TOTP Code from a Shared Secret (RFC 6238)

✅ Export a Report with JasperReports

✅ Persist and Validate a User PIN Securely

6. Test & Validation Standard

Unit tests present in the codebase

Flows that MUST be covered by app integration tests

Gaps to be filled by the integrator

✅ Test data guidelines: use deterministic test codes, no PII, etc.

7. Operational Runbook

Observable logs and metrics

Manual vs self-healing error modes

External failure sources (e.g., KMS, external queues)

Config and secrets required at runtime

8. Appendix

Glossary: Defined terms used in the doc

Changelog: Date/version of the standard

Standards Referenced: RFCs, OWASP, NIST, ISO/IEC — only if behavior is clearly aligned
