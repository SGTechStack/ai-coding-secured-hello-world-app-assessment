# MFA Frontend – React Module Specification (MCC)

You are a senior React engineer implementing the MFA frontend module from first principles.

Your job is to generate production-grade, fully wired React code based solely on what is specified in this document — a canonical implementation guide for the MFA frontend module described below.

⚠️ This is not a design discussion or a suggestion list. It is an enforceable implementation standard. Every section is a contract the generated code must satisfy.

**Related backend standard**:
- [Cloud MFA Application Standard](../../MFA_MCC/MCC_MFA_Application_Standard.md)

## 1. Overview

### 1.1 Purpose

Enables users to set up and verify multi-factor authentication factors (OTP, TOTP). There are three key areas:
* On the settings page, users can provision a TOTP authenticator app key. TOTP re-provisioning is gated behind an OTP challenge.
* On any page that performs a critical transaction, users are prompted to verify their identity with the appropriate factor before the request is sent to the backend.
* A first-login advisory prompt informs users who have not yet completed MFA setup that critical transactions will be blocked until they do.

### 1.2 Scope

Owns the `/settings/mfa` route for factor setup. MFA verification dialogs are rendered inline on any page that contains a critical transaction — they are not route-owned. Session management and auth-guard redirect are out of scope.

## 2. Standard Flow

### 2.1 Happy Path — Critical Transaction Verification

```mermaid
sequenceDiagram
    participant User
    participant Form
    participant useMFA
    participant MFADialog
    participant Backend

    User->>Form: clicks Submit
    Form->>useMFA: handleMFA(submitFn, data, mfaType)
    note right of useMFA: checks cached mfaSetupRequired — false (setup complete)
    note right of useMFA: stores submitFn + data in refs
    opt mfaType === "X-OTP"
        useMFA->>Backend: GET /otp/generate
        Backend-->>useMFA: 200 OK
    end
    useMFA->>MFADialog: open dialog
    User->>MFADialog: enters code, clicks Verify
    MFADialog->>useMFA: callback(code)
    useMFA->>Backend: submitFn(data, code, mfaType) — header X-<TYPE>: code
    Backend-->>useMFA: 200 OK
    useMFA->>MFADialog: close dialog, clear state
```

### 2.2 Happy Path — TOTP Provisioning

```mermaid
sequenceDiagram
    participant User
    participant MFATotpForm
    participant useMFATotpForm
    participant useMFA
    participant Backend

    User->>useMFATotpForm: navigates to /settings/mfa
    useMFATotpForm->>Backend: GET /mfa/queryTotpKeyExists
    Backend-->>useMFATotpForm: false (no key yet)
    useMFATotpForm->>MFATotpForm: enable Generate QR Code button
    User->>MFATotpForm: clicks Generate QR Code
    MFATotpForm->>useMFA: handleMFA(viewQrCallback, data, "X-OTP")
    useMFA->>Backend: GET /otp/generate
    Backend-->>useMFA: 200 OK
    useMFA->>MFADialog: open OTP dialog
    User->>MFADialog: enters OTP, clicks Verify
    useMFA->>Backend: GET /mfa/authoriseResetTotp (X-Otp: code)
    Backend-->>MFATotpForm: PNG byte array
    MFATotpForm->>User: renders QR code image
    User->>MFATotpForm: scans QR with authenticator app, enters 6-digit code, clicks Verify
    MFATotpForm->>Backend: POST /mfa/confirmTotpSetup (X-TOTP: code)
    Backend-->>MFATotpForm: true
    MFATotpForm->>User: toast "TOTP Verified." — QR image and input hidden
```

### 2.3 Failure Paths

```mermaid
sequenceDiagram
    participant User
    participant Form
    participant useMFA
    participant MFADialog
    participant Backend

    note over User,Backend: Invalid Code (412)
    useMFA->>Backend: submitFn(data, code, mfaType)
    Backend-->>useMFA: 412
    useMFA->>MFADialog: re-open, retry=true, description "Invalid code. Please try again."
    User->>MFADialog: re-enters code

    note over User,Backend: Rate Limited
    useMFA->>Backend: submitFn(data, code, mfaType)
    Backend-->>useMFA: 429
    useMFA->>MFADialog: close dialog
    useMFA->>User: toast "Too many attempts. Please try again later."

    note over User,Backend: Insufficient Privileges
    useMFA->>Backend: submitFn(data, code, mfaType)
    Backend-->>useMFA: 403
    useMFA->>MFADialog: close dialog
    useMFA->>User: toast "Insufficient Privileges"

    note over User,Backend: MFA Not Set Up (pre-check — dialog never opens)
    User->>Form: clicks Submit
    Form->>useMFA: handleMFA(submitFn, data, mfaType)
    note right of useMFA: mfaSetupRequired is true (cached from mount)
    useMFA->>User: toast "MFA Setup Required — Please set up your MFA"
    note right of useMFA: redirect to /settings/mfa — dialog never opens

    note over User,Backend: MFA Not Set Up (server-side fallback — stale cache)
    useMFA->>Backend: submitFn(data, code, mfaType)
    Backend-->>useMFA: 422 — detail "User Details not found."
    useMFA->>MFADialog: close dialog
    useMFA->>User: toast "MFA Required — Please set up your MFA"

    note over User,Backend: TOTP Confirmation Failed
    MFATotpForm->>Backend: POST /mfa/confirmTotpSetup
    Backend-->>MFATotpForm: false
    MFATotpForm->>User: toast "Failed to verify TOTP." — user may retry

    note over User,Backend: OTP Expired (412 on retry)
    useMFA->>MFADialog: re-open with retry=true, ResendOtpButton visible
```


## 3. Components and Types

### 3.1 Types

```typescript
// types.ts

// --- Factor ---
type MfaType = "X-OTP" | "X-TOTP"
type MfaDisplayType = "OTP" | "TOTP"
```

### 3.2 Components

> All state and side effects live in hooks. Components receive everything they need via props derived from hook return values.

| Component | Description | Props | Hook(s) & how linked |
|:---|:---|:---|:---|
| `MFADialog` | Blocking dialog that prompts the user to enter a 6-digit factor code before a critical transaction proceeds. Renders a digit-slot input, Cancel and Verify buttons, and a Resend OTP control when the factor type is OTP. | `mfaType: MfaDisplayType`<br>`callback: (code: string) => void`<br>`description?: string`<br>`open: boolean`<br>`setOpen: (open: boolean) => void`<br>`onCancel: () => void`<br>`showSpinner: boolean`<br>`resendOtp?: () => Promise<void>` | `useMFA` (via `mfaDialogProps`). Parent spreads `mfaDialogProps` onto the component. Clicking **Verify** calls `setMfaCode(code)` from hook → triggers the `useEffect` on `mfaCode` to execute the original method with the MFA header. Clicking **Cancel** calls `onCancel()` (provided by hook; closes dialog and clears retry state). |
| `MFATotpForm` | Form for provisioning a TOTP key. Renders a Generate QR Button, QR code image, a confirmation code input, and Verify button. Generate QR button is **disabled when `keyExists = true`** — re-provisioning requires OTP authorisation. | `handleSubmit: () => void`<br>`imageSrc: string`<br>`showSpinner: boolean`<br>`codeToVerify: string`<br>`setCodeToVerify: (code: string) => void`<br>`verifyTotp: () => void`<br>`isVerified: boolean`<br>`keyExists: boolean` | `useMFATotpForm`, `useMFA`. Clicking **Generate QR Code** calls `handleMFA(viewQrCallback, data, "X-OTP")` which opens the OTP dialog first. Typing into the confirmation input calls `setCodeToVerify`. Clicking **Verify TOTP** calls `verifyTotpCode()`. |
| `MFAPrompt` | First-login advisory modal shown when the user has not completed MFA setup. Dismissible; does not block navigation or enforce setup. | `open: boolean`<br>`setOpen: (open: boolean) => void`<br>`onClick: () => void` | `useMfaPrompt`. Hook sets `promptOpen = true` on mount if setup is required. Clicking **Close** or **Set Up MFA** calls `setPromptOpen(false)`. |
| `ResendOtpButton` | Inline "Resend OTP" control rendered inside `MFADialog` when the factor type is OTP. Shows a spinner during the request and a confirmation on success. | `resendOtp: () => Promise<void>` | `useMFA` (via prop). Parent passes `resendOtp` as a prop. Clicking the button calls `resendOtp()`. |

---

## 5. Hooks


### `useMFA`

**Purpose**: Orchestrates MFA verification for transactions. Stores the originating submit function and form data in refs, manages dialog state, and re-invokes the submit function with the collected factor code.

**Parameters**: `options?: { skipSetupCheck?: boolean }` — when `true`, the on-mount setup check and `handleMFA` pre-check are skipped. Required on the `/settings/mfa` page where `useMFA` is used for OTP-gated TOTP provisioning — without this flag, the pre-check would redirect users to the page they are already on.

**How it works**:
- On mount (unless `skipSetupCheck` is `true`): calls `GET /mfa/requireTotpSetup` and caches the result as `mfaSetupRequired`.
- `handleMFA` pre-check (unless `skipSetupCheck` is `true`): if `mfaSetupRequired` is `true`, toasts "MFA Setup Required — Please set up your MFA", redirects to `/settings/mfa`, and returns early — the dialog never opens. This prevents users who have not registered MFA from seeing the verification dialog.
- If `mfaType` is `X-OTP`, `GET /otp/generate` is called before the dialog opens.
- `requestCallback` stores the original critical transaction function and `dataRef` stores the original function parameters / data.
- User enters code → dialog calls `setMfaCode(code)` via callback → `useEffect` on `mfaCode` fires → calls `submitFn(data, code, mfaType)` with the factor header → resets `mfaCode` to `""`.
- Triggers `handleMFAError` if receives an error back from server:
  - `412`: sets `retry = true`, updates dialog with invalid-code message.
  - `403`: toasts "Insufficient Privileges", closes dialog, no retry.
  - `422` (detail "User Details not found."): toasts "MFA Setup Required", closes dialog, redirects to MFA setup page, no retry. This is a server-side fallback for cases where the cached `mfaSetupRequired` flag is stale (e.g. admin removed MFA after page load).
  - `429`: reads `Retry-After` header (seconds); toasts "Too many attempts. Please try again in {retryAfter}s.", closes dialog, no retry.
  - Non-MFA error: `handleMFAError` returns `false` — caller handles it.

**Returns**
```typescript
{
  mfaDialogProps: MFADialogProps
  handleMFA: (submitFn: MFASubmitFn, data: unknown, mfaType: MfaType) => Promise<void>
  handleMFAError: (error: unknown) => Promise<boolean>
  handleMFASuccess: () => void
  handleMFAFinally: () => void
  resendOtp: () => Promise<void>
}
```

---

### `useMFATotpForm`

**Purpose**: Manages TOTP provisioning state — QR code image, confirmation code input, verified flag, and key-exists check.

**How it works**:
- `GET /mfa/queryTotpKeyExists` is called once on mount to check if `keyExists`. If a TOTP key already exists, the Generate QR Code button is disabled — re-provisioning requires OTP authorisation.
- Clicking Generate QR Code calls `handleMFA(viewQrCallback, data, "X-OTP")` from `useMFA`; the OTP dialog opens, OTP is generated, and `viewQrCallback` which calls `GET /mfa/authoriseResetTotp` is only invoked after the user verifies their OTP.
- QR Code PNG response is converted to an object URL via `URL.createObjectURL`, stored as `imageSrc` and displayed to user. The hook revokes the previous object URL on each `imageSrc` change and on unmount via a `useEffect` cleanup.
- `verifyTotpCode` calls `POST /mfa/confirmTotpSetup`: `true` → `isVerified = true`, `keyExists = true`, toast "TOTP Verified."; `false` → toast failure, `codeToVerify` unchanged (user can retry).


**Returns**
```typescript
{
  imageSrc: string
  codeToVerify: string
  setCodeToVerify: (code: string) => void
  verifyTotpCode: () => Promise<void>
  isVerified: boolean
  keyExists: boolean
  showSpinner: boolean
  viewQrCallback: (otp: string, data: unknown) => Promise<void>
}
```

---

### `useMfaPrompt`

**Purpose**: Fetches the MFA setup requirement flag on mount and controls the `MFAPrompt` dialog's open state.

**How it works**:
- On mount: calls `GET /mfa/requireTotpSetup`; if `true`, sets `promptOpen = true`.
- Prompt is advisory; dismissing calls `setPromptOpen(false)` and does not block navigation.


**Returns**
```typescript
{
  promptOpen: boolean
  setPromptOpen: (open: boolean) => void
}
```

**Notes**
```
- The prompt is advisory only. setPromptOpen(false) on dismiss — do not block navigation.
- No retry or polling — fetch once on mount only.
```

---

## 6. API Contract

### Endpoint: GET /mfa/requireTotpSetup

```
Purpose:     Determine whether the user needs to complete MFA setup (TOTP)
Consumers:   useMfaPrompt (advisory prompt), useMFA (pre-check guard)
Responses:
  200 OK     → boolean    Action: useMfaPrompt — if true, setPromptOpen(true)
                                  useMFA — cache as mfaSetupRequired for handleMFA pre-check
```

### Endpoint: GET /mfa/queryTotpKeyExists

```
Purpose:     Check whether the user already has an active TOTP key
Responses:
  200 OK     → boolean    Action: setKeyExists(response), setPromptOpen(!response)
```

### Endpoint: GET /mfa/authoriseResetTotp

```
Purpose:     Generate TOTP QR code, gated behind OTP verification
Headers:     X-Otp: <otp>
Responses:
  200 OK     → PNG byte array    Action: convert to object URL, setImageSrc(url)
  4xx/5xx           Action: toast generic error
```

### Endpoint: POST /mfa/confirmTotpSetup

```
Purpose:     Verify the 6-digit TOTP code against the provisioned key
Request:     { code: string }
Responses:
  200 OK     → boolean    Action: true → setIsVerified(true), toast "TOTP Verified."
                                   false → toast "Failed to verify TOTP."
```

### Endpoint: GET /otp/generate

```
Purpose:     Trigger OTP delivery to the user's registered contact
Responses:
  200 OK     → void       Action: (no state change; dialog opens or stays open)
  4xx/5xx    Action: toast generic error
```

### Required MFA Transaction Endpoint (caller-defined)

```
Purpose:     Any protected backend action requiring a factor header
Headers:     One of: X-OTP: <code> | X-TOTP: <code>
             Only attach when both mfaType and mfaCode are non-empty:
             { ...(mfaType && mfaCode ? { [mfaType]: mfaCode } : {}) }
Responses:
  200 OK     Action: handleMFASuccess() — dialog closes
  412  Action: handleMFAError detects 412 — dialog stays open with retry=true (invalid code)
  403        Action: handleMFAError detects 403 — toast "Insufficient Privileges", dialog closes
  422        Action: handleMFAError detects 422 — toast "MFA Setup required", dialog closes (when detail is "User Details not found.")
  429        Action: handleMFAError detects 429 — reads Retry-After header, toast "Too many attempts. Please try again in {retryAfter}s.", dialog closes
  other      Action: handleMFAError returns false — caller shows generic error
```

---


## 7. Best Practices & Contracts

### 7.1 Validation Contract

**Base Standard**

| Field | Rule | Error Message | Trigger |
|---|---|---|---|
| `codeToVerify` (TOTP) | Exactly 6 digits, non-empty, must be the correct code as verified with server | "Incorrect Code" | On submit only |

### 7.2 Error Contract

**Base Standard**

| Level | Trigger | UI Treatment | Retryable |
|---|---|---|---|
| MFA not set up (pre-check) | `mfaSetupRequired` is `true` (cached on mount) | Toast + redirect to MFA setup page — dialog never opens | NO |
| Invalid code (412) | Backend rejects or cannot read factor code | `description` prop on MFADialog, `role="alert"` | YES |
| Insufficient privileges (403) | Backend returns 403 | Toast, auto-dismiss 5s | NO |
| MFA not set up (422 fallback) | Backend returns 422 with MFA detail (stale cache) | Toast + redirect to MFA setup page, auto-dismiss 5s | NO |
| OTP expired (412) | OTP challenge expired | `description` prop on MFADialog, `role="alert"` | YES - resend OTP button |
| Rate limited (429) | Backend returns 429 with `Retry-After` header | Toast "Too many attempts. Please try again in {retryAfter}s.", auto-dismiss 5s | NO |
| Network / 5xx | Fetch throws or 500 | Toast, auto-dismiss 5s | YES |


- Error description on MFADialog must be cleared when the dialog re-opens for a new transaction (retry=false).
- Factor codes must be cleared from dialog local state on every Verify click and on Cancel.


### 7.3 Security Contract

**Base Standard**

- Factor codes must NEVER be stored in localStorage, sessionStorage, or any persistent store.
- MFA session data (e.g. codes, retry state) must not persist beyond the lifetime of the dialog interaction (e.g. after submission).
- Console.log must never output OTP or TOTP codes.
- Raw API error messages must not be displayed in the UI.
- Submit/Verify button must be disabled immediately on click to prevent double-submit.
- Verify button must be disabled until the input reaches 6 characters.

```tsx
// Submit/Verify button — disable while request is in-flight (showSpinner = true)
<Button type="submit" disabled={showSpinner}>
  {showSpinner ? <Spinner /> : "Submit"}
</Button>

// MFADialog — disable until 6 digits entered or while in-flight
<Button disabled={value.length < 6 || showSpinner} onClick={...}>
  {showSpinner ? <Spinner /> : "Verify"}
</Button>

// MFATotpForm confirmation — disable until 6 digits entered or while in-flight
<Button disabled={codeToVerify.length < 6 || showSpinner} onClick={() => verifyTotp()}>
  Verify
</Button>
```

- Object URLs created for QR images must be revoked on unmount.
- All MFA-related API calls must go through @/lib/api — never raw fetch directly.


---

## 8. Test Contract

### 8.1 Unit Tests (Vitest + React Testing Library)

**Component: `MFADialog`**

```
✓ renders title as "<mfaType> Verification"
✓ shows ResendOtpButton when mfaType is "OTP"
✓ does not show ResendOtpButton when mfaType is "TOTP"
✓ calls callback with entered code on Verify click
✓ clears input value after Verify click
✓ calls onCancel() on Cancel
✓ shows spinner on Verify button when showSpinner is true
✓ renders description text when description prop is provided
```

**Hook: `useMFA`**

```
✓ calls GET /mfa/requireTotpSetup on mount and caches result
✓ skips GET /mfa/requireTotpSetup on mount when skipSetupCheck is true
✓ does not open dialog and redirects to /settings/mfa when mfaSetupRequired is true
✓ opens dialog and stores submitFn when handleMFA is called and mfaSetupRequired is false
✓ calls GET /otp/generate before opening dialog when mfaType is "X-OTP"
✓ invokes submitFn with collected code on useEffect trigger
✓ sets retry=true and reopens dialog on 412 error (invalid code)
✓ sets retry=true and reopens dialog on 412 error (missing factor)
✓ shows toast and closes dialog on 403 error
✓ shows MFA required toast on 422
✓ shows rate-limited toast and closes dialog on 429 error
✓ closes dialog and clears state on handleMFASuccess
✓ sets showSpinner=false on handleMFAFinally
```

---

## 9. Architectural Constraints

**[Enforced Constraint]** Business logic, API calls, and state transitions live exclusively in hooks — not in any component.

**[Enforced Constraint]** Factor codes must be cleared from state immediately after use. No factor code survives beyond the Verify callback.


**[Assumption]** An HTTP client instance exists at `@/lib/api` that handles base URL, default auth headers, and response parsing. This module does not configure it.

**[Assumption]** Toast notifications are available via the project's design system. This module does not implement a toast system.


---

## 10. Example Fixtures

```typescript
// TOTP verification
const mockTotpVerifiedTrue = true
const mockTotpVerifiedFalse = false
const mockQrPngBytes = new Uint8Array([137, 80, 78, 71]) // PNG magic bytes (stub)

// MFA dialog interaction
const mockMfaCode = '123456'
const mockMfaTypeOtp: MfaType = 'X-OTP'
const mockMfaTypeTotp: MfaType = 'X-TOTP'

// API errors
const mock412Error = { status: 412, data: { code: 'INVALID_FACTOR' } }
const mock412MissingHeader = { status: 412, data: { code: 'MISSING_HEADER' } }
const mock403Error = { status: 403, data: { code: 'FORBIDDEN' } }
const mock422MfaError = { status: 422, data: { detail: 'User Details not found.' } }
const mock412OtpExpired = { status: 412, data: { detail: 'OTP expired.' } }
const mock429Error = { status: 429, data: { code: 'TOO_MANY_REQUESTS' } }
```
---

## Appendix

### A. Glossary

| Term | Definition |
|---|---|
| Factor | A single MFA method: OTP or TOTP |
| Critical Transaction | A backend-protected action that requires a factor header |
| MFA Dialog | The `MFADialog` component; collects a factor code via a ref-based callback |
| Retry State | `retry=true` in dialog state; triggers an error description on re-open |
| OTP Challenge | Server-generated one-time password; required for TOTP re-provisioning |
| Object URL | Blob URL created from a PNG byte array response for QR display; must be revoked on unmount |


### . Changelog

| Version | Date | Author | Notes |
|---|---|---|---|
| 1.0 | 2026-04-29 | — | Initial standard (combined) |
| 2.0 | 2026-05-22 | — | Refactored from combined standard: MCC-only, IS_MCC_ENV removed |
