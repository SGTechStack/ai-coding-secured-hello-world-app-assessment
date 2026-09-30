# KB Router

Use when AWS Bedrock KB retrieval is available. Select KB lanes from concrete file paths, symbols, dependencies, and framework APIs in the code/config being implemented or reviewed.

Do not rely on pre-classified areas. Query only lanes with concrete evidence.

| Signal | Candidate query prefix |
| --- | --- |
| Java backend files, Spring annotations, controllers, repositories, JPA, validation, Maven/Gradle backend config | `Spring Boot` |
| TSX/JSX components, hooks, effects, event handlers, rendering, accessibility, browser state | `React` |
| `useQuery`, `useMutation`, `QueryClient`, `queryKey`, invalidation, optimistic updates, query functions | `TanStack Query` |
| `createRoute`, route files, `Link`, `useNavigate`, search params, route loaders | `TanStack Router` |
| TanStack form APIs, validation timing, field binding, submit handling | `TanStack Form` |
| TanStack table APIs, column definitions, sorting, filtering, pagination, virtualization | `TanStack Table` |
| Setup/config with no framework lane | Relevant product phrase from setup files/config |

Build each query as:

```text
<query prefix> <code signal> <goal or failure>
```

Examples:

```text
React defer await sequential data fetching
TanStack Query mutation invalidation queryKey
Spring Boot transactional readOnly repository projection
```
