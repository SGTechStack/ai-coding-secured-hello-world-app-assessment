# INFRA-FE-01: Frontend data & routing layer is ready

`infrastructure` · wave 1

| Effort | Float |
| --- | --- |
| 2.3 days | 0.5 days |

## Acceptance criteria

- `npm run dev` serves the app on `http://localhost:3000`.
- React Router routes exist for `/`, `/login`, `/register`, `/forgot-password`, `/reset-password`, `/admin/users`.
- The API client sends `credentials: 'include'`, attaches the CSRF header on mutations, and maps `ProblemDetail` errors to a typed `ApiError`.
- `npm run lint`, `npm run typecheck` and `npm test` pass.

## Dev tasks

1. `project_setup` (0.25 d) — Vite + React 19 + TypeScript strict, Vitest + Testing Library.
2. `routing_setup` (0.25 d) — `AppRoutes` with a layout shell.
3. `state_management` (0.25 d) — TanStack `QueryClient` provider.
4. `api_client` (0.5 d) — `apiFetch` wrapper, CSRF token cache, `ApiError`; unit tests.
5. `fe_arch_tests` (0.5 d) — ESLint flat config (typescript-eslint, react-hooks), Prettier.

## Dependencies

- Blocked by: —
- Unblocks: INFRA-FE-03, 1, 6
