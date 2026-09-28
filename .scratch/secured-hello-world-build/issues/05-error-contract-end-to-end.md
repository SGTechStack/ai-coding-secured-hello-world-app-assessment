# 05: Error contract end to end

**What to build:** Every non-actuator error body is RFC 9457 `application/problem+json` with a `code` extension, written only by `ProblemDetailWriter` (ADR-031). The closed code enum maps each code to exactly one status, with a constant `detail` per code. `sendError` is prohibited, and `/actuator/**` is exempt (REJ-066). The build generates two things from the enum:
- the prose `error-contract.md` (R-AUTH-003);
- a JSON Schema that validates every backend error body (T-AUTH-011) and every SPA MSW error fixture (T-AUTH-012).

The main entry point matches `fetch` requests, so the SPA's `Accept` header is part of the server's configuration (T-AUTH-015). 401s carry no `WWW-Authenticate` (R-AUTH-005). On the SPA side, a typed error client branches only on `code` (REJ-092).

This ticket also builds the default-deny authorization matrix skeleton (ADR-043):
- an unmatched route is denied;
- `/api/admin/roles/**` is `denyAll()`, placed before the admin guards;
- an unhandled exception produces `INTERNAL_ERROR`.

**Blocked by:** 01, 02

**Status:** ready-for-agent

- [ ] An unhandled exception returns 500 `INTERNAL_ERROR` in the envelope, with no stack trace.
- [ ] An unmatched route returns the envelope, never a container error page.
- [ ] Any `/api/admin/roles/**` request is refused.
- [ ] The build regenerates `error-contract.md` and the JSON Schema from the enum, and drift fails the build.
- [ ] A backend body that doesn't match the schema fails a test. An SPA fixture that doesn't match the schema fails Vitest.
- [ ] An ArchUnit or grep check forbids `sendError` in main code.
