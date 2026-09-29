# Reviewer Decisions

Human decisions on reviewer `Human Decision Needed` findings. Apply a matching entry instead of asking again.

## Request body size limit for chunked / length-less bodies

- **Area:** Backend request-body limit filter (`RequestBodyLimitFilter`), issue 01 walking-skeleton.
- **KB finding:** None (KB unavailable). Reviewer finding: bodies without `Content-Length` bypassed the up-front size rejection; oversized chunked bodies reached auth/controllers, and `getReader()` was not wrapped.
- **Chosen action:** When `Content-Length` is absent, buffer up to limit+1 bytes in the filter and reject with 400 `request_too_large` before CSRF, auth, or controllers. Enforce the limit on both `getInputStream()` and `getReader()`. Cover the chunked path with a test. (Rejected: refusing all length-less bodies; accepting read-time-only enforcement.)
- **When to reuse:** Any request-size / body-limit enforcement question — enforce pre-controller regardless of transfer encoding, with `request_too_large` as the error code.

## Non-blocking reviewer notes to fold into correction passes

- **Area:** Issue 01 minor review notes.
- **KB finding:** None (KB unavailable).
- **Chosen action:** Fix issues that prevent near-term bugs (catch-all `@ExceptionHandler(Exception.class)` must re-throw Spring Security `AccessDeniedException`/`AuthenticationException`; `VITE_API_ORIGIN` must be a bare origin with no path/query/fragment). Skip cosmetic/low-risk ones (plain-text CORS 403 body; `aria-live` on placeholder loading text).
- **When to reuse:** Fold cheap, future-bug-preventing minor notes into the correction pass; leave cosmetic notes on placeholder UI or browser-unreadable responses.

## Code-reviewer gate: controls owned by a later issue

- **Area:** Code-reviewer validation gate (IM8 and similar checks), issue 01.
- **KB finding:** None (KB unavailable). IM8 flagged lm-15, lm-16, as-4, as-5, as-6, as-11, ac-6 and pm-6 as Critical/High, but each is explicitly owned by a later issue file (02, 14, 06/07, 03, 03, 05, 10/13, 16).
- **Chosen action:** Treat them as tracked deferrals, not blockers, for the current issue's gate. They count as blockers again once the gate runs for the owning issue.
- **When to reuse:** Any gate finding for a control that a later issue file assigns to itself (cite the issue file and line). A finding with no owning issue is still a blocker.

## Code-reviewer gate: ADR-accepted deviations awaiting risk-owner sign-off

- **Area:** IM8 ac-2 (MFA), ac-3 (inactive accounts), ac-4 (access review).
- **KB finding:** None (KB unavailable). Accepted deviations in `docs/adr/0001-app-standards-override-prd.md`; IM8 still wants risk-owner sign-off.
- **Chosen action:** The ADR is enough for the code gate; not a blocker. Risk-owner sign-off is a governance item tracked outside the code commits.
- **When to reuse:** Any gate finding for a control that an accepted ADR records as a deviation.

## OpenAPI spec gap (IM8 pm-6)

- **Area:** System documentation.
- **KB finding:** None (KB unavailable). No issue planned an OpenAPI spec; issue 16 covers only the README.
- **Chosen action:** Tracked as a new issue, `.scratch/secured-hello-world/issues/17-openapi-spec.md` (needs-triage). Not a blocker for earlier issues.
- **When to reuse:** Gate findings about missing API documentation before issue 17 is done.

## Chunked-body wrapper replays bytes only (no form decoding)

- **Area:** `RequestBodyLimitFilter`, issue 01 (thermo-nuclear High, raised twice).
- **KB finding:** None (KB unavailable). Decoding form fields in the filter duplicates servlet parsing that no endpoint needs.
- **Chosen action:** The buffered chunked-body wrapper only replays the body bytes (`getInputStream()` / `getReader()`). A chunked `application/x-www-form-urlencoded` post therefore loses its body form fields and fails CSRF with 403 (fail closed). The SPA sends CSRF as a header. All API endpoints, including issue 04's login, must accept JSON bodies, not form posts. Supersedes the earlier "malformed chunked form body → 400 validation" behaviour; the size limit (400 `request_too_large`) still applies.
- **When to reuse:** Any request-body wrapper or endpoint body-format question: keep wrappers byte-replay only and use JSON request bodies.

## Frontend error and performance reporting (IM8 lm-16, SPA side)

- **Area:** SPA observability.
- **KB finding:** None (KB unavailable). The SPA reports no client-side errors or performance data; no issue owned it.
- **Chosen action:** Added to issue 14's scope (`.scratch/secured-hello-world/issues/14-operations-metrics-and-dependency-scan.md`). Tracked deferral for earlier issues.
- **When to reuse:** Gate findings about SPA-side telemetry before issue 14 is done.
