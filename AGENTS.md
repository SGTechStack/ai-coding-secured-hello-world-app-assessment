# AGENTS.md

Guidance for agents working in this repository.

## Agent skills

### Issue tracker

Issues and specs for `SGTechStack/ai-coding-secured-hello-world-app-assessment` are GitHub issues, handled with `gh`. See `docs/agents/issue-tracker.md`. The assessment slices themselves are listed in `docs/spec/implementation-plan.md`.

### Triage labels

Use the five default triage labels. See `docs/agents/triage-labels.md`.

### Domain docs

This is one application. Read `CONTEXT.md` at the repo root, then `docs/adr/` for the decisions behind sessions, reset tokens, and passwords. See `docs/agents/domain.md`.

## Working on this app

The API is Spring Boot in `backend/`. The UI is React in `frontend/`. Auth is a server session in the `HELLOSESSION` cookie, not a bearer token. Keep CSRF on for state-changing routes. Do not log passwords or password hashes.

Work stays on the author's branch. Do not commit to `main` or to another participant's branch.
