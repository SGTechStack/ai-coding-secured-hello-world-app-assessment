import { useState } from "react";
import { confirmPasswordReset } from "../api/client.js";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription } from "@/components/ui/alert";

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
      <Alert data-testid="reset-password-success">
        <AlertDescription>
          Your password has been reset. You can now sign in with your new password.
        </AlertDescription>
      </Alert>
    );
  }

  return (
    <form
      onSubmit={handleSubmit}
      aria-label="reset password"
      className="flex flex-col gap-4"
    >
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="reset-new-password">New password</Label>
        <Input
          id="reset-new-password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          name="newPassword"
          type="password"
          autoComplete="new-password"
        />
      </div>
      {error && (
        <Alert variant="destructive" data-testid="reset-password-error">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      <Button type="submit" className="w-full">
        Set new password
      </Button>
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
