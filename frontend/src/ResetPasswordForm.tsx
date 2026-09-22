import { useState, type FormEvent } from "react";
import { ApiError, confirmPasswordReset } from "./api/client";

const MIN_PASSWORD_LENGTH = 12;

interface ResetPasswordFormProps {
  onResetComplete: () => void;
}

type SubmitState =
  | { kind: "idle" }
  | { kind: "submitting" }
  | { kind: "success"; message: string }
  | { kind: "error"; message: string; details: string[] };

function tokenFromUrl(): string {
  return new URLSearchParams(window.location.search).get("token") ?? "";
}

export function ResetPasswordForm({ onResetComplete }: ResetPasswordFormProps) {
  const [token, setToken] = useState(tokenFromUrl);
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

  return (
    <section>
      <h2>Reset password</h2>
      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="reset-token">Reset token</label>
          <input
            id="reset-token"
            name="token"
            type="text"
            required
            value={token}
            onChange={(e) => setToken(e.target.value)}
            placeholder="From your password reset email"
          />
        </div>

        <div>
          <label htmlFor="reset-new-password">New password</label>
          <input
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
          <p id="reset-password-hint">At least {MIN_PASSWORD_LENGTH} characters.</p>
        </div>

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Resetting…" : "Reset password"}
        </button>
      </form>

      {state.kind === "success" && (
        <div>
          <p role="status">{state.message}</p>
          <button type="button" onClick={onResetComplete}>
            Back to log in
          </button>
        </div>
      )}

      {state.kind === "error" && (
        <div role="alert" style={{ color: "crimson" }}>
          <p>{state.message}</p>
          {state.details.length > 0 && (
            <ul>
              {state.details.map((detail) => (
                <li key={detail}>{detail}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  );
}
