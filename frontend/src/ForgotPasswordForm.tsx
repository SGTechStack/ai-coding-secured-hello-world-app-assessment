import { useState, type FormEvent } from "react";
import { ApiError, requestPasswordReset } from "./api/client";

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
      <h2>Forgot password</h2>
      <form onSubmit={handleSubmit}>
        <div className="field">
          <label htmlFor="forgot-email">Email</label>
          <input
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
          <p className="alert alert-success" role="status">
            {state.message}
          </p>
        )}

        {state.kind === "error" && (
          <p className="alert alert-error" role="alert">
            {state.message}
          </p>
        )}

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Sending…" : "Send reset link"}
        </button>
      </form>

      <div className="form-footer btn-row">
        <button type="button" className="btn-link" onClick={onBackToLogin}>
          Back to log in
        </button>
        {state.kind === "success" && (
          <button type="button" className="btn-link" onClick={onHaveToken}>
            I have a reset token
          </button>
        )}
      </div>
    </>
  );
}
