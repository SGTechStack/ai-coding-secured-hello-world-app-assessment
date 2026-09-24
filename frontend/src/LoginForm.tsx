import { useState, type FormEvent } from "react";
import { ApiError, login, type LoginResponse } from "./api/client";

interface LoginFormProps {
  onLoginSuccess: (result: LoginResponse) => void;
  /** Prefills the username field, e.g. right after a successful registration. */
  initialUsername?: string;
}

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

  // Username only. The dev admin password is generated per backend boot and
  // printed to the backend log, so there is no longer a fixed value to prefill
  // — which is the point: a password this panel could hardcode would be a
  // password an attacker could read here too.
  function fillDemoAdminUsername() {
    setUsername("admin");
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
                <code>admin</code>
              </dd>
            </div>
            <div className="demo-credentials-row">
              <dt>Password</dt>
              <dd>
                generated per backend restart — copy it from the backend console
              </dd>
            </div>
          </dl>
          <button type="button" className="btn-link demo-credentials-fill" onClick={fillDemoAdminUsername}>
            Fill in demo username
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
