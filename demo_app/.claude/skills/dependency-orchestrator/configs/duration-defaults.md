# Duration Defaults (AI-assisted development)

## Size → Duration

Editable: adjust the **Duration** column to change how size tiers map to days.

| Size | Duration |
|------|----------|
| Tiny | 0.25 days |
| Small | 0.5–1 day |
| Medium | 1–2 days |
| Large | 3–4 days |
| Epic | 5–7 days |

*Assumes AI coding tools. Adjust up if not using AI assistance.*

## Testing Overhead

Editable: adjust the **Default** value.

| Setting | Default |
|---------|---------|
| testing_overhead | 0.3 |

A flat multiplier applied to every node's duration to account for unit/integration testing. `0.3` means each task's duration is scaled by 1.3×. Set to `0` to disable. Override per-run via `--testing-overhead` on `dag-processor.py`.

## Foundation Node Sizes

Each foundation node can appear multiple times — one row per tooling variant. The processor matches the project's tooling choice to the right row to get the size.

Editable: change the **Size** column, or add new rows for additional tooling variants.

### Web Frontend Foundations

| Node ID | Tooling | Size |
|---|---|---|
| `project_setup` | Vite scaffold | Tiny |
| `project_setup` | Next.js scaffold | Tiny |
| `project_setup` | Nuxt scaffold | Tiny |
| `project_setup` | SvelteKit scaffold | Tiny |
| `design_tokens` | Tailwind CSS config | Small |
| `design_tokens` | CSS variables | Small |
| `design_tokens` | Theme file | Small |
| `design_tokens` | Custom token system + theme provider | Medium |
| `component_lib` | shadcn/ui | Small |
| `component_lib` | Vuetify | Small |
| `component_lib` | Skeleton | Small |
| `component_lib` | Other pre-built library | Small |
| `component_lib` | Custom design system from scratch | Epic |
| `routing_setup` | React Router | Tiny |
| `routing_setup` | Vue Router | Tiny |
| `routing_setup` | SvelteKit routes | Tiny |
| `routing_setup` | Custom routing solution | Medium |
| `state_management` | TanStack Query | Tiny |
| `state_management` | SWR | Tiny |
| `state_management` | Pinia | Tiny |
| `state_management` | Redux Toolkit | Small |
| `state_management` | Zustand + manual cache | Small |
| `state_management` | Custom state management from scratch | Medium |
| `api_client` | Axios wrapper with interceptors | Small |
| `api_client` | fetch wrapper with interceptors | Small |
| `api_client` | Custom HTTP client with retry + auth | Medium |
| `auth_ui` | Auth provider + route guards (using pre-built component lib) | Small |
| `auth_ui` | Custom auth UI from scratch | Epic |
| `arch_tests` | ESLint flat config + import boundary rules | Small |
| `arch_tests` | Custom lint rules + architectural test framework | Medium |

### Backend API Foundations

| Node ID | Tooling | Size |
|---|---|---|
| `logging` | logback + MDC filter (Spring Boot) | Small |
| `logging` | pino + middleware (Express / Fastify) | Small |
| `logging` | Winston + middleware | Small |
| `logging` | Custom structured logging framework | Medium |
| `exception_mapping` | @ControllerAdvice (Spring Boot) | Tiny |
| `exception_mapping` | Express error middleware | Tiny |
| `exception_mapping` | NestJS exception filter | Tiny |
| `exception_mapping` | Custom global error handler + response format | Small |
| `openapi_docs` | springdoc-openapi (Spring Boot) | Tiny |
| `openapi_docs` | swagger-jsdoc (Express) | Tiny |
| `openapi_docs` | NestJS Swagger | Tiny |
| `openapi_docs` | Manual OpenAPI spec + docs UI setup | Medium |
| `auth_service` | JWT auth (schema + data access + logic) | Small |
| `auth_service` | Session auth (schema + data access + logic) | Small |
| `auth_service` | Custom auth system from scratch (signup, login, sessions) | Large |
| `arch_tests` | ArchUnit (Spring Boot) | Small |
| `arch_tests` | eslint-plugin-boundaries (Node.js) | Small |
| `arch_tests` | Custom architectural test rules | Medium |

### Mobile App Foundations

| Node ID | Tooling | Size |
|---|---|---|
| `project_setup` | Expo scaffold | Tiny |
| `project_setup` | React Native CLI scaffold | Tiny |
| `project_setup` | Flutter scaffold | Tiny |
| `design_tokens` | Theme config (colors, spacing, typography constants) | Small |
| `design_tokens` | Custom token system + theme provider | Medium |
| `native_modules` | expo-modules | Small |
| `native_modules` | react-native link | Small |
| `native_modules` | native plugins | Small |
| `native_modules` | Custom native bridges from scratch | Large |
| `navigation_setup` | React Navigation | Tiny |
| `navigation_setup` | Expo Router | Tiny |
| `navigation_setup` | GoRouter (Flutter) | Tiny |
| `navigation_setup` | Custom navigation solution | Large |
| `state_management` | TanStack Query | Tiny |
| `state_management` | Riverpod (Flutter) | Tiny |
| `state_management` | Provider (Flutter) | Tiny |
| `state_management` | Custom state management + cache layer | Small |
| `api_client` | Axios wrapper with interceptors | Small |
| `api_client` | Dio (Flutter) | Small |
| `api_client` | fetch wrapper with interceptors | Small |
| `api_client` | Custom HTTP client with retry + auth | Medium |
| `component_lib` | React Native Paper | Small |
| `component_lib` | NativeBase | Small |
| `component_lib` | Custom design system from scratch | Epic |
| `auth_screens` | Auth provider + navigation guards (using pre-built component lib) | Small |
| `auth_screens` | Custom auth screens from scratch | Epic |
| `arch_tests` | ESLint + import boundary rules | Small |
| `arch_tests` | dart analyze + lint rules (Flutter) | Small |
| `arch_tests` | Custom architectural test rules | Medium |

## General Task Sizes

For non-foundation, non-module tasks. Categorised for easier lookup.

Editable: change the **Size** column, or add new rows for additional task types.

| Category | Task | Size |
|---|---|---|
| config | Config file, env variable setup | Tiny |
| config | Linter/formatter setup | Tiny |
| backend | Single API endpoint (no business logic) | Tiny |
| backend | Basic CRUD operations | Small |
| backend | Auth guard or middleware | Small |
| backend | Database migration (single table) | Small |
| backend | Complex business logic module | Large |
| backend | Domain with multiple sub-features | Large |
| frontend | Simple UI component (button, card, input) | Small |
| frontend | Static layout (header, footer, sidebar shell) | Small |
| frontend | Form with validation | Small |
| frontend | Toast/notification component | Small |
| frontend | Modal/dialog component | Small |
| frontend | Responsive nav bar with mobile menu | Medium |
| frontend | State management slice (store, actions, selectors) | Medium |
| frontend | Dashboard layout with multiple widget slots | Large |
| fullstack | Feature with UI + API + tests | Medium |
| fullstack | Real-time data sync (WebSocket/SSE) | Large |
| fullstack | File upload/processing pipeline | Epic |
| fullstack | Cross-cutting concern spanning multiple layers | Epic |
| integration | Integration hook (connect two existing modules) | Small |
| infra | CI/CD pipeline with testing and deployment stages | Epic |

## Backend Feature Modules

Each module can appear multiple times — one row per scope tier.

Editable: change the **Size** column, or add new rows for additional modules/tiers.

| Module | Scope Tier | Description | Size |
|---|---|---|---|
| `mcc_auth` | `request_context` | Sync outbound OAuth2 only | Small |
| `mcc_auth` | `full` | + background client (async/batch) | Small |
| `mcns_core` | `minimal_sms` | SMS only, sync, no retry | Small |
| `mcns_core` | `minimal_email` | Email only, templates, no retry | Small |
| `mcns_core` | `sms_email` | SMS + email, basic retry | Medium |
| `mcns_batch` | `full` | Batch sends, retry queue, DLQ | Medium |
| `mfa_cloud` | `otp_only` | OTP factor only (SMS/email codes) | Medium |
| `mfa_cloud` | `full` | OTP + cloud TOTP with re-provisioning | Large |
| `mfa_standalone` | `pin_only` | PIN factor only | Medium |
| `mfa_standalone` | `totp_only` | TOTP only, admin-reset on loss | Medium |
| `mfa_standalone` | `full` | PIN + TOTP | Large |
| `mfa_standalone_ct` | `full` | MFA + critical transaction gating | Small |
| `mfa_cloud_ct` | `full` | Cloud MFA + critical transaction gating | Small |
| `interface_batch` | `inbound_only` | Single inbound file, no lock | Small |
| `interface_batch` | `outbound_only` | Single outbound file | Small |
| `interface_batch` | `full` | Inbound + outbound + ShedLock | Medium |
| `file_mcc` | `upload_only` | Upload + basic validation (AWS) | Small |
| `file_mcc` | `full` | + S3 clean store + SFS virus scan | Medium |
| `file_standalone` | `upload_only` | Upload + basic validation (local) | Small |
| `file_standalone` | `full` | + storage quota + local promotion | Medium |
| `report_core` | `single_format` | One format, one template | Small |
| `report_core` | `multi_format` | Multiple formats + templates | Medium |
| `report_programmatic` | `full` | Dynamic columns + programmatic design | Medium |

## Frontend Feature Modules

Each module can appear multiple times — one row per tooling variant.

Editable: change the **Size** column, or add new rows for additional modules/tooling variants.

| Module | Tooling | Size |
|---|---|---|
| `fe_data_table` | TanStack Table | Medium |
| `fe_data_table` | AG Grid | Medium |
| `fe_data_table` | Custom from scratch | Large |
| `fe_rich_text_editor` | TipTap | Large |
| `fe_rich_text_editor` | Slate | Large |
| `fe_rich_text_editor` | Lexical | Large |
| `fe_rich_text_editor` | Custom from scratch | Epic |
| `fe_file_upload` | react-dropzone | Small |
| `fe_file_upload` | Uppy | Small |
| `fe_file_upload` | Custom from scratch | Medium |
| `fe_charts` | Recharts | Medium |
| `fe_charts` | Chart.js | Medium |
| `fe_charts` | Victory | Medium |
| `fe_charts` | Custom from scratch | Epic |
| `fe_realtime` | Socket.io client | Large |
| `fe_realtime` | SSE EventSource | Large |
| `fe_realtime` | Custom from scratch | Epic |
| `fe_drag_drop` | dnd-kit | Large |
| `fe_drag_drop` | react-beautiful-dnd | Large |
| `fe_drag_drop` | Custom from scratch | Epic |
| `fe_search` | Autocomplete component + debounced API | Large |
| `fe_search` | Custom from scratch | Epic |
| `fe_map` | Leaflet | Medium |
| `fe_map` | Mapbox GL | Medium |
| `fe_map` | Google Maps | Medium |
| `fe_map` | Custom from scratch | Epic |
| `fe_form_builder` | React Hook Form + dynamic schema renderer | Medium |
| `fe_form_builder` | Custom from scratch | Large |
| `fe_pdf_viewer` | react-pdf | Medium |
| `fe_pdf_viewer` | PDF.js | Medium |
| `fe_pdf_viewer` | Custom from scratch | Epic |
| `fe_mfa_standalone` | React MFA module (PIN/TOTP screens) | Medium |
| `fe_mfa_mcc` | React MFA module (OTP + cloud TOTP screens) | Medium |

## Optional Node Sizes

Each optional node can appear multiple times — one row per tooling variant.

Editable: change the **Size** column, or add new rows for additional nodes/tooling variants.

### Web Frontend Optional

| Node ID | Tooling | Size |
|---|---|---|
| `e2e_tests` | Selenium | Medium |
| `e2e_tests` | Playwright | Medium |
| `e2e_tests` | Cypress | Medium |
| `e2e_tests` | Custom from scratch | Large |
| `accessibility_audit` | axe-core + eslint-plugin-jsx-a11y | Small |
| `accessibility_audit` | pa11y | Small |
| `accessibility_audit` | Lighthouse CI | Small |
| `accessibility_audit` | Custom from scratch | Medium |
| `analytics_integration` | PostHog | Small |
| `analytics_integration` | Mixpanel | Small |
| `analytics_integration` | Google Analytics | Small |
| `analytics_integration` | Custom from scratch | Medium |
| `performance_optimization` | Lighthouse CI + bundlesize | Small |
| `performance_optimization` | web-vitals library | Small |
| `performance_optimization` | Custom from scratch | Medium |

### Backend Optional

| Node ID | Tooling | Size |
|---|---|---|
| `caching_layer` | Redis (Lettuce / ioredis) | Medium |
| `caching_layer` | Memcached | Medium |
| `caching_layer` | Caffeine (in-process) | Small |
| `caching_layer` | Custom from scratch | Large |
| `event_queue` | Apache Kafka | Large |
| `event_queue` | SQS | Medium |
| `event_queue` | RabbitMQ | Medium |
| `event_queue` | Custom from scratch | Epic |
| `monitoring_alerting` | Prometheus + Grafana | Medium |
| `monitoring_alerting` | Datadog | Medium |
| `monitoring_alerting` | New Relic | Medium |
| `monitoring_alerting` | Custom from scratch | Large |

### Mobile Optional

| Node ID | Tooling | Size |
|---|---|---|
| `push_notifications` | expo-notifications | Small |
| `push_notifications` | Firebase Cloud Messaging | Small |
| `push_notifications` | OneSignal | Small |
| `push_notifications` | Custom from scratch | Large |
| `deep_linking` | Expo Linking + React Navigation deep links | Small |
| `deep_linking` | Firebase Dynamic Links | Small |
| `deep_linking` | Branch.io | Small |
| `deep_linking` | Custom from scratch | Medium |
| `offline_sync` | WatermelonDB | Large |
| `offline_sync` | MMKV + custom sync | Large |
| `offline_sync` | Realm | Large |
| `offline_sync` | Custom from scratch | Epic |
| `mobile_tests` | Detox | Medium |
| `mobile_tests` | Maestro | Medium |
| `mobile_tests` | Appium | Medium |
| `mobile_tests` | Custom from scratch | Large |
| `app_store_setup` | EAS Build + EAS Submit | Small |
| `app_store_setup` | Fastlane | Medium |
| `app_store_setup` | Codemagic | Small |
| `app_store_setup` | Custom from scratch | Large |
| `analytics_integration` | PostHog React Native | Small |
| `analytics_integration` | Mixpanel | Small |
| `analytics_integration` | Firebase Analytics | Small |
| `analytics_integration` | Custom from scratch | Medium |
| `accessibility_audit` | react-native-a11y + manual audit | Small |
| `accessibility_audit` | Accessibility Inspector | Small |
| `accessibility_audit` | axe DevTools | Small |
| `accessibility_audit` | Custom from scratch | Medium |
