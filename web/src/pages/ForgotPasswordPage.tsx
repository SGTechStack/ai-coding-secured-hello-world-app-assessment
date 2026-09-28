import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import * as authApi from "../api/auth.api";
import { Alert } from "../components/ui/Alert";
import { Button } from "../components/ui/Button";
import { Card, CenteredPage } from "../components/ui/Card";
import { FormField } from "../components/ui/FormField";
import { toMessage } from "../lib/errors";

/** Story 6 — asking for a password reset. */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setNotice(null);
    setSubmitting(true);
    try {
      const response = await authApi.requestPasswordReset(email);
      // The server's message is identical whether or not the address is registered, and this page
      // repeats it verbatim. Adding "check your inbox" versus "no such account" here would leak
      // exactly what the endpoint was built not to.
      setNotice(response.message);
    } catch (caught) {
      setError(toMessage(caught, "Could not send a reset link."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <CenteredPage>
      <Card title="Reset your password">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          {notice ? <Alert tone="success">{notice}</Alert> : null}
          {error ? <Alert tone="error">{error}</Alert> : null}
          <FormField
            label="Email"
            name="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            hint="We will send a link if this address has an account."
            onChange={(event) => setEmail(event.target.value)}
          />
          <Button type="submit" busy={submitting} busyLabel="Sending the link">
            Send reset link
          </Button>
        </form>
        <p className="mt-4 text-sm text-ink-muted">
          In development no mail is sent — the reset link is written to the API's log.
        </p>
        <p className="mt-4 text-sm text-ink-muted">
          <Link to="/login" className="text-accent underline hover:text-accent-hover">
            Back to log in
          </Link>
        </p>
      </Card>
    </CenteredPage>
  );
}
