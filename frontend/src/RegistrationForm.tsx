import { useState, type FormEvent } from "react";
import { ApiError, register, type RegistrationResponse } from "./api/client";
import { PrivacyNoticeSummary } from "./PrivacyNoticeSummary";

const MIN_PASSWORD_LENGTH = 12;

type SubmitState =
  | { kind: "idle" }
  | { kind: "submitting" }
  | { kind: "success"; data: RegistrationResponse }
  | { kind: "error"; message: string; details: string[] };

interface RegistrationFormProps {
  /** Called once the account has been created, so the parent can move the user off the form (e.g. to login). */
  onRegistered?: (data: RegistrationResponse) => void;
}

export function RegistrationForm({ onRegistered }: RegistrationFormProps) {
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
      onRegistered?.(data);
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

  if (state.kind === "success") {
    return (
      <div className="alert alert-success" role="status">
        <p>
          Account <strong>{state.data.username}</strong> created successfully.
        </p>
        <p>You can now log in with your new credentials.</p>
      </div>
    );
  }

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
          // Mirrors the server's allow-list so the rejection arrives before the
          // round trip. Convenience only — the server validates independently,
          // because this attribute is trivially bypassed and the reason for the
          // restriction (a username ends up in audit records, where a newline
          // would let its owner forge log lines) is a server-side concern.
          pattern="[A-Za-z0-9._\-]+"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          aria-describedby="username-hint"
        />
        <p id="username-hint" className="field-hint">
          Letters, digits, dots, underscores and hyphens.
        </p>
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

      {/*
        Above the submit button, not below it and not on a separate page. This is
        the point of collection: the person deciding whether to hand over an email
        address has to be able to read what it is for before they decide, not
        after.
      */}
      <PrivacyNoticeSummary />

      <button type="submit" disabled={isSubmitting}>
        {isSubmitting ? "Registering…" : "Register"}
      </button>
    </form>
  );
}
