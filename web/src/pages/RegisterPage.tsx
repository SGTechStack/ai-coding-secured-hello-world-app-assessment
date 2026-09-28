import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import * as authApi from "../api/auth.api";
import { Alert } from "../components/ui/Alert";
import { Button } from "../components/ui/Button";
import { Card, CenteredPage } from "../components/ui/Card";
import { FormField } from "../components/ui/FormField";
import { fieldErrorsOf, toMessage } from "../lib/errors";
import { PASSWORD_HINT, describePasswordProblem } from "../lib/password-policy";

/** Story 1 — registering an account. */
export function RegisterPage() {
  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);

    // Checked here purely to save a round trip. The server checks again and its answer governs.
    const passwordProblem = describePasswordProblem(password);
    if (passwordProblem) {
      setFieldErrors({ password: passwordProblem });
      return;
    }
    setFieldErrors({});
    setSubmitting(true);
    try {
      await authApi.register({ username, email, password });
      // No session was created, so there is nowhere to land but the login page. That is decision 6:
      // registration creates an account and story 2 owns creating sessions.
      navigate("/login", {
        replace: true,
        state: { notice: "Account created. Log in to continue." },
      });
    } catch (caught) {
      setError(toMessage(caught, "Could not create your account."));
      setFieldErrors(fieldErrorsOf(caught));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <CenteredPage>
      <Card title="Create an account">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          {error ? <Alert tone="error">{error}</Alert> : null}
          <FormField
            label="Username"
            name="username"
            autoComplete="username"
            required
            minLength={3}
            maxLength={64}
            value={username}
            error={fieldErrors.username}
            onChange={(event) => setUsername(event.target.value)}
          />
          <FormField
            label="Email"
            name="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            hint="Used only to reset your password."
            error={fieldErrors.email}
            onChange={(event) => setEmail(event.target.value)}
          />
          <FormField
            label="Password"
            name="password"
            type="password"
            autoComplete="new-password"
            required
            value={password}
            hint={PASSWORD_HINT}
            error={fieldErrors.password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <Button type="submit" busy={submitting} busyLabel="Creating your account">
            Create account
          </Button>
        </form>
        <p className="mt-4 text-sm text-ink-muted">
          Already registered?{" "}
          <Link to="/login" className="text-accent underline hover:text-accent-hover">
            Log in
          </Link>
        </p>
      </Card>
    </CenteredPage>
  );
}
