# Use orval (OpenAPI client) for frontend API type and hook generation

The backend generates an OpenAPI 3.1 spec at build time via springdoc. Rather than hand-writing TypeScript types and TanStack Query hooks that duplicate backend DTOs, we use `orval` to generate both from `docs/openapi.json`. The generated output lives in `frontend/src/api/generated/` and is committed to the repo; a CI drift check runs before the frontend and backend build jobs and fails the pipeline if the committed spec no longer matches what Maven generates.

## Custom mutator

Orval is configured with a custom fetch mutator (`src/lib/openapi-orval-mutator.ts`) that delegates to the existing `lib/http.ts` wrapper. This means all generated hooks automatically inherit CSRF token handling and the 401 → session-expired dispatch without any additional setup.

## Considered Options

**axios** — orval has first-class axios support but would add a new dependency when `lib/http.ts` already provides the same capabilities (CSRF headers, 401 handling). Rejected in favour of the existing native fetch wrapper.

**openapi-typescript (types only)** — generates types but not hooks, leaving all query/mutation boilerplate hand-written. Rejected because orval eliminates that boilerplate entirely.

**Manual types and queries** — fragile: any backend DTO change requires a coordinated manual frontend update with no compiler enforcement of the API contract.

## Wrapper pattern

Orval-generated hooks handle the happy path. Project-specific behaviour (cache invalidation cascades, 422 field-error mapping) lives in thin per-feature wrapper files (e.g. `user.queries.ts`) that call the generated hooks internally. The generated file is never edited; all customisation is additive.

## Consequences

Every backend DTO change requires regenerating `docs/openapi.json` (`./mvnw verify`) and `frontend/src/api/generated/` (`npm run generate:api`) as part of the same commit. The CI drift check enforces this.