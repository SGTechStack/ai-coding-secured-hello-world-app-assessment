# demo-app

Full-stack monorepo: Spring Boot 4 backend (`backend/`, Java 21) serving a React 19 SPA (`frontend/`). Production serves the pre-built frontend as static files; locally they run as separate dev servers.

Backend and frontend each have their own `CLAUDE.md`; read the one for the area you edit.

## Conventions

- Secrets in `dev`/`qa`/`prod` come from AWS Secrets Manager; commit no credentials.
- Backend config is profile-layered: `application.properties` (prod defaults) overridden by `application-{profile}.properties`.
- Extract non-trivial literals into named constants with a purpose comment: an **enum** for a closed set of variants, a **constant** for a single fixed literal, a **property** only when the value varies by profile or needs tuning without a code change.
- Commits: Conventional Commits, one logical change each (`git-commit` skill). Generated files are committed only where documented (`docs/openapi.json`, `frontend/src/api/generated/`).
- Reports to the user: extremely concise, grammar sacrificed for concision.

## Where to look

- Run locally, H2 console, external SQL clients: [README.md](README.md)
- Runtime profiles and feature flags: [backend/docs/runtime-profiles.md](backend/docs/runtime-profiles.md)
- Regenerating OpenAPI specs and the frontend client: [docs/api-client-generation.md](docs/api-client-generation.md)
- ADRs: `docs/adr/` (system-wide), `backend/docs/adr/`, `frontend/docs/adr/`. Number a new one as highest existing + 1 in that directory; see [ADR-0001](docs/adr/ADR-0001-adr-naming-convention.md).
- Domain vocabulary: [CONTEXT.md](CONTEXT.md)

## Verify

Backend: `./mvnw spotless:apply` then `./mvnw verify`. Frontend: `npm run lint`, `npm run test:ci`, and `npm run build` after every change.
