import { useState, type FormEvent } from "react";
import { ApiError, login, type LoginResponse } from "./api/client";

interface LoginFormProps {
  onLoginSuccess: (result: LoginResponse) => void;
  /** Prefills the username field, e.g. right after a successful registration. */
  initialUsername?: string;
}

/**
 * Must match the `dev` profile defaults in `application.yml`
 * (`app.admin.username` / `app.admin.password`). Nothing enforces that at build
 * time, so if the dev default changes, change it here too — a panel that shows a
 * stale credential is worse than one that shows none, because it sends people
 * looking for a problem that is not there.
 */
const DEMO_ADMIN_USERNAME = "admin";
const DEMO_ADMIN_PASSWORD = "password1234";

type SubmitState =
  | { kind: "idle" }
  | { kind: "submitting" }
  | { kind: "error"; message: string };

export function LoginForm({ onLoginSuccess, initialUsername }: LoginFormProps) {
  const [username, setUsername] = useState(initialUsername ?? "");
  const [password, setPassword] = useState("");
  const [state, setState] = useState<SubmitState>({ kind: "idle" });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setState({ kind: "submitting" });

    try {
      const result = await login({ username, password });
      setPassword("");
      onLoginSuccess(result);
    } catch (error: unknown) {
      setState({
        kind: "error",
        message:
          error instanceof ApiError
            ? error.message
            : error instanceof Error
              ? error.message
              : "Login failed",
      });
    }
  }

  const isSubmitting = state.kind === "submitting";

  /**
   * The dev admin credential, fixed by product decision (ADR 0008).
   *
   * Displaying it here adds no exposure worth avoiding: the same value is in
   * `application.yml` and the README, so withholding it from this panel would be
   * inconvenience rather than protection. What it does cost is recorded honestly —
   * this is the TM-07 credential, so any instance running the dev profile on a
   * reachable address is a one-guess admin takeover unless it overrides
   * `APP_ADMIN_PASSWORD`.
   *
   * Gated on `import.meta.env.DEV`, so a production build never contains it.
   */
  function fillDemoAdminCredentials() {
    setUsername(DEMO_ADMIN_USERNAME);
    setPassword(DEMO_ADMIN_PASSWORD);
  }

  return (
    <form onSubmit={handleSubmit}>
      {import.meta.env.DEV && (
        <div className="demo-credentials" data-testid="demo-admin-credentials">
          <div className="demo-credentials-header">
            <span className="dev-banner-tag">DEV</span>
            <span className="demo-credentials-title">Demo admin login</span>
          </div>
          <dl className="demo-credentials-list">
            <div className="demo-credentials-row">
              <dt>Username</dt>
              <dd>
                <code>{DEMO_ADMIN_USERNAME}</code>
              </dd>
            </div>
            <div className="demo-credentials-row">
              <dt>Password</dt>
              <dd>
                <code>{DEMO_ADMIN_PASSWORD}</code>
              </dd>
            </div>
          </dl>
          <button type="button" className="btn-link demo-credentials-fill" onClick={fillDemoAdminCredentials}>
            Fill in demo credentials
          </button>
        </div>
      )}

      <div className="field">
        <label htmlFor="login-username">Username</label>
        <input
          id="login-username"
          name="username"
          type="text"
          autoComplete="username"
          required
          value={username}
          onChange={(e) => setUsername(e.target.value)}
        />
      </div>

      <div className="field">
        <label htmlFor="login-password">Password</label>
        <input
          id="login-password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </div>

      {state.kind === "error" && (
        <p className="alert alert-error" role="alert">
          {state.message}
        </p>
      )}

      <button type="submit" disabled={isSubmitting}>
        {isSubmitting ? "Logging in…" : "Log in"}
      </button>
    </form>
  );
}
