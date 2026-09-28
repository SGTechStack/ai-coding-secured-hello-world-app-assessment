import { useState } from "react";
import { confirmPasswordReset } from "../api/client.js";

/** Minimum new-password length, mirrored from the backend strength policy. */
const MIN_PASSWORD_LENGTH = 12;

/**
 * "Set new password" form (Story 7). Completes a password reset with a token and
 * a new password. The token comes from the `token` prop, falling back to the
 * `?token=` query parameter so the form works when opened straight from the
 * reset link.
 *
 * <p>On success it shows a confirmation; on failure (invalid/expired/used token,
 * or a policy-too-short password the backend rejects) it surfaces the backend's
 * generic message. A too-short password is also caught client-side before the
 * request to give immediate feedback.
 */
export default function ResetPasswordForm({ token: tokenProp }) {
  const token = tokenProp ?? tokenFromQuery();
  const [newPassword, setNewPassword] = useState("");
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");

    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      setError(`Password must be at least ${MIN_PASSWORD_LENGTH} characters`);
      return;
    }

    try {
      await confirmPasswordReset({ token, newPassword });
      setDone(true);
    } catch (err) {
      setError(err.message);
    }
  }

  if (done) {
    return (
      <p data-testid="reset-password-success">
        Your password has been reset. You can now sign in with your new password.
      </p>
    );
  }

  return (
    <form onSubmit={handleSubmit} aria-label="reset password">
      <label>
        New password
        <input
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          name="newPassword"
          type="password"
        />
      </label>
      {error && <p role="alert" data-testid="reset-password-error">{error}</p>}
      <button type="submit">Set new password</button>
    </form>
  );
}

/** Reads the `token` query parameter from the current URL (empty when absent). */
function tokenFromQuery() {
  if (typeof window === "undefined" || !window.location) {
    return "";
  }
  return new URLSearchParams(window.location.search).get("token") ?? "";
}
