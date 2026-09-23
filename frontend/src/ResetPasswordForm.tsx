import { useState, type FormEvent } from "react";
import { ApiError, confirmPasswordReset } from "./api/client";

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
        <h2>Reset password</h2>
        <p className="alert alert-success" role="status">
          {state.message}
        </p>
        <button type="button" className="btn-primary" onClick={onResetComplete}>
          Back to log in
        </button>
      </>
    );
  }

  return (
    <>
      <h2>Reset password</h2>
      <form onSubmit={handleSubmit}>
        <div className="field">
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

        <div className="field">
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
          <p id="reset-password-hint" className="field-hint">
            At least {MIN_PASSWORD_LENGTH} characters.
          </p>
        </div>

        {state.kind === "error" && (
          <div className="alert alert-error" role="alert">
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

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Resetting…" : "Reset password"}
        </button>
      </form>
    </>
  );
}
