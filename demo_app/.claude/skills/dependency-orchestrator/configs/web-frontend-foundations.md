---
name: web-frontend-foundations
description: Standard dependency graph template for web frontend applications. Covers universal scaffolding: design tokens, component library, routing/state/API client, auth UI, and architectural tests & linting. Feature-specific nodes are derived from user stories via backwards mapping.
---

# Web Frontend Foundations

When used inside the dependency-orchestrator, return the Foundation Template JSON and Story Groups.

## Core Foundation Nodes

Always included in every web frontend app. These form the base DAG that all feature nodes attach to.

### Template JSON

```json
{
  "nodes": [
    { "id": "project_setup",    "name": "Project Setup & Dev Tooling",                              "category": "infrastructure" },
    { "id": "design_tokens",    "name": "Design System (colors, typography, spacing tokens)",        "category": "design" },
    { "id": "component_lib",    "name": "Core Component Library (atoms: Button, Input, Card, etc.)", "category": "frontend" },
    { "id": "routing_setup",    "name": "Routing Configuration",                                    "category": "frontend" },
    { "id": "state_management", "name": "Data Fetching & Cache Setup (QueryClient / store)",         "category": "frontend" },
    { "id": "api_client",       "name": "API Client & Data Layer",                                  "category": "frontend" },
    { "id": "auth_ui",          "name": "Authentication (UI, context, session & route guards)",      "category": "frontend" },
    { "id": "arch_tests",       "name": "Architectural Tests & Linting (lint rules, import boundaries, code style)", "category": "testing" }
  ],
  "edges": [
    { "from": "project_setup",  "to": "design_tokens" },
    { "from": "project_setup",  "to": "routing_setup" },
    { "from": "project_setup",  "to": "state_management" },
    { "from": "project_setup",  "to": "api_client" },
    { "from": "project_setup",  "to": "arch_tests" },
    { "from": "design_tokens",  "to": "component_lib" },
    { "from": "component_lib",  "to": "auth_ui" },
    { "from": "api_client",     "to": "auth_ui" }
  ]
}
```

`arch_tests` starts after `project_setup` (needs the project to exist) and runs in parallel with everything else. All feature nodes must pass arch rules.

## Story Groups

```json
{
  "story_groups": [
    {
      "id": "INFRA-FE-01",
      "title": "Frontend data & routing layer is ready",
      "owned_nodes": ["project_setup", "routing_setup", "state_management", "api_client", "arch_tests"],
      "verifiable": "App loads, routes navigate correctly, API client configured, data fetching works, arch tests pass in CI"
    },
    {
      "id": "INFRA-FE-02",
      "title": "Design system & component library is ready",
      "owned_nodes": ["design_tokens", "component_lib"],
      "verifiable": "Design tokens applied, core atoms (Button, Input, Card) render correctly with token values"
    },
    {
      "id": "INFRA-FE-03",
      "title": "Users can log in via the frontend",
      "owned_nodes": ["auth_ui"],
      "verifiable": "Login/logout works, session persists across page refreshes, protected routes redirect to login"
    }
  ]
}
```

**Why the split?** `INFRA-FE-01` completes early — feature pages that need routing, data fetching, and arch rules can start immediately. `INFRA-FE-02` takes longer (the `design_tokens → component_lib` chain) but only `auth_ui` and pages needing styled atoms are blocked by it. This lets backend-dependent feature work begin earlier.

Tooling for each core node is configured in the [Project Configuration](#project-configuration) section at the end of this file.

### Dependency Rationale

- **design_tokens** — shared color/spacing/typography values (`tailwind.config`, CSS variables, theme file). Must exist before components can be styled.
- **state_management** — data fetching library setup (`QueryClient` + provider, or Redux store). Every data-fetching component needs this wrapper.
- **component_lib** → depends on `design_tokens` — atoms (Button, Input, Card) consume token values.
- **auth_ui** → depends on `component_lib` (login form uses shared atoms) and `api_client` (auth calls backend).
- **arch_tests** — ESLint rules, import boundary enforcement (e.g., no direct API calls from components, barrel file conventions), code style. Runs in CI from Day 0.25.

**Parallel after project_setup:** Once `project_setup` completes, `design_tokens`, `routing_setup`, `state_management`, `api_client`, and `arch_tests` all run in parallel.

### Foundation Connection Points

Every feature node derived by backwards mapping depends on these foundation nodes:
- **All feature pages** → depend on `arch_tests`, `routing_setup`, `state_management` (from `INFRA-FE-01`) and `component_lib` (from `INFRA-FE-02`)
- **Data-fetching features** → additionally depend on `api_client` (from `INFRA-FE-01`)
- **Auth-gated pages** → additionally depend on `auth_ui` (from `INFRA-FE-03`)

---

## Project Configuration

Editable: change the **Recommended** column to swap tooling (must exist in `duration-defaults.md`). Do NOT edit Node IDs, Alternatives, or Edges.

### Core Foundation Tooling

| Node ID | Recommended | Alternatives |
|---|---|---|
| `project_setup` | Vite scaffold | Next.js, Nuxt, SvelteKit |
| `design_tokens` | Tailwind CSS config | CSS variables, theme file |
| `component_lib` | shadcn/ui | Vuetify, Skeleton, other pre-built library |
| `routing_setup` | React Router | Vue Router, SvelteKit routes |
| `state_management` | TanStack Query | SWR, Pinia |
| `api_client` | Axios wrapper with interceptors | fetch wrapper with interceptors |
| `auth_ui` | Auth provider + route guards (using component_lib atoms) | — |
| `arch_tests` | ESLint flat config + import boundary rules | — |

### Feature Module Tooling

The backwards mapper derives these from stories. When a story's backward chain reveals a need for one, the mapper uses the tooling below and looks up `duration_days` from `duration-defaults.md`.

| Node ID | Description | Recommended | Alternatives | Edges |
|---|---|---|---|---|
| `fe_data_table` | Data table with sorting, filtering, pagination | TanStack Table | AG Grid | `component_lib → fe_data_table` |
| `fe_rich_text_editor` | Rich text / WYSIWYG editor | TipTap | Slate, Lexical | `component_lib → fe_rich_text_editor` |
| `fe_file_upload` | File upload UI with drag-and-drop, preview, progress | react-dropzone | Uppy | `component_lib → fe_file_upload` |
| `fe_charts` | Charts and data visualisation | Recharts | Chart.js, Victory | `component_lib → fe_charts` |
| `fe_realtime` | Real-time UI updates (live data, presence) | Socket.io client | SSE EventSource | `api_client → fe_realtime` |
| `fe_drag_drop` | Drag-and-drop interactions (kanban, reorder) | dnd-kit | react-beautiful-dnd | `component_lib → fe_drag_drop` |
| `fe_search` | Search with autocomplete and results display | Autocomplete component + debounced API | — | `api_client → fe_search` |
| `fe_map` | Interactive map with markers and overlays | Leaflet | Mapbox GL, Google Maps | `component_lib → fe_map` |
| `fe_form_builder` | Dynamic / configurable form generation | React Hook Form + dynamic schema renderer | — | `component_lib → fe_form_builder` |
| `fe_pdf_viewer` | In-browser PDF viewing and annotation | react-pdf | PDF.js | `component_lib → fe_pdf_viewer` |
| `fe_mfa_standalone` | MFA enrolment + challenge UI (standalone) | React MFA module (PIN/TOTP screens) | — | `component_lib → fe_mfa_standalone` |
| `fe_mfa_mcc` | MFA enrolment + challenge UI (MCC) | React MFA module (OTP + cloud TOTP screens) | — | `component_lib → fe_mfa_mcc` |

### Infrastructure Addition Tooling

The backwards mapper derives these from stories (e.g., a story mentioning "performance budget" → `performance_optimization`).

| Node ID | Description | Recommended | Alternatives | Edges |
|---|---|---|---|---|
| `e2e_tests` | E2E or integration tests | Selenium | Playwright, Cypress | feature pages → `e2e_tests` |
| `accessibility_audit` | Accessibility compliance | axe-core + eslint-plugin-jsx-a11y | pa11y, Lighthouse CI | feature pages → `accessibility_audit` |
| `analytics_integration` | Analytics/tracking | PostHog | Mixpanel, Google Analytics | feature pages → `analytics_integration` |
| `performance_optimization` | Perf / Core Web Vitals | Lighthouse CI + bundlesize | web-vitals library | `e2e_tests → performance_optimization` |
