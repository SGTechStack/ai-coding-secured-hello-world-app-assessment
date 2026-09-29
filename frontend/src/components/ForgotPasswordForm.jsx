import { useState } from "react";
import { requestPasswordReset } from "../api/client.js";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription } from "@/components/ui/alert";

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
      <Alert data-testid="forgot-password-success" className="mt-3">
        <AlertDescription>
          If an account exists for that email, a reset link has been sent.
        </AlertDescription>
      </Alert>
    );
  }

  return (
    <form
      onSubmit={handleSubmit}
      aria-label="forgot password"
      className="mt-3 flex flex-col gap-3"
    >
      <div className="flex flex-col gap-1">
        <Label htmlFor="forgot-email">Email</Label>
        <Input
          id="forgot-email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          name="email"
          type="email"
          autoComplete="email"
        />
      </div>
      {error && (
        <Alert variant="destructive" data-testid="forgot-password-error">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      <Button type="submit" variant="secondary" className="w-full">
        Send reset link
      </Button>
    </form>
  );
}
