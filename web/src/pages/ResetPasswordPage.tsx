import { useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import * as authApi from "../api/auth.api";
import { Alert } from "../components/ui/Alert";
import { Button } from "../components/ui/Button";
import { Card, CenteredPage } from "../components/ui/Card";
import { FormField } from "../components/ui/FormField";
import { fieldErrorsOf, toMessage } from "../lib/errors";
import { PASSWORD_HINT, describePasswordProblem } from "../lib/password-policy";

/** Story 7 — setting a new password with a reset token. */
export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  // The token comes from the emailed link. Kept in the URL and never stored anywhere, so closing the
  // tab is enough to be rid of it.
  const token = searchParams.get("token") ?? "";

  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);

    if (password !== confirmation) {
      setFieldErrors({ confirmation: "The two passwords do not match." });
      return;
    }
    const passwordProblem = describePasswordProblem(password);
    if (passwordProblem) {
      setFieldErrors({ password: passwordProblem });
      return;
    }
    setFieldErrors({});
    setSubmitting(true);
    try {
      await authApi.confirmPasswordReset({ token, password });
      navigate("/login", {
        replace: true,
        state: { notice: "Your password has been changed. Log in with your new password." },
      });
    } catch (caught) {
      setError(toMessage(caught, "Could not change your password."));
      setFieldErrors(fieldErrorsOf(caught));
    } finally {
      setSubmitting(false);
    }
  }

  if (!token) {
    return (
      <CenteredPage>
        <Card title="Reset your password">
          <Alert tone="error">
            This link is missing its reset token. Request a new link and use the one from the email.
          </Alert>
          <p className="mt-4 text-sm text-ink-muted">
            <Link to="/forgot-password" className="text-accent underline hover:text-accent-hover">
              Request a new link
            </Link>
          </p>
        </Card>
      </CenteredPage>
    );
  }

  return (
    <CenteredPage>
      <Card title="Choose a new password">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          {error ? <Alert tone="error">{error}</Alert> : null}
          <FormField
            label="New password"
            name="password"
            type="password"
            autoComplete="new-password"
            required
            value={password}
            hint={PASSWORD_HINT}
            error={fieldErrors.password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <FormField
            label="Confirm new password"
            name="confirmation"
            type="password"
            autoComplete="new-password"
            required
            value={confirmation}
            error={fieldErrors.confirmation}
            onChange={(event) => setConfirmation(event.target.value)}
          />
          <Button type="submit" busy={submitting} busyLabel="Changing your password">
            Change password
          </Button>
        </form>
        <p className="mt-4 text-sm text-ink-muted">
          Changing your password signs you out everywhere, on every device.
        </p>
      </Card>
    </CenteredPage>
  );
}
