---
name: mobile-app-foundations
description: Standard dependency graph template for mobile applications (iOS/Android/React Native/Flutter). Covers universal scaffolding: design tokens, navigation, components, auth screens, and architectural tests & linting. Feature-specific nodes are derived from user stories via backwards mapping.
---

# Mobile App Foundations

When used inside the dependency-orchestrator, return the Foundation Template JSON and Story Groups.

## Core Foundation Nodes

Always included in every mobile app. These form the base DAG that all feature nodes attach to.

### Template JSON

```json
{
  "nodes": [
    { "id": "project_setup",    "name": "Project Setup & Dev Tooling",                              "category": "infrastructure" },
    { "id": "design_tokens",    "name": "Design System (colors, typography, spacing tokens)",        "category": "design" },
    { "id": "native_modules",   "name": "Native Modules & Permissions",                             "category": "mobile" },
    { "id": "navigation_setup", "name": "Navigation Setup",                                         "category": "mobile" },
    { "id": "state_management", "name": "Data Fetching & Cache Setup (QueryClient / store)",         "category": "mobile" },
    { "id": "api_client",       "name": "API Client",                                               "category": "mobile" },
    { "id": "component_lib",    "name": "Core Component Library (atoms: Button, Input, Card, etc.)", "category": "mobile" },
    { "id": "auth_screens",     "name": "Authentication (screens, context, session & route guards)", "category": "mobile" },
    { "id": "arch_tests",       "name": "Architectural Tests & Linting (lint rules, import boundaries, code style)", "category": "testing" }
  ],
  "edges": [
    { "from": "project_setup",    "to": "design_tokens" },
    { "from": "project_setup",    "to": "native_modules" },
    { "from": "project_setup",    "to": "navigation_setup" },
    { "from": "project_setup",    "to": "state_management" },
    { "from": "project_setup",    "to": "api_client" },
    { "from": "project_setup",    "to": "arch_tests" },
    { "from": "design_tokens",    "to": "component_lib" },
    { "from": "component_lib",    "to": "auth_screens" },
    { "from": "api_client",       "to": "auth_screens" }
  ]
}
```

`arch_tests` starts after `project_setup` and runs in parallel with everything else. All feature nodes must pass arch rules.

## Story Groups

```json
{
  "story_groups": [
    {
      "id": "INFRA-MOB-01",
      "title": "Mobile data, navigation & platform layer is ready",
      "owned_nodes": ["project_setup", "navigation_setup", "state_management", "api_client", "native_modules", "arch_tests"],
      "verifiable": "App launches, navigation works, API client configured, permissions granted, arch tests pass in CI"
    },
    {
      "id": "INFRA-MOB-02",
      "title": "Design system & component library is ready",
      "owned_nodes": ["design_tokens", "component_lib"],
      "verifiable": "Design tokens applied, core atoms (Button, Input, Card) render correctly with token values"
    },
    {
      "id": "INFRA-MOB-03",
      "title": "Users can log in via the mobile app",
      "owned_nodes": ["auth_screens"],
      "verifiable": "Login/logout works, session persists across app restarts, protected screens redirect to login"
    }
  ]
}
```

**Why the split?** `INFRA-MOB-01` completes early — feature screens needing navigation, data fetching, and native modules can start immediately. `INFRA-MOB-02` takes longer (the `design_tokens → component_lib` chain) but only `auth_screens` and screens needing styled atoms are blocked by it.

Tooling for each core node is configured in the [Project Configuration](#project-configuration) section at the end of this file.

### Dependency Rationale

- **design_tokens** — shared color/spacing/typography values (theme file / design system constants). Must exist before components.
- **native_modules** — native permission handling, platform bridges. Required for push notifications and device APIs.
- **component_lib** → depends on `design_tokens` — atoms consume token values.
- **auth_screens** → depends on `component_lib` (login screens use shared atoms) and `api_client` (auth calls backend).
- **arch_tests** — lint rules, import boundary enforcement, code style. Runs in CI from Day 0.25.

**Parallel after project_setup:** Once `project_setup` completes, `design_tokens`, `native_modules`, `navigation_setup`, `state_management`, `api_client`, and `arch_tests` all run in parallel.

### Foundation Connection Points

Every feature node derived by backwards mapping depends on these foundation nodes:
- **All feature screens** → depend on `arch_tests`, `navigation_setup`, `state_management` (from `INFRA-MOB-01`) and `component_lib` (from `INFRA-MOB-02`)
- **Data-fetching screens** → additionally depend on `api_client` (from `INFRA-MOB-01`)
- **Auth-gated screens** → additionally depend on `auth_screens` (from `INFRA-MOB-03`)
- **Notification features** → additionally depend on `native_modules` (from `INFRA-MOB-01`)

---

## Project Configuration

Editable: change the **Recommended** column to swap tooling (must exist in `duration-defaults.md`). Do NOT edit Node IDs, Alternatives, or Edges.

### Core Foundation Tooling

| Node ID | Recommended | Alternatives |
|---|---|---|
| `project_setup` | Expo scaffold | React Native CLI, Flutter scaffold |
| `design_tokens` | Theme config (colors, spacing, typography constants) | — |
| `native_modules` | expo-modules | react-native link, native plugins |
| `navigation_setup` | React Navigation | Expo Router, GoRouter (Flutter) |
| `state_management` | TanStack Query | Riverpod, Provider (Flutter) |
| `api_client` | Axios wrapper with interceptors | Dio (Flutter), fetch wrapper |
| `component_lib` | React Native Paper | NativeBase |
| `auth_screens` | Auth provider + navigation guards (using component_lib atoms) | — |
| `arch_tests` | ESLint + import boundary rules | dart analyze + lint rules (Flutter) |

### Additional Node Tooling

The backwards mapper derives these from stories (e.g., a story mentioning "push notification" → `push_notifications`).

| Node ID | Description | Recommended | Alternatives | Edges |
|---|---|---|---|---|
| `push_notifications` | Push/notifications | expo-notifications | Firebase Cloud Messaging, OneSignal | `native_modules → push_notifications` |
| `deep_linking` | Deep links / universal links | Expo Linking + React Navigation deep links | Firebase Dynamic Links, Branch.io | `navigation_setup → deep_linking` |
| `offline_sync` | Offline support | WatermelonDB | MMKV + custom sync, Realm | `state_management + api_client → offline_sync` |
| `mobile_tests` | E2E / integration tests | Detox | Maestro, Appium | feature screens → `mobile_tests` |
| `app_store_setup` | Release / store pipeline | EAS Build + EAS Submit | Fastlane, Codemagic | feature screens → `app_store_setup` |
| `analytics_integration` | Analytics/tracking | PostHog React Native | Mixpanel, Firebase Analytics | feature screens → `analytics_integration` |
| `accessibility_audit` | Accessibility compliance | react-native-a11y + manual audit | Accessibility Inspector, axe DevTools | feature screens → `accessibility_audit` |
