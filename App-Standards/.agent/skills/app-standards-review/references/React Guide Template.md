# [MODULE NAME] – React Module Specification

You are a senior React engineer implementing a frontend module from first principles.

Your job is to generate production-grade, fully wired React code based solely on what is specified in this document — a canonical implementation guide for the module described below.

⚠️ This is not a design discussion or a suggestion list. It is an enforceable implementation standard. Every section is a contract the generated code must satisfy.

---

## ❗ Do Not

❌ Do not generate components, hooks, or utilities not listed in this document.

❌ Do not introduce libraries or imports outside the Dependency Contract (Section 7).

❌ Do not invent API shapes, status codes, or state fields not defined here.

❌ Do not use class components, lifecycle methods, or deprecated React patterns.

❌ Do not store business logic inside UI components — it belongs in custom hooks.

❌ Do not say "this could also be done with X" — implement what is specified.

❌ Do not generate backend code, database schemas, or infrastructure config.

---

## ✅ Do

✅ Use functional components and React hooks exclusively.

✅ Use TypeScript with explicit types for all props, state, API payloads, and return values.

✅ Separate concerns strictly: UI renders props, hooks own logic, utils are pure functions.

✅ Apply the exact naming conventions defined in Section 8.

✅ Implement every error path, not just the happy path.

✅ Output a Post-Generation Checklist (Section 10) after all code is written.

✅ Reference applicable standards (WCAG 2.1, OWASP ASVS) only where directly enforced by this spec.

---

## 📄 Output Format

Generate the following files, in this order:

1. `types.ts` — all interfaces and enums
2. `constants.ts` — enums, magic values, config
3. `hooks/use[ModuleName].ts` — business logic and API calls
4. `components/[ComponentName].tsx` — one file per component
5. `[ModuleName]Page.tsx` — page/container component
6. `[module-name].test.tsx` — co-located test stubs
7. Post-Generation Checklist

---

## 1. Overview

### 1.1 Purpose

*[1 paragraph. What does this module enable? What user need does it satisfy? Example: "Allows an unauthenticated visitor to verify their identity using email and password, with an optional second factor, and establishes an authenticated session upon success."]*

### 1.2 Scope

*[What part of the application does this module own? Specify route, entry trigger, and exit boundary. Example: "Owns the /login route. Entered via redirect from the auth guard or direct navigation. Exits on successful authentication to the post-login redirect target, or to the MFA step within the same module."]*

### 1.3 Definitions

Define 5–8 key behavioral concepts used throughout this document. The model must use these exact terms consistently in generated code, comments, and variable names.

| Term | Definition |
|---|---|
| [Term 1] | [What it means in this module. e.g., "Principal — the authenticated user identity returned by the API after successful login."] |
| [Term 2] | [e.g., "Auth Step — the current stage of the login flow. One of: credentials, mfa, success."] |
| [Term 3] | [e.g., "Session Token — a short-lived JWT stored in an httpOnly cookie, never in localStorage."] |
| [Term 4] | [e.g., "Challenge — a server-issued MFA prompt identified by a sessionId, valid for 5 minutes."] |
| [Term 5] | [e.g., "Credential Error — a 401 response indicating the submitted email or password is incorrect."] |
| [Term 6] | [e.g., "Rate Limit Window — a 429 response with retryAfter seconds; the form is locked for that duration."] |

---

## 2. Standard Flow

### 2.1 Happy Path

Describe the complete success path as numbered, observable steps. Each step maps to a behavior the generated code must produce.

```
1. [e.g., User navigates to /login. If already authenticated, redirect immediately to /dashboard — do not render the form.]
2. [e.g., LoginForm renders with focus set to the email field.]
3. [e.g., User fills in email and password. Email validates on blur. Password does not validate until submit.]
4. [e.g., User submits the form. Submit button enters loading state: disabled, spinner visible, aria-busy="true" on form.]
5. [e.g., POST /auth/login is called with { email, password }.]
6. [e.g., On 200 OK: store token and user in authStore, navigate to post-login redirect target.]
```

### 2.2 Failure Paths

Describe each failure path as a discrete, numbered sequence. Include what triggers it, how the UI responds, and whether it is retryable.

```
FAILURE: Invalid Credentials (401)
  1. [e.g., API returns 401 with code: INVALID_CREDENTIALS.]
  2. [e.g., isLoading resets to false.]
  3. [e.g., Error banner renders above the form: "Incorrect email or password."]
  4. [e.g., Password field clears. Focus moves to password input.]
  5. Retryable: YES

FAILURE: Rate Limited (429)
  1. [e.g., API returns 429 with retryAfter: 30.]
  2. [e.g., Submit button disabled with countdown: "Try again in 29s…".]
  3. [e.g., After countdown expires, button re-enables.]
  Retryable: YES — after timer expires

FAILURE: Network / 5xx
  1. [e.g., Fetch throws or response is 500.]
  2. [e.g., Toast notification: "Something went wrong. Please try again."]
  3. [e.g., Form remains populated. Button re-enables.]
  Retryable: YES

FAILURE: MFA Required (202)
  1. [e.g., API returns 202 with code: MFA_REQUIRED and sessionId.]
  2. [e.g., Auth step transitions to 'mfa'. MfaStep renders, replacing LoginForm.]
  3. [e.g., User enters 6-digit code and submits.]
  4. [e.g., POST /auth/mfa called with { code, sessionId }.]
  5. [e.g., On 200: same success flow as above.]
  Retryable: YES (up to N attempts per session — define N)
```

### 2.3 Decision Logic

List conditional branching rules that the generated code must enforce. These are not UI preferences — they are behavioral contracts.

```
- If user is authenticated on mount → redirect to /dashboard, skip rendering.
- If step === 'mfa' → render MfaStep only; do not render LoginForm.
- If retryAfter is present in 429 response → start countdown; do not allow resubmit until zero.
- If API returns a field-level error (field property present) → attach error to that specific field, not the banner.
```

> ✅ Optional: Include a PlantUML or Mermaid sequence diagram only if all steps above are fully specified with no guessed transitions.

```
[Paste Mermaid diagram here if applicable]
```

---

## 3. Type Contract

Define all TypeScript types the model must use verbatim. Do not allow the model to infer or expand these.

```typescript
// types.ts

// --- Domain ---
interface [EntityName] {
  id: string
  // ... all fields with explicit types
}

// --- Form ---
interface [ModuleName]FormData {
  // ... one field per form input
}

// --- API Request / Response ---
interface [ModuleName]Request { }
interface [ModuleName]Response { }
interface ApiError {
  code: string
  message: string
  field?: string       // present only for field-level validation errors
}

// --- UI State ---
type [ModuleName]Step = '[step1]' | '[step2]' | '[step3]'

interface UIState {
  step: [ModuleName]Step
  isLoading: boolean
  error: string | null
}
```

---

## 4. State Contract

Specify where every piece of state lives and why. The model must not move state between layers.

### 4.1 Local State

| Variable | Type | Initial Value | Owner |
|---|---|---|---|
| `uiState` | `UIState` | `{ step: '[default]', isLoading: false, error: null }` | `use[ModuleName]` hook |
| `[field]` | `[type]` | `[value]` | `[component or hook]` |

### 4.2 Global State

| Store | Fields Read | Actions Dispatched | When |
|---|---|---|---|
| `use[StoreName]` | `[field1], [field2]` | `[actionName()]` | `[e.g., on successful login]` |

### 4.3 Form State

```
Library:        [e.g., react-hook-form]
Mode:           [e.g., onSubmit — validate only on submit, not on change]
DefaultValues:  [e.g., { email: '', password: '', rememberMe: false }]
Managed by:     use[ModuleName] hook — not by the component directly
```

### 4.4 Derived State (useMemo)

Do not store these as state. Compute them inline or in useMemo.

```
isFormValid  = [e.g., email !== '' && password.length >= MIN_PASSWORD_LENGTH]
hasError     = uiState.error !== null
isRateLimited = retryCountdown > 0
```

---

## 5. Component Contract

Define every component's interface, rendering condition, and ownership. The model must not create components outside this list.

### 5.1 Component Tree

```
<[ModuleName]Page>                         ← page/container; owns nothing directly; delegates to hook
  │  uses: use[ModuleName]()
  │
  ├── <[FormComponent]>                    ← rendered when step === '[step]'
  │     props:
  │       onSubmit: (data: [FormData]) => void
  │       isLoading: boolean
  │       error: string | null
  │     contains:
  │       <[FieldComponent] />             ← stateless; registered with RHF
  │       <[FieldComponent] />
  │       <[ButtonComponent] />
  │
  ├── <[StepComponent]>                    ← rendered when step === '[step]'; lazy-loaded
  │     props:
  │       onSubmit: ([param]: [type]) => void
  │       onBack: () => void
  │       isLoading: boolean
  │       error: string | null
  │
  └── <[AuxComponent] />                   ← stateless; emits callbacks upward
        props: { [callbackName]: () => void }
```

### 5.2 Component Rules

```
- Every component must have a JSDoc comment describing its single responsibility.
- Stateless leaf components (fields, buttons) must not access any hook directly.
- <[FormComponent]> must be wrapped in React.memo.
- <[StepComponent]> must be lazy-loaded with React.lazy + Suspense.
- The page component must not contain any conditional logic beyond rendering the correct step.
```

---

## 6. API Contract

Define every endpoint this module calls. The model must handle every status code listed. No others should be assumed.

### Endpoint: [METHOD] [/path]

```
Purpose:     [e.g., Authenticate user with email and password]
Headers:     Content-Type: application/json
Request:     [RequestType]
Responses:
  200 OK     → [ResponseType]         Action: [e.g., store token, navigate to /dashboard]
  202 OK     → { code: string, sessionId: string }  Action: [e.g., transition step to 'mfa']
  401        → ApiError               Action: [e.g., set error banner, clear password field]
  422        → ApiError (with field)  Action: [e.g., attach error to named form field]
  429        → { code, retryAfter: number }  Action: [e.g., start countdown timer]
  500        → any                    Action: [e.g., show generic toast, re-enable form]
```

*Repeat block for each endpoint.*

---

## 7. Dependency Contract

### 7.1 Allowed Imports

The model may only import from this list. Any package not listed here is forbidden.

```
react, react-dom                          ← core
react-hook-form                           ← form state management
zod + @hookform/resolvers/zod             ← schema validation
zustand                                   ← global state
[next/navigation | react-router-dom]      ← routing — pick one
@/components/ui/*                         ← internal design system primitives
@/lib/api                                 ← shared HTTP client instance
@/stores/[storeName]                      ← existing global stores only
```

### 7.2 Forbidden

```
- axios (use the existing @/lib/api client)
- localStorage / sessionStorage (tokens are httpOnly cookie only)
- any state manager not already in the project (no new Redux, Jotai, etc.)
- moment / dayjs (not required in this module)
- any UI library not listed in 7.1
```

---

## 8. Best Practices & Contracts

### 8.1 Validation Contract

Split into Base Standard and Org Standard.

**✅ Base Standard**

| Field | Rule | Error Message | Trigger |
|---|---|---|---|
| `email` | Required; RFC 5321 format; max 254 chars | "Enter a valid email address" | On blur; re-validate on submit |
| `password` | Required; min [N] chars | "Password must be at least [N] characters" | On submit only |
| `[field]` | [rule] | [message] | [trigger] |

**🏢 Org Standard**

```
- [e.g., Password field must not validate on change — avoids aggressive UX per design system policy.]
- [e.g., Field-level errors must use the exact error message strings above — do not paraphrase.]
```

### 8.2 Error Contract

**✅ Base Standard**

| Level | Trigger | UI Treatment | Retryable |
|---|---|---|---|
| Field error | RHF validation failure | Inline below field, `aria-describedby` linked | YES |
| API 4xx (banner) | 401, 403 response | `role="alert"` banner above form, dismissible | YES |
| API 429 | Rate limit response | Countdown timer on submit button | YES — after timer |
| API 5xx | 500 or network throw | Toast, top-right, auto-dismiss 5s | YES |
| Unhandled | Uncaught exception | Existing `ErrorBoundary` — do not create a new one | NO |

**🏢 Org Standard**

```
- [e.g., Never display raw API error messages — map all codes to user-safe strings defined in constants.ts.]
- [e.g., Error state must be cleared on any user input change via resetError().]
- [e.g., On unmount, clear error state in useEffect cleanup.]
```

### 8.3 Accessibility Contract

**✅ Base Standard (WCAG 2.1 AA)**

```
- Form element: <form aria-label="[descriptive label]">
- Heading: <h1> for page title — not a <div> or <p>
- All inputs paired with explicit <label htmlFor="[id]">
- Errors linked via aria-describedby from input to error element ID
- Error banners: role="alert" aria-live="polite"
- Loading state: aria-busy="true" on form while isLoading is true
- Colour contrast: minimum 4.5:1 for all text (WCAG 1.4.3)
- Touch targets: minimum 44×44px for all interactive elements (WCAG 2.5.5)
- Error states must not rely on colour alone — use icon + text
```

**Tab Order (enforced, left to right, top to bottom):**

```
[Field 1] → [Field 2] → [Primary CTA] → [Secondary link] → [Aux actions]
```

**🏢 Org Standard**

```
- [e.g., All interactive elements must be reachable via keyboard — no click-only interactions.]
- [e.g., Form submission via Enter key in the last field must work without JS workarounds.]
```

### 8.4 Security Contract

**✅ Base Standard (OWASP ASVS 4.0)**

```
- Tokens must be stored in httpOnly cookies only — NEVER in localStorage or sessionStorage.
- All inputs must carry correct autocomplete attributes:
    email field:    autocomplete="email"
    password field: autocomplete="current-password"
- Submit button must be disabled immediately on click — prevents double-submit.
- Password field value must be cleared from form state on success or unmount.
- Console.log must never output passwords, tokens, or session identifiers.
- Raw API error messages must not be displayed in the UI verbatim.
```

**🏢 Org Standard**

```
- [e.g., All auth-related API calls must go through @/lib/api — never raw fetch directly.]
- [e.g., MFA session IDs must not be stored in component state beyond the lifetime of the MFA step.]
```

### 8.5 Performance Contract

**✅ Base Standard**

```
- <[FormComponent]> must be wrapped in React.memo.
- Callbacks passed as props must be memoized with useCallback in the parent.
- Conditionally-rendered step components must be lazy-loaded with React.lazy + Suspense.
- Do not use useEffect to sync derived values — compute them inline or with useMemo.
- Do not duplicate API response data into local useState if it is already covered by global store.
```

**🏢 Org Standard**

```
- [e.g., Session restoration call (GET /auth/me) must be guarded by a ref to prevent double-invocation in StrictMode.]
```

---

## 9. Test Contract

### 9.1 Unit Tests (Vitest + React Testing Library)

The model must generate test stubs for every case below.

**Component: `[FormComponent]`**

```
✓ renders all fields and the submit button
✓ shows field-level error when [field] is empty on submit
✓ shows error for invalid [field] format
✓ disables submit button when isLoading is true
✓ calls onSubmit with correct values on valid submission
✓ clears error state on input change
```

**Hook: `use[ModuleName]`**

```
✓ calls [METHOD] [/endpoint] with correct payload
✓ transitions step to '[step]' on [status code] response
✓ calls [store].[action]() on [status code] response
✓ sets error message on [failure status] response
✓ starts countdown timer on 429 response
```

### 9.2 Mock Strategy

```
- Mock @/lib/api at module level with vi.mock('@/lib/api')
- Mock [storeName] with vi.mock('@/stores/[storeName]')
- Mock [router hook] to assert navigation calls
- Use deterministic test data only — no random values, no PII
```

### 9.3 Coverage Target

```
Minimum line coverage: 80% across all files in this module.
```

---

## 10. Architectural Constraints

Each constraint is labeled as:

**Enforced Constraint** — must be implemented exactly as stated.
**Design Choice** — implementation pattern to follow.
**Assumption** — requires external integration to be in place.

```
[Enforced Constraint] Business logic, API calls, and state transitions live exclusively in use[ModuleName] — not in any component.

[Enforced Constraint] The page component renders only the active step component and passes down hook-returned values as props. It contains no conditional logic of its own beyond step routing.

[Design Choice] Form state is managed by react-hook-form internally. Local useState is used only for non-form UI state (isLoading, step, error).

[Design Choice] Global auth state is written to [storeName] on success. Reading from that store is the responsibility of the auth guard — not this module.

[Assumption] An HTTP client instance exists at @/lib/api that handles base URL, default headers, and response parsing. This module does not configure it.

[Assumption] An ErrorBoundary is already mounted above this route in the component tree. This module does not render its own.

[Assumption] Routing (redirect on success, redirect if already authenticated) depends on [router hook] being available in the render context.
```

---

## 11. Out of Scope

The model must not generate any of the following. If a related concept appears in the spec, do not expand beyond what is defined here.

```
❌ [e.g., /auth/callback OAuth handler — separate module]
❌ [e.g., Password reset flow — separate module]
❌ [e.g., Registration form — separate module]
❌ [e.g., Session token refresh — handled in @/lib/api interceptor]
❌ [e.g., Email verification screen]
❌ Any backend code, API route handlers, or database access
```

---

## 12. Example Fixtures

Provide realistic, deterministic data. The model must use these in test stubs and infer no additional fields.

```typescript
// Successful response
const mock[ModuleName]Success: [ResponseType] = {
  // ... exact fields matching the type in Section 3
}

// Each failure response
const mockInvalidCredentials: ApiError = { code: 'INVALID_CREDENTIALS', message: '...' }
const mockRateLimited = { code: 'RATE_LIMITED', retryAfter: 30 }
const mockMfaRequired = { code: 'MFA_REQUIRED', sessionId: 'ses_test_abc123' }
```

---

## 13. Naming Conventions

The model must apply these consistently across all generated files.

| Construct | Convention | Example |
|---|---|---|
| Components | PascalCase | `LoginForm`, `SubmitButton` |
| Hooks | camelCase, prefixed `use` | `useLogin`, `useMfaTimer` |
| Types / Interfaces | PascalCase | `LoginFormData`, `UIState` |
| Constants | SCREAMING_SNAKE_CASE | `MAX_ATTEMPTS`, `MFA_CODE_LENGTH` |
| Event handlers | prefixed `handle` or `on` | `handleSubmit`, `onGoogleLogin` |
| Test files | `*.test.tsx` / `*.test.ts` | `LoginForm.test.tsx` |
| Style classes | kebab-case or Tailwind utilities | `login-form__error` |

---

## 14. Post-Generation Checklist

Output this section after all code is generated. Each item must be confirmed or flagged.

```
□ All types from Section 3 used verbatim — no new fields added
□ State shape from Section 4 implemented with no additions or removals
□ Component tree from Section 5 matches exactly — no new components introduced
□ use[ModuleName] hook implements all happy and failure paths from Section 6
□ Every API status code from Section 6 is handled in generated code
□ All user flows from Section 2 are reachable in the generated code
□ Validation rules from Section 8.1 match exactly — timing, messages, fields
□ React.memo and useCallback applied per Section 8.5
□ All aria attributes from Section 8.3 are present
□ Security rules from Section 8.4 applied — no tokens in localStorage
□ Test stubs for all cases in Section 9.1 are generated
□ No imports outside the allowlist in Section 7
□ Naming conventions from Section 13 applied throughout
□ Nothing from the Out of Scope list in Section 11 was generated
```

---

## Appendix

### A. Glossary

| Term | Definition |
|---|---|
| [Term] | [Definition as used in this document] |

### B. Standards Referenced

| Standard | Section Applied |
|---|---|
| WCAG 2.1 AA | Section 8.3 — Accessibility Contract |
| OWASP ASVS 4.0 | Section 8.4 — Security Contract |
| RFC 5321 | Section 8.1 — Email format validation |

### C. Changelog

| Version | Date | Author | Notes |
|---|---|---|---|
| 1.0 | [YYYY-MM-DD] | [Author] | Initial standard |
