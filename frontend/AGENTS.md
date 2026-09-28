# Frontend agent notes

React 19 + TypeScript, Vite build, `oxlint` for linting. See root `ARCHITECTURE.md` for module boundaries and data flows, root `DESIGN.md` for the visual design system, and root `AGENTS.md` for repo-wide conventions — all apply here.

## Build & test

- Dev server: `npm run dev`. Production build: `npm run build` (runs `tsc -b` first, so type errors fail the build). Lint: `npm run lint`.
- No test runner is configured yet — check `package.json` before assuming one exists.

## Conventions

- One component per file under `src/`, named after the component (`LoginForm.tsx`, `AdminUsersPage.tsx`). Shared API calls go through `src/api.ts`; auth state through `src/AuthContext.tsx`. Add new endpoints there rather than calling `fetch` directly from components.
- Wrap new top-level routes/pages in `ErrorBoundary` (see `src/ErrorBoundary.tsx`) with a generic fallback — never surface raw error details to the user.
- Public exports need TSDoc comments (enforced by the `gen-code-docs`/TypeDoc gate) — internal, non-exported props/state types do not.
