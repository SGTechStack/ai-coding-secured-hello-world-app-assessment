# 17: OpenAPI spec

**What to build:** An integrator or reviewer can read a machine-readable OpenAPI description of every `/api/*` endpoint the app exposes, including its ProblemDetail error responses and their `code` values, and the CSRF and session requirements. This closes the gap in IM8 pm-6 (system documentation) that the README in issue 16 does not cover. The gap was raised by the issue 01 code-reviewer gate.

**Blocked by:** none recorded yet (triage should set this; it documents endpoints delivered across issues 01–15)

**Status:** needs-triage

- [ ] An OpenAPI 3 document describes every `/api/*` endpoint, method, request body, success response and ProblemDetail error response (with `code`).
- [ ] The document states the CSRF token and session cookie requirements.
- [ ] A test fails if an endpoint exists in the app but is missing from the document, or the other way round.
- [ ] Whether the document is generated or hand-written, and whether it is served at runtime (and to whom), is decided during triage and recorded here.
