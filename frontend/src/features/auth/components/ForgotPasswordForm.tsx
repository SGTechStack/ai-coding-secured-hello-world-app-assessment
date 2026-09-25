import { useState, type FormEvent } from "react";

import { Button } from "@/common/components/ui/button";
import { Input } from "@/common/components/ui/input";
import { Label } from "@/common/components/ui/label";
import { Alert, AlertDescription } from "@/common/components/ui/alert";
import { ApiError } from "@/common/api/http";
import { requestPasswordReset } from "../api";

interface ForgotPasswordFormProps {
  onBackToLogin: () => void;
  onHaveToken: () => void;
}

type SubmitState =
  | { kind: "idle" }
  | { kind: "submitting" }
  | { kind: "success"; message: string }
  | { kind: "error"; message: string };

export function ForgotPasswordForm({ onBackToLogin, onHaveToken }: ForgotPasswordFormProps) {
  const [email, setEmail] = useState("");
  const [state, setState] = useState<SubmitState>({ kind: "idle" });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setState({ kind: "submitting" });

    try {
      const message = await requestPasswordReset(email);
      setState({ kind: "success", message });
    } catch (error: unknown) {
      setState({
        kind: "error",
        message:
          error instanceof ApiError
            ? error.message
            : error instanceof Error
              ? error.message
              : "Request failed",
      });
    }
  }

  const isSubmitting = state.kind === "submitting";

  return (
    <>
      <h2 className="mb-4 text-[1.15rem] font-semibold">Forgot password</h2>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="forgot-email">Email</Label>
          <Input
            id="forgot-email"
            name="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>

        {state.kind === "success" && (
          <Alert variant="success">
            <AlertDescription>{state.message}</AlertDescription>
          </Alert>
        )}

        {state.kind === "error" && (
          <Alert variant="destructive">
            <AlertDescription>{state.message}</AlertDescription>
          </Alert>
        )}

        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Sending…" : "Send reset link"}
        </Button>
      </form>

      <div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-center text-[0.85rem] text-muted-foreground">
        <Button type="button" variant="link" className="h-auto p-0" onClick={onBackToLogin}>
          Back to log in
        </Button>
        {state.kind === "success" && (
          <Button type="button" variant="link" className="h-auto p-0" onClick={onHaveToken}>
            I have a reset token
          </Button>
        )}
      </div>
    </>
  );
}
