# demo-frontend

React 19 + Vite + TypeScript (strict) SPA. TanStack Router (file-based) and Query, orval-generated API client, Base UI + Tailwind v4. Stack versions and scripts: `package.json`. Path aliases (`@lib/`, `@features/`, `@components/`): `tsconfig.app.json`.

## Architecture

### Route files are thin

`src/routes/**` holds only `beforeLoad`, `loader`, and `component`. All JSX and state live in a page component under `src/features/<feature>/`, imported by the route. Create routes with `createFileRoute('/<path>')`; `routeTree.gen.ts` is generated, never edited.

### Feature query files wrap orval

`features/<feature>/<feature>.queries.ts` wraps the generated hooks and owns:

- `queryOptions()` factories for route loaders, with `select` unwrapping the `{ data }` envelope
- cache invalidation cascades between related queries
- 422 field-error mapping (`fieldErrors` on `ApiError`)

Components import from the wrapper, never from `src/api/generated/`. Loaders prefetch with `queryClient.ensureQueryData(...)`; data-critical components use `useSuspenseQuery`.

### HTTP

`fetch` is the only HTTP client. Feature code never calls it directly: generated hooks go through `lib/openapi-orval-mutator.ts`, which delegates to `lib/http.ts` (CSRF header, 401 → `session:expired`). One-off calls use `http()`.

### Generated API client

`src/api/generated/**` and `docs/openapi.json` are committed, never hand-edited. Adding or changing an endpoint: follow [docs/api-client-generation.md](../docs/api-client-generation.md), then add the wrapper in the feature's `.queries.ts`. Commit spec, generated code, and backend change together.

### Validation

Zod is for UX form validation only (trim, max length, regex, "must be > 0"), in `<feature>.schema.ts` beside the queries file. API responses are not parsed at runtime: backend and frontend ship from one commit. Forms render server 422 field errors alongside Zod errors.

## Code conventions

- **Named parameters**: destructured object params, never positional. Reordered same-type args compile silently.
- **Named constants**: extract non-trivial literals (dimensions, durations, caps, domain strings) into constants with a right-side purpose comment. Many constants in one module: one `as const` namespace object.
- **React Compiler** memoizes automatically. Write no `useMemo`, `useCallback`, or `memo` (react-doctor flags them).
- **Styling**: Tailwind v4, PRIZM semantic tokens (see UI doc).

## UI components

Before adding or modifying anything in `src/components/ui/`, read [docs/ui-components.md](docs/ui-components.md). Every change there updates `src/components/component-registry.ts`.

## Verify

After every frontend change, before committing:

1. `npm run lint`
2. `npx tsc --noEmit`
3. `npm run test:ci`
4. `npx react-doctor@latest`
5. `npm run build`

Prettier formatting is handled by lint-staged on commit.
