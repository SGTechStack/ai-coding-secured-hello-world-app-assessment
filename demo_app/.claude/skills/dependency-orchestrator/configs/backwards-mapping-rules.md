# Backwards Mapping Rules

## Story Identity Rule

Every input user story maps to exactly one output story with the **same ID and title**. If the input story is `AFW-972: "As a user manager, I can enable multiple user accounts..."`, the output story must be `AFW-972` with that same title. Never merge multiple input stories into one. Never split one input story into multiple. Never rename or rephrase story titles. Never fabricate new feature stories that weren't in the input. The input stories ARE the output stories — the only thing added is their `owned_nodes` and `category`.

## For Each User Story

1. **Identify the end-state** — what is the final value-delivery outcome described by this story? (e.g., "patient receives triage assessment", "user completes checkout")
2. **Walk backward** — trace the strict chain of functional prerequisites from end-state to entry point:
   - `[End Goal]` -> depends on -> `[Prerequisite]` -> depends on -> `[Origin]`
   - For each link, state *why* the dependency is structurally required (not just conventionally expected)
   - Stop when you reach a foundation node (already owned by an infrastructure story) or a node already derived by a previous story in the same batch (within-batch dedup). Cross-batch dedup is handled by `scripts/merge-batch.py` during the merge phase
3. **Map cross-cutting concerns** — these often reveal edges to shared infrastructure nodes (auth, middleware, logging) that pure feature-level analysis would miss:
   - **RBAC**: Which identities/roles need access? What level (write, read, update)?
   - **Session & State**: Session rules (auto-timeouts, concurrency locks, state transitions) required for production safety?
   - **Compliance & Audit**: Immutable telemetry, state mutations, or user actions that must be logged?
4. **Emit nodes and edges** — each new step in the chain becomes a dev task node owned by this story. Each dependency link becomes an edge. Cross-cutting concerns add edges to foundation nodes.

## Key Principles

- **First Principles**: Do not inject unnecessary framework assumptions. Derive dependencies from structural necessity.
- **Separation of Concerns**: Business features must never be decoupled from their corresponding security/compliance gates — these gates become edges in the DAG.
- **Minimalism**: The backwards chain reveals the minimum viable dependency path. Do not add speculative edges.

## Node Ownership

Each node is owned by the story that first derives it. When a later story's backwards chain reaches a node already derived by an earlier story, it reuses that node (adds an edge to it) rather than creating a duplicate — but the node's ownership stays with the original story.

**Multiple stories owning the same node is normal and expected.** This happens when stories across different releases touch the same domain entities (e.g., story 1.1 and 7.1 both produce `be_asset_req_*` nodes). The DAG processor treats this as valid — it simply tracks the first owner for scheduling purposes.

**HARD RULE: Every story MUST own at least one node.** An `owned_nodes: []` output is always invalid and will be rejected by verification. Even if a story seems to reuse all existing nodes, it still requires at minimum its own unique node (e.g., a frontend page `fe_{domain}_page` or a backend service `be_{domain}_service`). Stories with generic titles like "Reports", "Dashboard", or "Manage X" are distinct features — derive their full backward-mapping chain and assign dedicated nodes.

## Node Format

```json
{ "id": "<snake_case_id>", "name": "<Task Name>", "duration_days": <N>, "tooling": ["<framework or library>", ...], "appfw_standard": "<sub-standard path, array of paths, or omit>" }
```

**`tooling` field (required):** An array of the specific frameworks, libraries, or tools needed to implement this node. For foundation nodes, use the **Recommended** tooling from the foundation template's Project Configuration → Core Foundation Tooling table (e.g., `["shadcn/ui"]` not `"pre-built component library"`). Only use an alternative if the user explicitly overrides the recommendation in Step 1. For feature nodes, list the primary frameworks the task will use (e.g., `["TanStack Table", "React Hook Form"]`). For feature module nodes, use the **Recommended** tooling from the Project Configuration → Feature Module Tooling table. Only include significant frameworks — omit the base language and ubiquitous tools (e.g., don't list `"TypeScript"` or `"npm"`).

**Duration assignment:** Look up the size from `duration-defaults.md` using the node's tooling:
1. **Foundation nodes** — find the node's `tooling` value in the Foundation Node Sizes table (matched by Node ID + Tooling columns) → get Size → convert to `duration_days` using the Size → Duration table at the top of `duration-defaults.md`.
2. **Feature module nodes** — find the node's `tooling` value in the Frontend Feature Modules or Backend Feature Modules table (matched by Module + Tooling or Module + Scope Tier) → get Size → convert to `duration_days`.
3. **Feature nodes derived by backwards mapping** — match the task description against the General Task Sizes table → get Size → convert to `duration_days`.

**Omit `status`** — `scripts/merge-batch.py` defaults all new nodes to `"pending"`. The `reconcile-stories` skill sets nodes to `"completed"` when their owning story's GitHub issue is closed.

**Omit `category` when it can be inferred from the node ID prefix:** `fe_` → frontend, `be_` → backend, `mob_` → mobile. `scripts/merge-batch.py` infers these automatically. **Only include `category` explicitly** for nodes whose ID doesn't start with one of these prefixes (e.g., `"category": "infrastructure"` for `project_setup`, or `"category": "design"` / `"category": "testing"` where applicable). If omitted and no prefix matches, defaults to `"infrastructure"`.

Node ownership is determined by the story's `owned_nodes` array, not by a field on the node. The processor derives the node→story mapping from `owned_nodes`.

## Edge Format

```json
["<prerequisite_node_id>", "<dependent_node_id>"]
```

The first element is the prerequisite, the second is the dependent. The prerequisite must complete before the dependent can start. For example, `["auth_service", "cart_finalization"]` means auth_service must be built before cart_finalization. This matches the `→` arrow direction used throughout: `auth_service → cart_finalization` means `auth_service` is the prerequisite.

## Feature Group Format

Feature groups are lightweight labels used to group related stories in reports and Gantt charts. Each group has an `id` and a `name`. Only emit groups that stories in this batch belong to. Do NOT put story-level fields (`owned_nodes`, `category`, `verifiable`) on feature groups — those belong on `user_stories` only.

```json
{ "id": "auth", "name": "Authentication & Authorization" }
{ "id": "reports", "name": "Reports" }
```

If the input stories have a `group` or `feature_group` field, use that as the group `id` and derive a human-readable `name`. If no groups are specified, emit an empty `feature_groups` array.

## Story Output Format

Stories no longer carry `appfw_standards` — standards are assigned at the node level via the `appfw_standard` field. The story-level `appfw_standards` is derived automatically as the union of its owned nodes' `appfw_standard` values.

```json
{ "id": "AFW-101", "title": "As a medic, I can triage a patient", "owned_nodes": ["be_triage_service", "be_triage_routes", "fe_triage_page"], "category": "feature", "duration_assumptions": "Single-table schema, Spring Boot CRUD service + routes, React Hook Form page" }
{ "id": "INFRA-BE-02", "title": "Users can authenticate end-to-end", "owned_nodes": ["be_auth_service", "be_middleware"], "category": "infrastructure", "duration_assumptions": "JWT session auth via Spring Security, @ControllerAdvice error middleware" }
```

**`duration_assumptions` field (required):** After finishing backwards mapping for a story, derive this from the `tooling` arrays of the story's owned nodes combined with the complexity assumptions. Summarise the key tooling and complexity drivers (e.g., `"shadcn/ui + Tailwind CSS pre-built components, single-table CRUD"` for infra, or `"TanStack Table for data grid, React Hook Form + Zod validation"` for features). This is displayed on reports so stakeholders can see and challenge the assumptions.

## Worked Example: E-Commerce Checkout

**User story:** "As a customer, I can complete checkout and pay for my order"

**Right-to-Left Chain:**
- **[Order Fulfillment Initiation]** -> Depends on -> **[Payment Capture Confirmation]**
  - Reasoning: Cannot fulfill an order until financial liability is successfully transferred.
- **[Payment Capture Confirmation]** -> Depends on -> **[Inventory Reservation]**
  - Reasoning: Cannot capture payment for goods not secured; risks overselling.
- **[Inventory Reservation]** -> Depends on -> **[Shipping/Tax Calculation]**
  - Reasoning: Inventory must be reserved against a specific shipping destination to ensure regional restrictions and costs are finalized.
- **[Shipping/Tax Calculation]** -> Depends on -> **[Cart Finalization]**
  - Reasoning: Cannot calculate taxes or shipping without knowing the exact line items and destination.
- **[Cart Finalization]** -> Depends on -> `auth_service` *(foundation node — stop, already owned by INFRA-02)*

**Cross-cutting boundaries derived:**
- RBAC: Customer needs auth → edge to `auth_service`. Payment gateway service principal → edge to `auth_service`.
- Session & State: Inventory Lock TTL → edge to `exception_mapping` (error handling for expired locks). Idempotent payment capture → edge to `exception_mapping`.
- Audit: State transition logging → edge to `logging`.

**Nodes derived (all owned by this story):** `cart_service`, `shipping_service`, `inventory_service`, `payment_integration`, `order_service`

*Note how suffixes are deterministic: business logic steps get `_service`, the external payment gateway call gets `_integration`. Any subagent processing a checkout story would derive the same suffixes — the domain (`cart`, `shipping`, `inventory`, `payment`, `order`) comes from the story, the suffix comes from the layer.*

**Edges from the backwards chain:**
```
cart_service → shipping_service → inventory_service → payment_integration → order_service
```

**Edges to foundation nodes (cross-cutting):**
```
auth_service → cart_service              (RBAC)
auth_service → payment_integration       (RBAC)
exception_mapping → inventory_service    (Session: TTL error handling)
exception_mapping → payment_integration  (Session: idempotency error handling)
logging → order_service                  (Audit: state transition logging)
```

## Node Conventions

**Node ID naming — `{domain}_{layer_suffix}` convention:**

Node IDs follow the pattern `{domain}_{layer_suffix}` where `{domain}` is the business concept in snake_case and `{layer_suffix}` is from the fixed set below. For fullstack projects, prepend the stack prefix: `{stack_prefix}_{domain}_{layer_suffix}` (e.g., `be_triage_service`, `fe_triage_page`). The `fe_`/`be_`/`mob_` prefixes distinguish stack layers — they are namespaces, not team boundaries.

This convention ensures independent subagents converge on the same node IDs for the same concepts. The domain comes from the story's business context; the suffix is deterministic based on where in the backwards chain the node sits.

**Backend layer suffixes:**

| Suffix | Layer | When to use |
|---|---|---|
| `_db_schema` | Database schema / migration | Story needs a new table or schema change |
| `_data_access` | Repository / DAO | Story needs data read/write operations |
| `_service` | Business logic | Story has domain logic, validation, orchestration |
| `_routes` | API endpoints / controller | Story exposes HTTP endpoints |
| `_job` | Background / scheduled task | Story has async processing, cron, queue consumer |
| `_integration` | External service call | Story calls an external API or third-party service |

**Frontend layer suffixes:**

| Suffix | Layer | When to use |
|---|---|---|
| `_page` | Page-level component (route) | Story is a full page or routable view |
| `_form` | Form component | Story involves structured user input |
| `_list` | List / table component | Story displays a collection of items |
| `_modal` | Dialog / overlay | Story has modal interactions |
| `_widget` | Embedded component | Story is a reusable UI block within a page |

**Mobile layer suffixes:**

| Suffix | Layer | When to use |
|---|---|---|
| `_screen` | Screen component | Story is a full screen |
| `_form` | Form component | Story involves structured user input |
| `_list` | List / table component | Story displays a collection of items |
| `_modal` | Dialog / overlay | Story has modal interactions |

**Shared suffixes (any stack):**

| Suffix | Layer | When to use |
|---|---|---|
| `_config` | Configuration | Story needs specific configuration setup |
| `_middleware` | Request processing | Story needs request/response interception |

**Suffix selection rules:**

1. **Pick the suffix matching the node's primary responsibility.** A node that reads from the DB and applies business rules is `_service` (its primary job is logic), not `_data_access`.
2. **One suffix per node.** If a story needs both a service and its routes, that's two nodes: `checkout_service` and `checkout_routes`.
3. **Domain names should be specific enough to avoid collision but not over-qualified.** Use `triage_service` (not `patient_triage_assessment_service`). If two distinct concepts share a domain name, qualify the more specific one: `cart_service` (general cart logic) vs `cart_checkout_service` (checkout-specific cart operations).
4. **Foundation nodes keep their template IDs.** Do not rename `auth_service`, `logging`, `exception_mapping`, etc. — these are fixed anchor points.
5. **Standards-derived prerequisite nodes** are named after the standard they implement (e.g., a node implementing `Shared-Auth` is named `shared_auth`). These are created automatically when a standard has hard dependencies — see `configs/standards.md`.

**`appfw_standard` field (node-level):** each dev task node carries an optional `appfw_standard` field naming the sub-standard path(s) that govern how to build that node. Value can be a single string (e.g., `"Appfw-Logging-Standards"`) or an array of strings when multiple sub-standards apply to the same node (e.g., `["Appfw-User-Standards/User_SSO", "Appfw-User-Standards/Shared_Recipes"]`). Omit the field if no standard applies. Consult `configs/standards.md` for the full node-to-standard mapping rules — it defines which standards apply to which nodes (foundation nodes, feature module nodes, and concept-based matching). The story-level `appfw_standards` array is derived automatically as the deduplicated union of all owned nodes' `appfw_standard` values.

**Foundation connection points:** Each foundation template defines its connection points (where backwards-mapped nodes attach). Consult the loaded template's "Foundation Connection Points" section — do not hardcode the list here.

**Standards-driven dependencies:** After deriving a node, consult `configs/standards.md` for the node-to-standard mapping. If a standard is matched and that standard has hard dependencies, create new prerequisite nodes named after each prerequisite standard and add them to the chain. Each prerequisite node gets its own `appfw_standard` field. The backwards-mapped node keeps its natural `{domain}_{layer_suffix}` name — it does not get renamed. See the "How Standards Drive the DAG" section in `configs/standards.md` for the full flow and worked example.

**Right-sizing shared infrastructure nodes:** For feature module nodes that have **Scope Tiers** in the foundation template, pick the lowest tier that satisfies all consumers. Consult the Scope Tiers table in the loaded backend foundation template.

**Optional nodes:** Consult the Optional Additions table in each loaded foundation template. Only add if confirmed by the user in Step 1 question 6.

**Granularity:** The backwards mapping determines node granularity — some stories derive a single node, others derive several. Do not prescribe a fixed decomposition pattern; let the chain reveal what is structurally required.

## Worked Example: Healthcare Patient Triage (Fullstack)

**User story:** "As a medic, I can triage a patient and record their initial assessment"

**Right-to-Left Chain:**
- **[Triage Record Persists]** -> Depends on -> **[Triage Form Submission]**
  - Reasoning: Cannot persist a record without structured input from the medic.
- **[Triage Form Submission]** -> Depends on -> **[Triage API Endpoints]**
  - Reasoning: Frontend form needs backend routes to accept and validate triage data.
- **[Triage API Endpoints]** -> Depends on -> **[Triage Business Logic]**
  - Reasoning: Routes delegate to a service layer for validation, scoring, and persistence orchestration.
- **[Triage Business Logic]** -> Depends on -> **[Triage Data Access]**
  - Reasoning: Service layer reads/writes triage records through a repository.
- **[Triage Data Access]** -> Depends on -> **[Triage DB Schema]**
  - Reasoning: Repository needs the table/schema to exist before querying.
- **[Triage DB Schema]** -> Depends on -> `db_setup` *(foundation node — stop)*

**Cross-cutting boundaries derived:**
- RBAC: Only medics can create triage records → edge to `auth_service`
- Session: Triage form should not timeout mid-entry → edge to `be_middleware`
- Audit: Triage decisions are clinical records → edge to `logging`

**Nodes derived (all owned by this story):** `be_triage_db_schema`, `be_triage_data_access`, `be_triage_service`, `be_triage_routes`, `fe_triage_page`

**Edges from the backwards chain:**
```
be_triage_db_schema → be_triage_data_access → be_triage_service → be_triage_routes → fe_triage_page
```

**Edges to foundation nodes (cross-cutting):**
```
db_setup → be_triage_db_schema            (schema depends on DB setup)
auth_service → be_triage_routes           (RBAC)
be_middleware → be_triage_routes           (session handling)
logging → be_triage_service               (audit trail)
be_api_route_skeleton → be_triage_routes  (routes extend skeleton)
fe_auth_ui → fe_triage_page               (page behind auth gate)
```

## Worked Example: SaaS Admin User Management

**User story:** "As an admin, I can invite new users, assign roles, and deactivate accounts"

**Right-to-Left Chain:**
- **[Account Deactivation]** -> Depends on -> **[Role Assignment]**
  - Reasoning: Deactivation must revoke role-bound access; role model must exist first.
- **[Role Assignment]** -> Depends on -> **[User Invitation & Provisioning]**
  - Reasoning: Cannot assign roles to users who haven't been provisioned.
- **[User Invitation & Provisioning]** -> Depends on -> **[User Management API]**
  - Reasoning: Invitation flow requires API endpoints for creating user records and sending invites.
- **[User Management API]** -> Depends on -> **[User Management Service]**
  - Reasoning: API delegates to service for invitation logic, email dispatch, token generation.
- **[User Management Service]** -> Depends on -> **[User Data Access]**
  - Reasoning: Service reads/writes user records and role assignments.
- **[User Data Access]** -> Depends on -> **[User DB Schema]**
  - Reasoning: Repository needs user and role tables.
- **[User DB Schema]** -> Depends on -> `db_setup` *(foundation node — stop)*

**Cross-cutting boundaries derived:**
- RBAC: Only admins can manage users → edge to `auth_service`
- Audit: User creation, role changes, deactivation are security-sensitive mutations → edge to `logging`
- Notification: Invitation emails → edge to `notification_service` (if it exists) or derive a `notification_integration` node

**Nodes derived (all owned by this story):** `be_user_mgmt_db_schema`, `be_user_mgmt_data_access`, `be_user_mgmt_service`, `be_user_mgmt_routes`, `fe_user_mgmt_page`

**Edges from the backwards chain:**
```
be_user_mgmt_db_schema → be_user_mgmt_data_access → be_user_mgmt_service → be_user_mgmt_routes → fe_user_mgmt_page
```

**Edges to foundation nodes:**
```
db_setup → be_user_mgmt_db_schema        (schema depends on DB setup)
auth_service → be_user_mgmt_routes       (RBAC: admin-only)
logging → be_user_mgmt_service           (audit: security mutations)
be_api_route_skeleton → be_user_mgmt_routes
fe_auth_ui → fe_user_mgmt_page
```

## Worked Example: Logistics Shipment Tracking

**User story:** "As a warehouse operator, I can scan a package, assign it to a route, and track its delivery status in real time"

**Right-to-Left Chain:**
- **[Real-Time Delivery Tracking]** -> Depends on -> **[Route Assignment]**
  - Reasoning: Cannot track delivery progress without a route to track against.
- **[Route Assignment]** -> Depends on -> **[Package Scanning & Registration]**
  - Reasoning: Cannot assign a route to an unregistered package.
- **[Package Scanning & Registration]** -> Depends on -> **[Shipment API]**
  - Reasoning: Scanner input needs backend endpoints to register packages.
- **[Shipment API]** -> Depends on -> **[Shipment Service]**
  - Reasoning: API delegates to service for validation, route optimization, status transitions.
- **[Shipment Service]** -> Depends on -> **[Shipment Data Access]**
  - Reasoning: Service reads/writes shipment records, route assignments, status updates.
- **[Shipment Data Access]** -> Depends on -> **[Shipment DB Schema]**
  - Reasoning: Repository needs shipment, route, and tracking tables.
- **[Shipment DB Schema]** -> Depends on -> `db_setup` *(foundation node — stop)*

**Cross-cutting boundaries derived:**
- RBAC: Warehouse operators can scan/assign; dispatchers can reassign; customers can view status → edge to `auth_service`
- Session: Scanner sessions with idle timeout → edge to `be_middleware`
- Audit: Package state transitions must be immutably logged for chain-of-custody → edge to `logging`

**Nodes derived (all owned by this story):** `be_shipment_db_schema`, `be_shipment_data_access`, `be_shipment_service`, `be_shipment_routes`, `fe_shipment_page`

**Edges from the backwards chain:**
```
be_shipment_db_schema → be_shipment_data_access → be_shipment_service → be_shipment_routes → fe_shipment_page
```

**Edges to foundation nodes:**
```
db_setup → be_shipment_db_schema          (schema depends on DB setup)
auth_service → be_shipment_routes         (RBAC: role-based access)
be_middleware → be_shipment_routes        (session handling)
logging → be_shipment_service             (audit: chain-of-custody)
be_api_route_skeleton → be_shipment_routes
fe_auth_ui → fe_shipment_page
```

## Domain Vocabulary

Use these canonical domain names to ensure independent subagents converge on the same node IDs. When the business domain matches a concept below, use the listed term — do not invent synonyms.

| Canonical term | Do NOT use | Domain |
|---|---|---|
| `user` | account, member, person, profile | Identity & access |
| `auth` | login, authentication, session | Authentication |
| `role` | permission, privilege, access_level | Authorization |
| `notification` | alert, message, email, sms | Messaging |
| `payment` | billing, charge, transaction | Payments |
| `invoice` | bill, receipt, statement | Billing documents |
| `order` | purchase, booking, reservation | Orders |
| `cart` | basket, bag | Shopping cart |
| `inventory` | stock, catalog, supply | Inventory |
| `shipment` | delivery, dispatch, shipping | Logistics |
| `product` | item, good, sku | Product catalog |
| `customer` | buyer, client, consumer | Customer identity |
| `report` | analytics, dashboard, stats | Reporting |
| `audit` | log, trail, history | Audit logging |
| `config` | setting, preference, option | Configuration |
| `schedule` | calendar, booking, appointment | Scheduling |
| `document` | file, attachment, upload | Document management |
| `workflow` | process, pipeline, flow | Process orchestration |
| `search` | query, lookup, find | Search functionality |
| `comment` | note, remark, feedback | User comments |
| `triage` | assessment, evaluation, screening | Clinical assessment |
| `vitals` | measurements, readings, metrics | Health measurements |
| `patient` | subject, individual, case | Healthcare identity |

**Usage rule:** When a story mentions "stock levels," use `inventory` as the domain prefix, not `stock`. When it mentions "sending an alert," use `notification`, not `alert`. The suffix rules (from the Node Conventions section) still apply — combine the canonical domain with the appropriate layer suffix: `inventory_service`, `notification_integration`, etc.
