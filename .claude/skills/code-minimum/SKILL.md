---
name: code-minimum
description: Minimizes codebase size and cognitive overhead by maximizing native framework capabilities, installed dependencies, external popular libraries, and existing codebase utilities before writing custom code. Use when auditing, refactoring, or writing code to ensure the absolute minimum lines of code (LOC), eliminating boilerplate, and avoiding reinventing wheels across frontend and backend.
---

# Code Minimum (Library-First & Reuse Engineering)

Every line of custom code is a liability: a maintenance burden, a potential bug surface, and permanent cognitive overhead. The best code is the code never written.

The core directive: **Write the absolute minimum code.** Stop at the first tier that holds: eliminate speculative needs, reuse existing clean codebase assets, leverage framework built-ins, use installed dependencies, evaluate external libraries for complex domains, and only write custom code as an uncompromising last resort.

---

## The 6-Tier Decision Ladder

Evaluate against the hierarchy in strict descending order. Stop at the first tier that holds:

```
┌────────────────────────────────────────────────────────┐
│ Tier 0: Do Not Write It (YAGNI & Architecture)         │
│ (Eliminate speculative needs, dev proxy, or defaults)  │
└───────────────────────┬────────────────────────────────┘
                        │ if not eliminated
┌───────────────────────▼────────────────────────────────┐
│ Tier 1: Existing Codebase Reuse (Subject to Smell Gate)│
│ (Reuse project helpers/hooks; disqualify anti-patterns)│
└───────────────────────┬────────────────────────────────┘
                        │ if not in codebase or disqualified
┌───────────────────────▼────────────────────────────────┐
│ Tier 2: Native Framework Capabilities & Built-in DSLs  │
│ (Declarative annotations, fluent DSLs, platform APIs)  │
└───────────────────────┬────────────────────────────────┘
                        │ if not natively built-in
┌───────────────────────▼────────────────────────────────┐
│ Tier 3: Installed Library APIs & Conventions           │
│ (Leverage capabilities of already installed packages)  │
└───────────────────────┬────────────────────────────────┘
                        │ if no installed dependency satisfies
┌───────────────────────▼────────────────────────────────┐
│ Tier 4: External Library Research & Comparison         │
│ (High complexity only; compare trade-offs; > ~30 LOC)  │
└───────────────────────┬────────────────────────────────┘
                        │ only as an absolute last resort
┌───────────────────────▼────────────────────────────────┐
│ Tier 5: Custom Code (Strict Last Resort)               │
│ (Shortest working diff; no unrequested abstractions)   │
└────────────────────────────────────────────────────────┘
```

### 1. Tier 0: Do Not Write It (YAGNI & Architectural Elimination)

- Question whether the task or abstraction needs to exist at all. Speculative need = skip it.
- Eliminate code at the architectural boundary (reverse proxy routing, platform defaults, infrastructure conventions).

### 2. Tier 1: Existing Codebase Reuse (The Smell Disqualification Gate)

- Search the repository (`grep_search`, `find_by_name`) for existing helpers, custom hooks, services, or domain types.
- **The Smell Disqualification Gate**: Evaluate matches against the Smell Checklist. If candidate code is a reinvented wheel, it is **disqualified from reuse** and marked for replacement by Tier 2 or 3. Only clean, domain-specific utilities qualify for reuse.

### 3. Tier 2: Native Framework Built-ins & Platform Features

- Use declarative framework mechanisms, annotations, fluent DSLs, and native platform/browser primitives before reaching for custom code or libraries.

### 4. Tier 3: Installed Library Capabilities & Conventions

- Check documentation of dependencies already in `package.json` or `pom.xml`. Leverage built-in flags, defaults, and existing library conventions.

### 5. Tier 4: External Library Research & Comparison (Complexity-Gated)

- Search the internet for established, popular third-party libraries when installed dependencies cannot fulfill the goal.
- **Complexity Threshold**: Never add an external dependency for what **< ~30 lines of clean code** can do. Only introduce external libraries for high domain complexity, non-trivial edge cases, or security sensitivity.
- **Comparative Analysis**: If multiple candidate libraries exist, provide an explicit trade-off comparison (ergonomics, bundle size, maintenance health, license) and recommend one with rationale.

### 6. Tier 5: Custom Code (Strict Last Resort)

- Only write custom code when Tiers 0 through 4 are completely exhausted.
- Write the shortest working diff. Can it be one line? Make it one line.

---

## Senior Engineering Rules

- **No Unrequested Abstractions**: No interface with one implementation. No factory for one product. No abstract classes for single use cases. No config keys for constants.
- **No Boilerplate / Speculative Scaffolding**: No scaffolding "for later".
- **Deletion Over Addition**: Boring over clever.
- **Shortest Working Diff Wins**: Fewest files possible.
- **Bug Fix = Root Cause, Not Symptom**: Grep all callers before editing. Fix at the highest shared convergence point rather than patching caller symptoms.

---

## Coding Discipline Rules

These rules govern how custom code (Tier 5) and any refactored code is written. They are evaluated after the tier decision is made.

### Modularity — Extract Only When Justified

A function extraction must earn its existence. Extract a function **only when**:

- It is called from **≥2 distinct call sites**, OR
- It encapsulates a **non-trivial, independently testable unit of logic**.

Never extract speculatively for anticipated future reuse. Speculative extraction is unrequested abstraction.

### Naming Gate — If You Can't Name It, Don't Extract It

Naming is a prerequisite for any extraction or externalisation:

- **Constants**: Name *why* the value exists, not *what* it is. `MAX_RETRY_ATTEMPTS = 3` ✅ — `THREE = 3` ❌.
- **Functions**: Name *what it produces or does*, not *how*. `filterAdmittedPatients()` ✅ — `processLoop()` ❌.
- If you cannot name it clearly, the abstraction boundary is wrong — collapse it back inline.

### No Hard-Coding — The 4-Axis Rule

| Value type | Where it lives |
| --- | --- |
| **Business rule / domain invariant** (e.g., max ward capacity) | Named constant in domain layer — not an env var, it doesn't change per environment |
| **Operational parameter** (e.g., retry count, timeout, batch size) | Externalised to config (`application.yml` / `.env`) — ops must tune without a redeploy |
| **Infrastructure coordinate** (e.g., URL, port, queue name) | Always env/config — never in code |
| **Pure technical literal with no business meaning** (e.g., `0`, `""` in obvious context) | Inline is fine |

**Closed finite sets** (roles, statuses, ward types, event types) → **enum**, not a string constant. Enums give exhaustiveness checking, IDE navigation, and refactor safety.

**Open or externally-driven sets** (values arriving from a DB or external config at runtime) → named constant with a validation layer, not an enum.

### Single Responsibility — The "And/Or" Litmus Test

A function should have exactly one reason to change. Apply the litmus test:

- Can you describe what the function does **without using "and" or "or"**? If not, split it.
- Side effects must be **explicit in the name or signature**. A function that fetches *and* transforms *and* logs is three functions pretending to be one.

### Prefer Pure Functions — Isolate Side Effects

- **Prefer pure functions** (same input → same output, no side effects) for all transformation, calculation, and validation logic.
- **Isolate side effects** (I/O, DB calls, logging, API calls) to the outermost boundary layer — controllers, repositories, event handlers.
- Purity test: *"Can I call this function in a unit test with no mocks?"* Mocks are acceptable but their count is a smell signal — **>2 mocks in a single unit test means the function is likely doing too much**.

### Guard Clauses & DSA Efficiency

**Control flow:**

- Validate preconditions with **guard clauses at the top** of every function. Return or throw early — never bury the happy path inside nested branches.
- **Max nesting depth of 2** as a soft limit. At 3+ levels, extract a function or apply an early return.

**Data access patterns:**

- Use the right data structure for the access pattern: `Map` for O(1) lookup, `Set` for membership checks — never iterate a `List` repeatedly for what a `Map` can do in one pass.
- Never iterate a collection twice when one pass suffices (prefer stream/reduce over loop-then-map).
- **Short-circuit first**: place the cheapest or most-likely-to-fail condition first in compound predicates.
- A nested loop over the same collection is always a smell — suspect O(n²) and redesign.

### Dependency Injection Over Direct Instantiation

Prefer injected dependencies over direct instantiation for infrastructure concerns. This keeps functions testable, mocks minimal, and supports reuse across call sites.

```java
// ❌ Hard dependency — untestable, tightly coupled
public class AdmissionService {
    private final EmailClient emailClient = new EmailClient("smtp.hospital.com");
}

// ✅ Injected — mockable, configurable, reusable
public class AdmissionService {
    private final EmailClient emailClient;
    public AdmissionService(EmailClient emailClient) {
        this.emailClient = emailClient;
    }
}
```

`new` is acceptable only for **pure value objects and DTOs** (`new AdmissionRequest(...)`, `new PageRequest(...)`).

### Error Handling — Leverage the Full-Stack Framework Contract

Use the framework's error handling mechanisms end-to-end as a coherent full-stack contract:

- **Backend**: Use `@ControllerAdvice` (Spring) to produce a **consistent error response shape** from a single place. Domain exceptions propagate up — never catch-and-swallow.
- **Frontend**: Consume that shape via a **single HTTP interceptor or error boundary**. No ad-hoc `try/catch` scattered across components.
- The smell is **abandoning the framework's error model** at any layer to invent a custom one — this forces every consumer to re-implement the same handling logic.

### Documentation — Comments Explain *Why*, Not *What*

- **Self-documenting code first**: if a comment is needed to explain *what* the code does, fix the naming or structure — not by adding a comment.
- **Comments explain *why***: business rules, non-obvious constraints, regulatory requirements, performance trade-offs. These are mandatory where applicable.
- **TSDoc on public-facing frontend code**: public React components, custom hooks, and shared utility functions.
- **Javadoc on public-facing backend code**: public Spring services, controllers, and domain types. Private helpers do not require Javadoc — naming suffices.
- **TODOs must reference an issue tracker ID** or be removed. `// TODO: fix this` is permanent cognitive debt. `// TODO(#142): remove after migration` is acceptable.

---

## Smell Checklist (Disqualification & Elimination)

| Smell (What to Eliminate / Disqualify) | Preferred Replacement | Tier / Source |
| --- | --- | --- |
| Hand-rolled HTTP, cookie, or CSRF handling | Client defaults / framework filter | Tier 2 / 3 |
| Manual cache, polling, or state synchronization | Query/data-fetching library | Tier 3 |
| Custom UI primitives (modals, dropdowns, tables) | Component library primitives | Tier 3 |
| Hand-written validation or parsing loops | Declarative validation / serializer annotations | Tier 2 / 3 |
| Single-implementation interfaces (`FooService`/`FooServiceImpl`) | Concrete service class directly | Tier 5 (Rule) |
| Redundant CORS / proxy configuration | Same-origin dev/ingress proxy | Tier 0 |
| Complex domain logic (dates, crypto, CSV/PDF) | Evaluated external library | Tier 4 |
| Duplicate helpers matching existing codebase utils | Existing clean project helper | Tier 1 |
| Magic number or magic string literals | Named constant or enum | Coding Discipline |
| Function extracted with only one call site | Inline it (YAGNI) | Coding Discipline |
| Function name requiring "and/or" to describe | Split into single-responsibility functions | Coding Discipline |
| Null return from service/domain layer | Domain exception or `Optional` via framework convention | Coding Discipline |
| >2 mocks in a single unit test | Isolate side effects; split the function | Coding Discipline |
| Nested loops over the same collection | Redesign with appropriate data structure (Map/Set) | Coding Discipline |
| `new InfrastructureDep()` inside a service | Inject via constructor / framework DI | Coding Discipline |
| `// TODO` without an issue tracker reference | Link to issue or delete | Coding Discipline |
| Custom error handling per-layer, ad-hoc catch blocks | `@ControllerAdvice` + single frontend error boundary | Tier 2 / Coding Discipline |

---

## Workflow

1. **Eliminate (Tier 0)**: Verify necessity (YAGNI). Eliminate via architecture.
2. **Reuse Codebase (Tier 1)**: Search repo for existing helpers. Disqualify if matching the smell list.
3. **Use Framework Built-ins (Tier 2)**: Apply declarative annotations, DSLs, and platform primitives.
4. **Use Installed Libraries (Tier 3)**: Check installed packages for native features.
5. **Evaluate External Libraries (Tier 4)**: For high domain complexity (> ~30 LOC), research and compare external libraries.
6. **Write Minimal Code (Tier 5)**: Shortest working diff. Obey anti-abstraction rules. For bug fixes, patch at the root cause.
7. **Apply Coding Discipline**: Before committing any code — verify naming gate, no hard-coding, SRP litmus test, guard clauses, purity preference, and documentation rules.
8. **Verify**: Run tests and builds to confirm zero regression.
