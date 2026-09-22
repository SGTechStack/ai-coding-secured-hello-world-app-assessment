import { useState, type FormEvent } from "react";
import { ApiError, login, type LoginResponse } from "./api/client";

interface LoginFormProps {
  onLoginSuccess: (result: LoginResponse) => void;
}

type SubmitState =
  | { kind: "idle" }
  | { kind: "submitting" }
  | { kind: "error"; message: string };

export function LoginForm({ onLoginSuccess }: LoginFormProps) {
  const [username, setUsername] = useState("");
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

  return (
    <form onSubmit={handleSubmit}>
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
