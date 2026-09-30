# 17: OpenAPI spec

**What to build:** An integrator or reviewer can read a machine-readable OpenAPI description of every `/api/*` endpoint the app exposes, including its ProblemDetail error responses and their `code` values, and the CSRF and session requirements. This closes the gap in IM8 pm-6 (system documentation) that the README in issue 16 does not cover. The gap was raised by the issue 01 code-reviewer gate.

**Blocked by:** none. It documents the endpoints delivered across issues 01–15, which are all merged. Independent of issue 16: the README describes the deployment, this describes the API contract.

**Status:** resolved

- [x] An OpenAPI 3 document describes every `/api/*` endpoint, method, request body, success response and ProblemDetail error response (with `code`).
- [x] The document states the CSRF token and session cookie requirements.
- [x] A test fails if an endpoint exists in the app but is missing from the document, or the other way round.
- [x] Whether the document is generated or hand-written, and whether it is served at runtime (and to whom), is decided during triage and recorded here.

## Triage decision (2026-09-30)

**Hand-written, committed at `docs/api/openapi.yaml`; not served at runtime.**

- **Hand-written, not generated.** A drift test (criterion 3) supplies the guarantee that generation would otherwise buy, and it does so without adding `springdoc-openapi` to the shipped dependency tree — which would mean new dependencies to scan under issue 14's `security` profile, and new endpoints that `SecurityConfig`'s `anyRequest().denyAll()` would have to carve out explicitly. The `code` values in the ProblemDetail bodies are the substance of criterion 1 and would need hand-written `@ApiResponse` content either way, so generation would automate only the part the drift test already pins. No new dependency is needed: snakeyaml is already on the compile classpath via Spring Boot.
- **Not served at runtime.** The document is a repo artifact for integrators and reviewers. IM8 pm-6 asks that system documentation exist, not that it be public, and serving it would publish a complete map of `/api/admin/**` to unauthenticated callers and add a `permitAll` carve-out to an otherwise deny-by-default chain. Serving it on the management port was also rejected: it would couple this issue to issue 16's management-port work for no gain over a checkout.

## Comments

<!-- verification -->
### Verification

All four acceptance criteria met.

**What was implemented.** `docs/api/openapi.yaml` (OpenAPI 3.1, hand-written) describes all 16 `/api/*` operations across the 9 controllers — the CSRF bootstrap, registration, login, logout, `hello`, `me`, Password Change, both password-reset calls, client-event reporting, and the six Admin account-administration operations. Each carries its request body schema, its success response, and every ProblemDetail failure response it can produce with the `code` values named and exampled; the four codes that can reach *any* operation (`request_too_large`, `validation`, `method_not_allowed`, `unsupported_media_type`) are stated once in `info.description` instead of repeated 16 times. Two `securitySchemes` — `sessionCookie` (`SESSION`, `HttpOnly`, `SameSite=Lax`, `Secure` outside `dev`, one Session per Account, 15m idle / 8h absolute) and `csrfToken` (`X-CSRF-TOKEN`, session-bound, reissued at login and logout) — are declared and then required operation by operation. `backend/src/test/java/com/example/securedhello/OpenApiDocumentTest.java` (5 tests) pins the document to the application.

**Design notes.** The drift test derives the expected operation set from Spring's `RequestMappingHandlerMapping` at runtime, not from a second hand-maintained list in the test — a list in the test would just move the drift problem one file over. It asserts the two directions separately so a failure says whether the application gained an endpoint or the document invented one. The session cookie and CSRF header *names* are read from a live `GET /api/csrf` response rather than hard-coded, so renaming either in `SessionCookieConfig` or the CSRF repository fails the test instead of leaving the document quietly wrong. The single CSRF exemption (`POST /api/client-events`, ADR 0002) is likewise verified live — the test posts a client event with no token and requires 204 — because it is the one place a reader would otherwise suspect the document of an error.

**One deliberate omission, and why it is not a gap.** A request from an origin outside `app.cors.allowed-origins` is rejected by the CORS filter with `403` and a *plain-text* body, before the chain that would shape a problem body. It is documented in prose under "The one error that is not a ProblemDetail" rather than as a `403` response on each operation, because documenting it as ProblemDetail would be false, and it is a pre-existing accepted deviation ("plain-text CORS 403 body", issue 01 minor notes).

**Verification steps.** Targeted tests only, by request — the full suite was not run. `OpenApiDocumentTest` **5/5**, and a combined run with the two suites whose subject matter this document describes and whose cached Spring context the new test's live probes share: `OpenApiDocumentTest` 5, `ClientEventApiTest` 15, `SecurityBaselineApiTest` 17 — **37 tests, 0 failures, 0 errors**. The shared-context question was checked rather than assumed: `ResetRateLimitersListener` is registered for every Spring test in `META-INF/spring.factories` and empties the limiters before each method, and `ClientEventApiTest` asserts no absolute Micrometer counter value (its one stateful assertion snapshots `sessionRows()` inside the test), so the new test's client-event POST cannot perturb it.

**Every assertion was mutation-checked, not trusted**, each mutation reverted and the document verified byte-identical to its pre-mutation state afterwards. Renaming a path key (`/api/hello` → `/api/hello-gone`) fails the drift test; adding an operation the application does not expose (`POST /api/admin/users/{id}/impersonate`) fails it in the other direction, with the offending operation named. Declaring the wrong cookie name (`SESSIONX`) fails the security-scheme test. Deleting every `csrfToken: []` requirement fails the per-operation CSRF test. Renaming ProblemDetail's `code` property, and pointing the shared `500` response at a non-ProblemDetail schema, each fail the error-response test. Introducing one dangling `$ref` fails the reference test.

**Three inaccuracies were found and fixed in self-review**, which is worth recording because all three were cases of the document being *plausible* rather than *true*: `POST /api/admin/users/{id}/require-password-change` reused the read-only `AdminForbidden` response, which omits `csrf_invalid` although the operation is state-changing — it has no `self_action_forbidden` either, since `AccountAdministrationService.requirePasswordChange` takes only the target id, so it needed a third response (`AdminWriteForbidden`) rather than either existing one. The Client Event `path` cap was stated as 64 characters after the leading slash; the pattern allows 63. And the 20 `type: about:blank` scalars were left unquoted, which is valid YAML but relies on a parser treating a colon not followed by a space as literal, so they are now quoted.

**Deliberately not done.** No dependency was added and no endpoint serves the document, per the triage decision above. `README.md` and `backend/pom.xml` were not touched: issue 16 is in flight on both, and this issue needs neither.

**Open item for handoff.** The document is verified against the application and internally consistent (every `$ref` resolves), but it has **not** been validated against the OpenAPI 3.1 meta-schema — no offline validator is available in this environment, and adding one would have meant the dependency the triage decision declined. A human should run `npx @redocly/cli lint docs/api/openapi.yaml` (or equivalent) once before handoff. Also not exercised: the JaCoCo coverage gate, which needs the full suite, and the `code-reviewer` compliance gate and AWS Bedrock KB lanes, both skipped by request.

**Checklist:** all four acceptance-criteria boxes ticked.

No do-work HTML log was generated: the reviewer subagent loop and KB logging were not used for this issue.
