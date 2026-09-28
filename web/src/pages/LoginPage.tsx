import { useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { Alert } from "../components/ui/Alert";
import { Button } from "../components/ui/Button";
import { Card, CenteredPage } from "../components/ui/Card";
import { FormField } from "../components/ui/FormField";
import { toMessage } from "../lib/errors";

interface LocationState {
  from?: string;
  notice?: string;
}

/** Story 2 — logging in. */
export function LoginPage() {
  const { logIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const state = (location.state ?? {}) as LocationState;

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await logIn({ username, password });
      navigate(state.from ?? "/", { replace: true });
    } catch (caught) {
      // Whatever the real reason — wrong password, unknown account, locked, disabled — the server
      // gives one answer, and this page shows it unchanged. Guessing at a more specific message here
      // would undo the enumeration resistance the server went to the trouble of providing.
      setError(toMessage(caught, "Could not log you in."));
      setPassword("");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <CenteredPage>
      <Card title="Log in">
        {state.notice ? <Alert tone="success">{state.notice}</Alert> : null}
        <form onSubmit={handleSubmit} className="mt-4 flex flex-col gap-4" noValidate>
          {error ? <Alert tone="error">{error}</Alert> : null}
          <FormField
            label="Username"
            name="username"
            autoComplete="username"
            required
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
          <FormField
            label="Password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <Button type="submit" busy={submitting} busyLabel="Logging in">
            Log in
          </Button>
        </form>
        <p className="mt-4 text-sm text-ink-muted">
          <Link to="/forgot-password" className="text-accent underline hover:text-accent-hover">
            Forgot your password?
          </Link>
        </p>
        <p className="mt-1 text-sm text-ink-muted">
          No account yet?{" "}
          <Link to="/register" className="text-accent underline hover:text-accent-hover">
            Register
          </Link>
        </p>
      </Card>
    </CenteredPage>
  );
}
