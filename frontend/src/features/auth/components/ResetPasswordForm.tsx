import { useState, type FormEvent } from "react";

import { Button } from "@/common/components/ui/button";
import { Input } from "@/common/components/ui/input";
import { Label } from "@/common/components/ui/label";
import { Alert, AlertDescription } from "@/common/components/ui/alert";
import { ApiError } from "@/common/api/http";
import { confirmPasswordReset } from "../api";

const MIN_PASSWORD_LENGTH = 12;

interface ResetPasswordFormProps {
  /**
   * Token captured from the reset link by the parent, or `""` when the user
   * navigated here manually and will paste one in. The parent owns the URL
   * read so the token can be stripped from the address bar immediately.
   */
  initialToken: string;
  onResetComplete: () => void;
}

type SubmitState =
  | { kind: "idle" }
  | { kind: "submitting" }
  | { kind: "success"; message: string }
  | { kind: "error"; message: string; details: string[] };

export function ResetPasswordForm({ initialToken, onResetComplete }: ResetPasswordFormProps) {
  const [token, setToken] = useState(initialToken);
  const [newPassword, setNewPassword] = useState("");
  const [state, setState] = useState<SubmitState>({ kind: "idle" });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setState({ kind: "submitting" });

    try {
      const message = await confirmPasswordReset(token, newPassword);
      setState({ kind: "success", message });
    } catch (error: unknown) {
      if (error instanceof ApiError) {
        setState({ kind: "error", message: error.message, details: error.details });
      } else {
        setState({
          kind: "error",
          message: error instanceof Error ? error.message : "Reset failed",
          details: [],
        });
      }
    }
  }

  const isSubmitting = state.kind === "submitting";

  if (state.kind === "success") {
    return (
      <>
        <h2 className="mb-4 text-[1.15rem] font-semibold">Reset password</h2>
        <Alert variant="success" className="mb-4">
          <AlertDescription>{state.message}</AlertDescription>
        </Alert>
        <Button type="button" onClick={onResetComplete}>
          Back to log in
        </Button>
      </>
    );
  }

  return (
    <>
      <h2 className="mb-4 text-[1.15rem] font-semibold">Reset password</h2>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="reset-token">Reset token</Label>
          <Input
            id="reset-token"
            name="token"
            type="text"
            required
            value={token}
            onChange={(e) => setToken(e.target.value)}
            placeholder="From your password reset email"
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="reset-new-password">New password</Label>
          <Input
            id="reset-new-password"
            name="newPassword"
            type="password"
            autoComplete="new-password"
            required
            minLength={MIN_PASSWORD_LENGTH}
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            aria-describedby="reset-password-hint"
          />
          <p id="reset-password-hint" className="text-[0.78rem] text-muted-foreground">
            At least {MIN_PASSWORD_LENGTH} characters.
          </p>
        </div>

        {state.kind === "error" && (
          <Alert variant="destructive">
            <AlertDescription>
              <p>{state.message}</p>
              {state.details.length > 0 && (
                <ul className="ml-4 list-disc">
                  {state.details.map((detail) => (
                    <li key={detail}>{detail}</li>
                  ))}
                </ul>
              )}
            </AlertDescription>
          </Alert>
        )}

        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Resetting…" : "Reset password"}
        </Button>
      </form>
    </>
  );
}
