import { useState } from "react";
import { requestPasswordReset } from "../api/client.js";

/**
 * "Forgot password" form. Submits an email and shows a generic confirmation on
 * success. The confirmation is intentionally the same regardless of whether the
 * email is registered — the backend enforces the enumeration-resistant response
 * and this UI never claims the address was found.
 */
export default function ForgotPasswordForm() {
  const [email, setEmail] = useState("");
  const [error, setError] = useState("");
  const [submitted, setSubmitted] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    try {
      await requestPasswordReset({ email });
      setSubmitted(true);
    } catch (err) {
      setError(err.message);
    }
  }

  if (submitted) {
    return (
      <p data-testid="forgot-password-success">
        If an account exists for that email, a reset link has been sent.
      </p>
    );
  }

  return (
    <form onSubmit={handleSubmit} aria-label="forgot password">
      <label>
        Email
        <input
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          name="email"
          type="email"
        />
      </label>
      {error && <p role="alert" data-testid="forgot-password-error">{error}</p>}
      <button type="submit">Send reset link</button>
    </form>
  );
}
