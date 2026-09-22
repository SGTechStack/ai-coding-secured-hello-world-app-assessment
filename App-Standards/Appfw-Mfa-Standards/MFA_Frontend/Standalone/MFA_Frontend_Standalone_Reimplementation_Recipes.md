# MFA Frontend — Reimplementation Recipes (Standalone)

> Recipes show how to reimplement the frontend MFA flows described in the MFA Frontend Standalone Standard using React, react-hook-form, Zod, and a generated OpenAPI client. All code targets React 18+ with TypeScript.
>
> This recipe set covers the **Standalone** deployment profile: PIN + TOTP factors, no OTP delivery, direct QR code generation without an OTP gate.
>
> These recipes reference the following backend API standard:
> - [Base Standalone Application Standard](../../MFA_Core/Base_Standalone_Application_Standard.md)

---

## Recipe 1: PIN Setup Form and Validation Schema

**Goal**: Render a PIN setup form with client-side validation. Allow submission only when the user does not already have a PIN.

**How it works in the reference**: `useMFAPinForm` defines a Zod schema for `^\d{6}$`, checks `setupAllowed` on mount, and submits via `PinControllerService.setPin`. `MFAPinForm` renders the controlled form.

### Step 1: Zod Validation Schema

```typescript
import { z } from "zod";

export const setupPinFormSchema = z
  .object({
    pin: z.string().regex(/^\d{6}$/, {
      message: "Pin must be exactly 6 digits.",
    }),
    confirmedPin: z.string().regex(/^\d{6}$/, {
      message: "Pin must be exactly 6 digits.",
    }),
  })
  .refine((data) => data.pin === data.confirmedPin, {
    message: "Pins do not match.",
    path: ["confirmedPin"],
  });
```

### Step 2: Hook

```typescript
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { PinControllerService } from "@/__generated__/openapi/origin/services/PinControllerService";
import { toast } from "@/components/hooks/use-toast";
import { setupAllowed } from "@/lib/services";

export default function useMFAPinForm() {
  const pinForm = useForm<z.infer<typeof setupPinFormSchema>>({
    resolver: zodResolver(setupPinFormSchema),
    defaultValues: { pin: "", confirmedPin: "" },
    mode: "onSubmit",
    resetOptions: { keepDefaultValues: true },
  });

  const [pinChangeSuccess, setPinChangeSuccess] = useState(false);
  const [showSpinner, setShowSpinner] = useState(false);
  const [isSetupAllowed, setIsSetupAllowed] = useState(true);

  const handlePinFormSubmit = async (data: z.infer<typeof setupPinFormSchema>) => {
    setShowSpinner(true);
    try {
      await PinControllerService.setPin(data.pin);
      setPinChangeSuccess(true);
      pinForm.reset();
      setIsSetupAllowed(false);
    } catch {
      toast({ title: "Error setting PIN" });
    } finally {
      setShowSpinner(false);
    }
  };

  useEffect(() => {
    const checkSetupAllowed = async () => {
      setIsSetupAllowed(await setupAllowed());
    };
    checkSetupAllowed();
  }, []);

  return { pinForm, handlePinFormSubmit, showSpinner, pinChangeSuccess, isSetupAllowed };
}
```

### Step 3: Component

```tsx
import { Button } from "@/components/basic/button";
import { Input } from "@/components/basic/input";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/complex/form";
import { UseFormReturn } from "react-hook-form";
import { z } from "zod";
import { setupPinFormSchema } from "../_hooks/use-mfa-pin-form";
import Spinner from "@/app/_components/atomic/spinner";

interface SetupPinProps {
  pinForm: UseFormReturn<z.infer<typeof setupPinFormSchema>>;
  handlePinFormSubmit: (data: z.infer<typeof setupPinFormSchema>) => Promise<void>;
  showSpinner: boolean;
  isSetupAllowed: boolean;
  pinChangeSuccess: boolean;
}

export default function MFAPinForm({
  pinForm,
  handlePinFormSubmit,
  showSpinner,
  isSetupAllowed = false,
  pinChangeSuccess,
}: SetupPinProps) {
  return (
    <div className="flex flex-col items-center w-1/2 h-[60vh] mt-10 pb-10 bg-slate-50">
      <Form {...pinForm}>
        <div className="mt-10 font-semibold text-3xl">Set PIN</div>
        <div className="font-light text-sm mt-2">Pin should be a 6 digit numeric number</div>
        <form className="w-3/4 flex flex-col" onSubmit={pinForm.handleSubmit(handlePinFormSubmit)}>
          <FormField
            control={pinForm.control}
            name="pin"
            render={({ field }) => (
              <FormItem className="mt-8">
                <FormLabel>PIN</FormLabel>
                <FormControl>
                  <Input {...field} placeholder="eg. 123456" disabled={!isSetupAllowed} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={pinForm.control}
            name="confirmedPin"
            render={({ field }) => (
              <FormItem className="mt-8">
                <FormLabel>Confirm PIN</FormLabel>
                <FormControl>
                  <Input {...field} placeholder="eg. 123456" disabled={!isSetupAllowed} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          {showSpinner ? (
            <Spinner className="self-center mt-10" />
          ) : (
            <Button disabled={!isSetupAllowed} className="self-center mt-8 bg-black hover:bg-slate-600 w-1/3">
              Set PIN
            </Button>
          )}
        </form>
      </Form>
      {!isSetupAllowed && (
        <div className={`font-light self-center text-sm mt-3 text-center w-2/3 ${pinChangeSuccess ? "text-blue-400" : "text-amber-600"}`}>
          {pinChangeSuccess
            ? "Pin set successfully."
            : "Pin has already been set. Contact your user manager to reset your pin."}
        </div>
      )}
    </div>
  );
}
```

### Validation Rules

- Schema MUST use `^\d{6}$` — any deviation allows invalid PINs through to the server.
- Form MUST be in `mode: "onSubmit"` — validation runs on submit only, per §7.1.
- `isSetupAllowed` MUST be fetched from the backend on mount — do not rely on local state alone.
- `pinForm.reset()` MUST be called on success to clear the raw PIN from component state.

---

## Recipe 2: MFA Verification Dialog

**Goal**: A reusable blocking dialog that prompts for a factor code (PIN or TOTP). Renders a digit-slot input, a Cancel and Verify button.

**How it works in the reference**: `MFADialog` receives `mfaType` as `MfaDisplayType`, a `callback`, open/retry control props.

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
}

export default function MFADialog({
  callback,
  mfaType,
  description,
  open,
  setOpen,
  onCancel,
  showSpinner,
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
- `mfaType` is `MfaDisplayType` — values are `"PIN"` or `"TOTP"` with no `"X-"` prefix. The dialog title is derived directly from this prop.
- `showSpinner` replaces the "Verify" text — do not render both simultaneously.

---

## Recipe 3: Central MFA Hook (`useMFA`)

**Goal**: A single hook that any form can use to add MFA verification to a critical transaction. Handles dialog state, response error parsing, and request retry.

**How it works in the reference**: `useMFA` stores the original submit callback and form data in refs/state, then re-invokes them via a `useEffect` when the user enters a code. Error handling branches on `412` / `403` / `422` / `429`.

```typescript
import { toast } from "@/components/hooks/use-toast";
import { querySetupPrompt } from "@/lib/services";
import { useEffect, useRef, useState } from "react";

type MFASubmitFn = (data: unknown, mfaCode: string, mfaType: string) => Promise<void>

export function useMFA() {
  const [retry, setRetry] = useState(false);
  const [open, setOpen] = useState(false);
  const [showSpinner, setShowSpinner] = useState(false);
  const [mfaSetupRequired, setMfaSetupRequired] = useState(false);
  const mfaTypeRef = useRef("");
  const submitFnRef = useRef<MFASubmitFn | null>(null);
  const submitDataRef = useRef<unknown>(null);
  const [mfaCode, setMfaCode] = useState<string>("");

  // Pre-check: fetch MFA setup status on mount
  useEffect(() => {
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
    // Pre-check: redirect if MFA not set up
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
    setOpen(true);
  };

  return {
    mfaDialogProps: {
      // Strip "X-" prefix — dialog displays "PIN" or "TOTP"
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
  };
}
```

### Validation Rules

- `mfaSetupRequired` is fetched once on mount via `GET /mfa/requirePinAndTotpSetup` and cached. `handleMFA` checks this flag before opening the dialog — if `true`, it toasts and redirects to `/settings/mfa` without ever opening the dialog.
- `submitFnRef` and `submitDataRef` store the original function and data in refs — refs do not trigger re-renders and avoid stale closures in the `useEffect` callback.
- For `412` errors (invalid code), `handleMFAError` only sets `retry=true` and re-opens the dialog — the refs from the original `handleMFA` call are already correct and must not be overwritten.
- `mfaType` is stripped of the `"X-"` prefix via `substring(2)` before being included in `mfaDialogProps` — the dialog receives `"PIN"` or `"TOTP"`.
- `description` is derived from `retry` inside the hook — callers do not need to inspect or forward `retry` to the dialog.

> **Note — 422 (MFA not set up) is a server-side fallback.** The pre-check in `handleMFA` catches the common case (MFA not yet registered) before the dialog opens. The 422 handler remains as a fallback for edge cases where the cached `mfaSetupRequired` flag is stale — e.g. an admin removed the user's MFA after the page loaded. In addition to showing the toast, redirect the user to `/settings/mfa` so they can act immediately.

---

## Recipe 4: Attaching Factor Headers to Protected Requests

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
const { mfaDialogProps, handleMFA, handleMFAFinally, handleMFASuccess, handleMFAError } = useMFA();

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
const onSubmitClick = () => handleMFA(handleSubmitForm, formData, "X-PIN");

// Render the dialog alongside the form:
return (
  <>
    <LetterForm onSubmit={onSubmitClick} />
    <MFADialog {...mfaDialogProps} />
  </>
);
```

### Validation Rules

- The header is only attached when `mfaType` and `mfaCode` are both non-empty strings. An empty `mfaType` would attach `"": value` to the headers — the guard prevents this.
- `handleMFAError` MUST return `true` for all MFA-related errors so the caller does not also show a generic error toast.

---

## Recipe 5: TOTP Provisioning Hook and Form

**Goal**: Manage QR code generation and the TOTP setup confirmation step. In standalone, clicking Generate QR Code calls the backend directly — no OTP gate is required.

**How it works in the reference**: `useMFATotpForm` fetches `keyExists` and manages image/verification state. Clicking Generate QR Code calls `GET /mfa/generateTotpQrCode` directly. `MFATotpForm` renders the view.

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

  // Called directly on button click — no OTP gate in standalone
  async function viewQrCallback(_otp: string, _data: unknown) {
    setShowSpinner(true);
    try {
      const url = await generateQrCode("");
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
          disabled={keyExists}
          onClick={handleSubmit}
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
- The Generate QR Code button is disabled when `keyExists` is `true` — re-provisioning is not permitted in standalone. There is no OTP gate to unlock it (contrast with MCC, where the OTP gate enables re-provisioning).
- The `generateQrCode` utility (in `lib/services`) converts the PNG `arraybuffer` response to a blob object URL using `URL.createObjectURL`. `useMFATotpForm` revokes the previous object URL whenever `imageSrc` changes and on unmount via a `useEffect` cleanup — callers do not need to handle this.

---

## Recipe 6: MFA Setup Prompt

**Goal**: Show a modal if the user has not completed MFA setup. Prompt them to navigate to the MFA settings page.

**How it works in the reference**: `useMfaPrompt` calls `GET /mfa/requirePinAndTotpSetup` on mount. `MFAPrompt` renders the advisory dialog with a link to `/settings/mfa`.

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
  const url = "mfa/requirePinAndTotpSetup";
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

- This endpoint MUST be `requirePinAndTotpSetup` — calling the MCC endpoint causes the prompt to appear or not appear incorrectly for users in a standalone deployment.
- The prompt is advisory only — users may dismiss with "Close" without setting up MFA. Critical transaction enforcement is handled backend-side.

---

## Recipe 7: Composing the MFA Settings Page

**Goal**: Show how `SetupMFA` combines `MFAPinForm` and `MFATotpForm` into the full MFA settings view for standalone.

```tsx
import MFAPinForm from "./_components/mfa-pin-form";
import MFATotpForm from "./_components/mfa-totp-form";
import useMFAPinForm from "./_hooks/use-mfa-pin-form";
import useMFATotpForm from "./_hooks/use-mfa-totp-form";

export default function SetupMFA() {
  const {
    pinForm,
    handlePinFormSubmit,
    showSpinner,
    isSetupAllowed,
    pinChangeSuccess,
  } = useMFAPinForm();

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

  // In standalone, QR generation calls the backend directly — no OTP gate
  const handleGenerateQr = () => viewQrCallback("", null);

  return (
    <div className="flex justify-center items-center mt-10">
      <MFAPinForm
        pinForm={pinForm}
        handlePinFormSubmit={handlePinFormSubmit}
        showSpinner={showSpinner}
        isSetupAllowed={isSetupAllowed}
        pinChangeSuccess={pinChangeSuccess}
      />
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
    </div>
  );
}
```

### Validation Rules

- `viewQrCallback` is called directly with an empty OTP string — the standalone QR endpoint ignores any OTP header.
- `useMFA` is NOT used on this page. It is only used on pages that perform critical transactions (e.g., forms that need `X-PIN` or `X-TOTP` headers). See Recipe 4.
- No `MFADialog` is rendered on the settings page in standalone — there is no OTP gate for TOTP provisioning.

---

## Implementation Notes: Additional Detail Beyond the Standard

The following patterns appear in these recipes but are not defined by the MFA Frontend Standalone Standard. They are implementation choices made in the reference codebase.

**`input-otp` library and `InputOTP` component**
The digit-slot OTP input is built on the `input-otp` library. The `REGEXP_ONLY_DIGITS` pattern restricts input to numeric characters. The standard requires digit-only input but does not prescribe a specific library. Implementors may use any equivalent input component.

**`react-hook-form` + Zod for PIN validation**
The PIN form uses `react-hook-form` with `zodResolver` for declarative validation. The standard requires `^\d{6}$` enforcement before submit; the choice of form library is up to the implementor.

**`mfaTypeRef` as a `useRef`**
`useMFA` stores `mfaType` in a `ref` rather than state because changes to it should not trigger a re-render of the dialog mid-interaction. Reading `mfaTypeRef.current` in `mfaDialogProps` is safe because `mfaDialogProps` is re-evaluated on every render.

**`generateQrCode` PNG-to-object-URL conversion**
The `generateQrCode` service function in `lib/services` converts the PNG `arraybuffer` response to a blob object URL using `URL.createObjectURL`. `useMFATotpForm` revokes the previous object URL whenever `imageSrc` changes and on unmount via a `useEffect` cleanup — callers do not need to handle this.
