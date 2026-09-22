import { useState, type FormEvent } from "react";
import { ApiError, register, type RegistrationResponse } from "./api/client";

const MIN_PASSWORD_LENGTH = 12;

type SubmitState =
  | { kind: "idle" }
  | { kind: "submitting" }
  | { kind: "success"; data: RegistrationResponse }
  | { kind: "error"; message: string; details: string[] };

export function RegistrationForm() {
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [state, setState] = useState<SubmitState>({ kind: "idle" });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setState({ kind: "submitting" });

    try {
      const data = await register({ username, email, password });
      setState({ kind: "success", data });
      setUsername("");
      setEmail("");
      setPassword("");
    } catch (error: unknown) {
      if (error instanceof ApiError) {
        setState({ kind: "error", message: error.message, details: error.details });
      } else {
        setState({
          kind: "error",
          message: error instanceof Error ? error.message : "Registration failed",
          details: [],
        });
      }
    }
  }

  const isSubmitting = state.kind === "submitting";

  return (
    <form onSubmit={handleSubmit}>
      <div className="field">
        <label htmlFor="username">Username</label>
        <input
          id="username"
          name="username"
          type="text"
          autoComplete="username"
          required
          minLength={3}
          maxLength={64}
          value={username}
          onChange={(e) => setUsername(e.target.value)}
        />
      </div>

      <div className="field">
        <label htmlFor="email">Email</label>
        <input
          id="email"
          name="email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
      </div>

      <div className="field">
        <label htmlFor="password">Password</label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="new-password"
          required
          minLength={MIN_PASSWORD_LENGTH}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          aria-describedby="password-hint"
        />
        <p id="password-hint" className="field-hint">
          At least {MIN_PASSWORD_LENGTH} characters.
        </p>
      </div>

      {state.kind === "success" && (
        <p className="alert alert-success" role="status">
          Account <strong>{state.data.username}</strong> created. You can now log in.
        </p>
      )}

      {state.kind === "error" && (
        <div className="alert alert-error" role="alert">
          <p>{state.message}</p>
          {state.details.length > 0 && (
            <ul>
              {state.details.map((detail) => (
                <li key={detail}>{detail}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      <button type="submit" disabled={isSubmitting}>
        {isSubmitting ? "Registering…" : "Register"}
      </button>
    </form>
  );
}
