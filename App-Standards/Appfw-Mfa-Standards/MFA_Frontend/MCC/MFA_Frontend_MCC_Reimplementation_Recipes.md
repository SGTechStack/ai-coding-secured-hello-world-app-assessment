# MFA Frontend — Reimplementation Recipes (MCC)

> Recipes show how to reimplement the frontend MFA flows described in the MFA Frontend MCC Standard using React, react-hook-form, Zod, and a generated OpenAPI client. All code targets React 18+ with TypeScript.
>
> This recipe set covers the **MCC** deployment profile: OTP + TOTP factors, OTP delivery via MCNS, TOTP re-provisioning gated behind an OTP challenge.
>
> These recipes reference the following backend API standard:
> - [Cloud MFA Application Standard](../../MFA_MCC/MCC_MFA_Application_Standard.md)

---

## Recipe 1: MFA Verification Dialog

**Goal**: A reusable blocking dialog that prompts for a factor code (OTP or TOTP). Renders a digit-slot input, a Cancel and Verify button, and a "Resend OTP" control when the factor is OTP.

**How it works in the reference**: `MFADialog` receives `mfaType` as `MfaDisplayType`, a `callback`, open/retry control props, and an optional `resendOtp` callback forwarded to `ResendOtpButton` when the factor type is OTP.

```tsx
import {
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/basic/alert-dialog";
import { Button } from "@/components/basic/button";
import { AlertDialog } from "@radix-ui/react-alert-dialog";
import { REGEXP_ONLY_DIGITS } from "input-otp";
import { useState } from "react";
import { ResendOtpButton } from "./resend-otp-button";
import { InputOTP, InputOTPGroup, InputOTPSlot } from "@/app/_components/atomic/input-otp";
import Spinner from "@/app/_components/atomic/spinner";

interface MFADialogProps {
  callback: (code: string) => void;
  mfaType: MfaDisplayType;
  description?: string;
  open: boolean;
  setOpen: (value: boolean) => void;
  onCancel: () => void;
  showSpinner: boolean;
  resendOtp?: () => Promise<void>;
}

export default function MFADialog({
  callback,
  mfaType,
  description,
  open,
  setOpen,
  onCancel,
  showSpinner,
  resendOtp,
}: MFADialogProps) {
  const [value, setValue] = useState("");

  return (
    <AlertDialog open={open}>
      <AlertDialogContent className="max-w-xl">
        <AlertDialogHeader className="pl-2 mt-5">
          <AlertDialogTitle className="text-4xl flex justify-center">
            {mfaType.toUpperCase()} Verification
          </AlertDialogTitle>
          <AlertDialogDescription className="flex justify-center">
            Enter your {mfaType.toUpperCase()} to proceed
          </AlertDialogDescription>
        </AlertDialogHeader>
        <div className="flex flex-col items-center justify-start mt-5">
          <InputOTP
            maxLength={6}
            pattern={REGEXP_ONLY_DIGITS}
            value={value}
            onChange={(value: string) => setValue(value)}
          >
            <InputOTPGroup>
              {Array.from({ length: 6 }).map((_, index) => (
                <InputOTPSlot key={index} index={index} className="w-16 h-16" />
              ))}
            </InputOTPGroup>
          </InputOTP>
        </div>
        <div className="flex justify-center text-red-500">{description}</div>
        {mfaType === "OTP" && (
          <div className="flex justify-center mt-2">
            <ResendOtpButton resendOtp={resendOtp!} />
          </div>
        )}
        <AlertDialogFooter className="sm:justify-center">
          <Button
            variant="outline"
            onClick={() => {
              onCancel();
              setValue("");
            }}
          >
            Cancel
          </Button>
          <Button
            className="bg-black hover:bg-slate-600 w-1/3"
            disabled={value.length < 6 || showSpinner}
            onClick={() => {
              callback(value);
              setValue("");
            }}
          >
            {showSpinner ? <Spinner /> : "Verify"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
```

### Validation Rules

- `value` state is local to the dialog and MUST be cleared (`setValue("")`) on every verify click and on cancel.
- `mfaType` is `MfaDisplayType` — values are `"OTP"` or `"TOTP"` with no `"X-"` prefix. The dialog title is derived directly from this prop.
- `ResendOtpButton` MUST only render when `mfaType === "OTP"`. Pass `resendOtp` from the parent — `ResendOtpButton` does not call `useMFA` internally.
- `showSpinner` replaces the "Verify" text — do not render both simultaneously.

---

## Recipe 2: Central MFA Hook (`useMFA`)

**Goal**: A single hook that any form can use to add MFA verification to a critical transaction. Handles dialog state, response error parsing, OTP auto-generation, and request retry.

**How it works in the reference**: `useMFA` stores the original submit callback and form data in refs/state, then re-invokes them via a `useEffect` when the user enters a code. Error handling branches on `412` / `403` / `422` / `429`.

```typescript
import { OtpControllerService } from "@/__generated__/openapi/origin/services/OtpControllerService";
import { toast } from "@/components/hooks/use-toast";
import { querySetupPrompt } from "@/lib/services";
import { useEffect, useRef, useState } from "react";

type MFASubmitFn = (data: unknown, mfaCode: string, mfaType: string) => Promise<void>

export function useMFA(options?: { skipSetupCheck?: boolean }) {
  const [retry, setRetry] = useState(false);
  const [open, setOpen] = useState(false);
  const [showSpinner, setShowSpinner] = useState(false);
  const [mfaSetupRequired, setMfaSetupRequired] = useState(false);
  const mfaTypeRef = useRef("");
  const submitFnRef = useRef<MFASubmitFn | null>(null);
  const submitDataRef = useRef<unknown>(null);
  const [mfaCode, setMfaCode] = useState<string>("");

  // Pre-check: fetch MFA setup status on mount (skipped on settings page)
  useEffect(() => {
    if (options?.skipSetupCheck) return;
    const checkSetup = async () => {
      setMfaSetupRequired(await querySetupPrompt());
    };
    checkSetup();
  }, []);

  // Re-invoke the original submit function whenever the user enters a code.
  useEffect(() => {
    if (mfaCode && submitFnRef.current) {
      submitFnRef.current(submitDataRef.current, mfaCode, mfaTypeRef.current);
      setMfaCode("");
    }
  }, [mfaCode]);

  const handleMFAFinally = () => setShowSpinner(false);

  const handleMFASuccess = () => {
    setOpen(false);
    setRetry(false);
    setMfaCode("");
  };

  const handleMFAError = async (error: unknown): Promise<boolean> => {
    const status = (error as any).response?.status;
    const detail = (error as any).response?.data?.detail;

    if (status === 412) {
      setRetry(true);
      setOpen(true);
      return true;
    }

    if (status === 403) {
      toast({
        title: "Insufficient Privileges",
        description: "You do not have the privileges to perform this transaction.",
      });
      setOpen(false);
      return true;
    }

    if (status === 422 && detail === "User Details not found.") {
      toast({
        title: "MFA Required",
        description: "Please set up your MFA to perform this transaction.",
      });
      setOpen(false);
      return true;
    }

    if (status === 429) {
      const retryAfter = (error as any).response?.headers?.["retry-after"];
      toast({
        title: "Too many attempts",
        description: retryAfter
          ? `Please try again in ${retryAfter}s.`
          : "Please try again later.",
      });
      setOpen(false);
      return true;
    }

    return false;
  };

  const handleMFA = async (
    submitFn: MFASubmitFn,
    data: unknown,
    mfaType: string,
  ) => {
    // Pre-check: redirect if MFA not set up (skipped when skipSetupCheck is true)
    if (mfaSetupRequired) {
      toast({
        title: "MFA Setup Required",
        description: "Please set up your MFA to perform this transaction.",
      });
      // Navigate to /settings/mfa — implementation uses router.navigate or equivalent
      return;
    }

    submitFnRef.current = submitFn;
    submitDataRef.current = data;
    mfaTypeRef.current = mfaType;
    setRetry(false);

    if (mfaType === "X-OTP") {
      await OtpControllerService.generateOtp();
    }

    setOpen(true);
  };

  const resendOtp = async () => {
    await OtpControllerService.generateOtp();
  };

  return {
    mfaDialogProps: {
      // Strip "X-" prefix — dialog displays "OTP" or "TOTP"
      mfaType: mfaTypeRef.current?.substring(2) as MfaDisplayType,
      callback: setMfaCode,
      description: retry ? "Invalid code. Please try again." : undefined,
      open,
      setOpen,
      onCancel: () => { setOpen(false); setRetry(false); },
      showSpinner,
    },
    handleMFAFinally,
    handleMFASuccess,
    handleMFAError,
    handleMFA,
    resendOtp,
  };
}
```

### Validation Rules

- `mfaSetupRequired` is fetched once on mount via `GET /mfa/requireTotpSetup` and cached (unless `skipSetupCheck` is `true`). `handleMFA` checks this flag before opening the dialog — if `true`, it toasts and redirects to `/settings/mfa` without ever opening the dialog.
- `skipSetupCheck: true` MUST be passed when `useMFA` is used on the `/settings/mfa` page for OTP-gated TOTP provisioning. Without this flag, the pre-check would redirect users to the page they are already on.
- `submitFnRef` and `submitDataRef` store the original function and data in refs — refs do not trigger re-renders and avoid stale closures in the `useEffect` callback.
- OTP generation (`generateOtp`) MUST complete before `setOpen(true)` — the code is in transit by the time the dialog is visible to the user.
- For `412` errors (invalid code), `handleMFAError` only sets `retry=true` and re-opens the dialog — the refs from the original `handleMFA` call are already correct and must not be overwritten.
- `mfaType` is stripped of the `"X-"` prefix via `substring(2)` before being included in `mfaDialogProps` — the dialog receives `"OTP"` or `"TOTP"`.
- `description` is derived from `retry` inside the hook — callers do not need to inspect or forward `retry` to the dialog.

> **Note — 422 (MFA not set up) is a server-side fallback.** The pre-check in `handleMFA` catches the common case (MFA not yet registered) before the dialog opens. The 422 handler remains as a fallback for edge cases where the cached `mfaSetupRequired` flag is stale — e.g. an admin removed the user's MFA after the page loaded. In addition to showing the toast, redirect the user to `/settings/mfa` so they can act immediately.

---

## Recipe 3: Attaching Factor Headers to Protected Requests

**Goal**: Show how to attach the MFA factor code as an HTTP request header when retrying a protected operation.

**How it works in the reference**: The `handleSubmitForm` function conditionally spreads the header key/value using the `mfaType` string as the key.

```typescript
// Example: create a letter with an MFA factor header
async function createLetter(
  requestBody: Letter,
  mfaCode: string,
  mfaType: string,
) {
  return await apiClient.post(`letter/createLetter`, requestBody, {
    headers: {
      // Only attach if both values are present
      ...(mfaType && mfaCode ? { [mfaType]: mfaCode } : {}),
    },
  });
}
```

### Wiring to useMFA

```typescript
// In a form component:
const { mfaDialogProps, handleMFA, handleMFAFinally, handleMFASuccess, handleMFAError, resendOtp } = useMFA();

const handleSubmitForm = async (data: LetterFormValues, mfaCode = "", mfaType = "") => {
  try {
    await createLetter(data, mfaCode, mfaType);
    handleMFASuccess();
    toast({ title: "Letter created." });
  } catch (error) {
    const handled = await handleMFAError(error);
    if (!handled) {
      toast({ title: "Error", description: "Failed to create letter." });
    }
  } finally {
    handleMFAFinally();
  }
};

// On button click — pre-check runs, then opens dialog with the known factor type:
const onSubmitClick = () => handleMFA(handleSubmitForm, formData, "X-OTP");

// Render the dialog alongside the form:
return (
  <>
    <LetterForm onSubmit={onSubmitClick} />
    <MFADialog {...mfaDialogProps} resendOtp={resendOtp} />
  </>
);
```

### Validation Rules

- The header is only attached when `mfaType` and `mfaCode` are both non-empty strings. An empty `mfaType` would attach `"": value` to the headers — the guard prevents this.
- `handleMFAError` MUST return `true` for all MFA-related errors so the caller does not also show a generic error toast.

---

## Recipe 4: TOTP Provisioning Hook and Form

**Goal**: Manage QR code generation gated behind an OTP challenge, and the TOTP setup confirmation step.

**How it works in the reference**: `useMFATotpForm` fetches `keyExists` and manages image/verification state. Clicking Generate QR Code calls `handleMFA(viewQrCallback, data, "X-OTP")` from `useMFA` to gate QR generation behind an OTP challenge. `MFATotpForm` renders the view.

### Hook

```typescript
import { generateQrCode } from "@/lib/services";
import { useEffect, useState } from "react";
import { TotpControllerService } from "@/__generated__/openapi/origin";
import { toast } from "@/components/hooks/use-toast";

export default function useMFATotpForm() {
  const [imageSrc, setImageSrc] = useState("");
  const [codeToVerify, setCodeToVerify] = useState("");
  const [isVerified, setIsVerified] = useState(false);
  const [keyExists, setKeyExists] = useState(false);
  const [showSpinner, setShowSpinner] = useState(false);

  // Callback invoked by useMFA after OTP verification
  async function viewQrCallback(otp: string, _data: unknown) {
    setShowSpinner(true);
    try {
      const url = await generateQrCode(otp);
      setImageSrc(url);
    } catch {
      toast({ title: "Failed to generate QR code." });
    } finally {
      setShowSpinner(false);
    }
  }

  async function verifyTotpCode() {
    setShowSpinner(true);
    try {
      const verified = await TotpControllerService.verifyTotp(codeToVerify);
      if (verified) {
        toast({ title: "TOTP Verified." });
        setIsVerified(true);
        setKeyExists(true);
      } else {
        toast({ title: "Failed to verify TOTP." });
      }
    } catch {
      toast({ title: "Failed to verify TOTP." });
    } finally {
      setShowSpinner(false);
    }
  }

  useEffect(() => {
    const checkKeyExists = async () => {
      const keyExists = await TotpControllerService.queryTotpKeyExists();
      setKeyExists(keyExists);
    };
    checkKeyExists();
  }, []);

  useEffect(() => {
    return () => {
      if (imageSrc) URL.revokeObjectURL(imageSrc);
    };
  }, [imageSrc]);

  return { imageSrc, viewQrCallback, codeToVerify, setCodeToVerify, verifyTotpCode, isVerified, keyExists, showSpinner };
}
```

### Component

```tsx
import { InputOTP, InputOTPGroup, InputOTPSlot } from "@/app/_components/atomic/input-otp";
import Spinner from "@/app/_components/atomic/spinner";
import { Button } from "@/components/basic/button";
import { REGEXP_ONLY_DIGITS } from "input-otp";
import { QrCodeIcon } from "lucide-react";

interface MFATotpFormProps {
  showSpinner: boolean;
  imageSrc: string;
  handleSubmit: () => void;
  codeToVerify: string;
  setCodeToVerify: (value: string) => void;
  isVerified: boolean;
  verifyTotp: () => void;
  keyExists: boolean;
}

export default function MFATotpForm({
  showSpinner,
  imageSrc,
  handleSubmit,
  codeToVerify,
  setCodeToVerify,
  isVerified,
  verifyTotp,
  keyExists,
}: MFATotpFormProps) {
  return (
    <div className="flex flex-col w-1/2 ml-8 items-center h-[60vh] mt-10 pb-5 bg-slate-50">
      <div className="mt-10 font-semibold text-3xl">Set Time-based OTP</div>
      {!imageSrc && (
        <div className="w-4/5 text-center font-light text-sm mt-2">
          Clicking on <b>Generate QR Code</b> generates a new TOTP for you. The old TOTP can no longer be used.
        </div>
      )}
      {showSpinner ? (
        <Spinner className="w-10 h-10 mt-16" />
      ) : imageSrc && !isVerified ? (
        <img src={imageSrc} alt="QR Code" className="w-32 h-32 mt-5 bg-slate-50" />
      ) : (
        <QrCodeIcon className="mt-8 w-32 h-32" />
      )}
      {!showSpinner && (
        <Button
          className={`hover:bg-slate-600 mt-5 ${imageSrc ? "h-8 bg-slate-50 border border-black text-black" : "h-12 bg-black"}`}
          onClick={handleSubmit}
          disabled={keyExists}
        >
          {imageSrc ? "Regenerate QR Code" : "Generate QR Code"}
        </Button>
      )}
      {!isVerified && imageSrc && (
        <>
          <div className="w-4/5 text-left font-light text-sm mt-5">
            Please verify your TOTP code to confirm TOTP setup:
          </div>
          <InputOTP
            maxLength={6}
            pattern={REGEXP_ONLY_DIGITS}
            value={codeToVerify}
            onChange={(value: string) => setCodeToVerify(value)}
          >
            <InputOTPGroup>
              {Array.from({ length: 6 }).map((_, index) => (
                <InputOTPSlot key={index} index={index} className="mt-2 w-12 h-12" />
              ))}
            </InputOTPGroup>
          </InputOTP>
          <Button
            className="mt-3 h-3 bg-slate-50 hover:bg-slate-600 text-black border border-black"
            disabled={codeToVerify.length < 6 || showSpinner}
            onClick={() => verifyTotp()}
          >
            Verify
          </Button>
        </>
      )}
    </div>
  );
}
```

### Validation Rules

- `isVerified` hides the QR image and confirmation input. The confirmation flow is mandatory — do not set `isVerified` to `true` before the backend confirms the TOTP code.
- The Generate QR Code button is **disabled when `keyExists` is `true`** — re-provisioning in MCC always requires OTP authorisation via the dialog. Once the user completes the OTP flow, `viewQrCallback` is invoked and `keyExists` is updated.
- The `generateQrCode` utility (in `lib/services`) converts the PNG `arraybuffer` response to a blob object URL using `URL.createObjectURL`. `useMFATotpForm` revokes the previous object URL whenever `imageSrc` changes and on unmount via a `useEffect` cleanup — callers do not need to handle this.

---

## Recipe 5: MFA Setup Prompt

**Goal**: Show a modal if the user has not completed MFA setup. Prompt them to navigate to the MFA settings page.

**How it works in the reference**: `useMfaPrompt` calls `GET /mfa/requireTotpSetup` on mount. `MFAPrompt` renders the advisory dialog with a link to `/settings/mfa`.

### Hook

```typescript
import { querySetupPrompt } from "@/lib/services";
import { useEffect, useState } from "react";

export default function useMfaPrompt() {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const fetchResponse = async () => {
      setOpen(await querySetupPrompt());
    };
    fetchResponse();
  }, []);

  return { open, setOpen };
}
```

### Service function

```typescript
// lib/services.ts
async function querySetupPrompt() {
  const url = "mfa/requireTotpSetup";
  const response = await apiClient({ method: "GET", url });
  return response.data;
}
```

### Component

```tsx
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/basic/alert-dialog";
import { Button } from "@/components/basic/button";
import { AlertDialogCancel } from "@radix-ui/react-alert-dialog";
import { Link } from "@tanstack/react-router";
import { LucideMessageCircleWarning } from "lucide-react";

interface SetupPromptProps {
  open: boolean;
  setOpen: (bool: boolean) => void;
  onClick: () => void;
}

export default function MFAPrompt({ open, setOpen, onClick }: SetupPromptProps) {
  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle className="text-2xl">
            Set up Multi Factor Authentication
          </AlertDialogTitle>
        </AlertDialogHeader>
        <div>Your MFA has not been set.</div>
        <div className="flex">
          <LucideMessageCircleWarning className="w-8 h-8 mr-2" />
          You will not be able to perform critical transactions without your MFA setup.
        </div>
        <AlertDialogFooter className="mt-2">
          <AlertDialogCancel>Close</AlertDialogCancel>
          <Link to="/settings/mfa">
            <Button className="bg-black hover:bg-slate-600" onClick={onClick}>
              Set Up MFA
            </Button>
          </Link>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
```

### Validation Rules

- This endpoint MUST be `requireTotpSetup` — calling the standalone endpoint causes the prompt to appear or not appear incorrectly for users in an MCC deployment.
- The prompt is advisory only — users may dismiss with "Close" without setting up MFA. Critical transaction enforcement is handled backend-side.

---

## Recipe 6: Resend OTP Button

**Goal**: Render a standalone "Resend OTP" control inside the MFA dialog when the factor type is OTP. Shows a spinner during the resend request and a confirmation text on success.

```tsx
import { useState } from "react";
import { toast } from "@/components/hooks/use-toast";
import Spinner from "@/app/_components/atomic/spinner";

interface ResendOtpButtonProps {
  resendOtp: () => Promise<void>;
}

export function ResendOtpButton({ resendOtp }: ResendOtpButtonProps) {
  const [sent, setSent] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const handleClick = async () => {
    setIsLoading(true);
    try {
      await resendOtp();
      setSent(true);
    } catch {
      toast({ title: "Failed to send OTP", description: "Please try again later." });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex items-center">
      {isLoading ? (
        <Spinner />
      ) : (
        <p className="underline text-sm cursor-pointer hover:text-gray-500" onClick={handleClick}>
          Resend OTP
        </p>
      )}
      {sent && <p className="text-sm text-red-500 ml-2">- OTP Sent.</p>}
    </div>
  );
}
```

### Validation Rules

- `resendOtp` is received as a prop from the parent — the button does not call `useMFA` directly. The parent obtains `resendOtp` from `useMFA()` and passes it down.
- The `sent` state is not reset after a successful send — if the user clicks again it will re-send and the "OTP Sent." indicator remains visible.
- The button is only rendered inside `MFADialog` when `mfaType === "OTP"`. Do not render it for TOTP dialogs.

---

## Recipe 7: Composing the MFA Settings Page

**Goal**: Show how `SetupMFA` combines `MFATotpForm`, `useMFA`, and `MFADialog` into the full MFA settings view for MCC.

```tsx
import MFADialog from "./_components/mfa-dialog";
import MFATotpForm from "./_components/mfa-totp-form";
import { useMFA } from "./_hooks/use-handle-mfa";
import useMFATotpForm from "./_hooks/use-mfa-totp-form";

export default function SetupMFA() {
  const {
    imageSrc,
    viewQrCallback,
    codeToVerify,
    setCodeToVerify,
    verifyTotpCode,
    isVerified,
    keyExists,
    showSpinner: totpShowSpinner,
  } = useMFATotpForm();

  // Gate QR generation behind an OTP challenge via useMFA with X-OTP
  // skipSetupCheck: true — this IS the setup page; pre-check would redirect to itself
  const { mfaDialogProps, handleMFA, resendOtp } = useMFA({ skipSetupCheck: true });
  const handleGenerateQr = () => handleMFA(viewQrCallback, null, "X-OTP");

  return (
    <div className="flex justify-center items-center mt-10">
      <MFATotpForm
        codeToVerify={codeToVerify}
        verifyTotp={verifyTotpCode}
        keyExists={keyExists}
        isVerified={isVerified}
        setCodeToVerify={setCodeToVerify}
        handleSubmit={handleGenerateQr}
        imageSrc={imageSrc}
        showSpinner={totpShowSpinner}
      />
      <MFADialog {...mfaDialogProps} resendOtp={resendOtp} />
    </div>
  );
}
```

### Validation Rules

- `useMFA({ skipSetupCheck: true })` MUST be used here — this is the MFA setup page itself. Without `skipSetupCheck`, the pre-check would detect that MFA is not yet set up and redirect the user to `/settings/mfa`, creating an infinite loop.
- `handleMFA(viewQrCallback, null, "X-OTP")` triggers OTP generation and opens the shared `MFADialog` before invoking `viewQrCallback` with the entered OTP code.
- `MFADialog` MUST be rendered in the component tree so the dialog state from `useMFA` is tracked correctly.
- `MFAPinForm` is NOT rendered in MCC — there is no PIN setup flow.

---

## Implementation Notes: Additional Detail Beyond the Standard

The following patterns appear in these recipes but are not defined by the MFA Frontend MCC Standard. They are implementation choices made in the reference codebase.

**`input-otp` library and `InputOTP` component**
The digit-slot OTP input is built on the `input-otp` library. The `REGEXP_ONLY_DIGITS` pattern restricts input to numeric characters. The standard requires digit-only input but does not prescribe a specific library. Implementors may use any equivalent input component.

**`mfaTypeRef` as a `useRef`**
`useMFA` stores `mfaType` in a `ref` rather than state because changes to it should not trigger a re-render of the dialog mid-interaction. Reading `mfaTypeRef.current` in `mfaDialogProps` is safe because `mfaDialogProps` is re-evaluated on every render.

**`generateQrCode` PNG-to-object-URL conversion**
The `generateQrCode` service function in `lib/services` converts the PNG `arraybuffer` response to a blob object URL using `URL.createObjectURL`. `useMFATotpForm` revokes the previous object URL whenever `imageSrc` changes and on unmount via a `useEffect` cleanup — callers do not need to handle this.
