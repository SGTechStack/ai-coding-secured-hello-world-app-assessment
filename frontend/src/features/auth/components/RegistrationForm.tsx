import { useState, type FormEvent } from "react";

import { Button } from "@/common/components/ui/button";
import { Input } from "@/common/components/ui/input";
import { Label } from "@/common/components/ui/label";
import { Alert, AlertDescription } from "@/common/components/ui/alert";
import { ApiError } from "@/common/api/http";
import { PrivacyNoticeSummary } from "@/features/privacy/components/PrivacyNoticeSummary";
import { register, type RegistrationResponse } from "../api";

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
      <Alert variant="success">
        <AlertDescription>
          <p>
            Account <strong>{state.data.username}</strong> created successfully.
          </p>
          <p>You can now log in with your new credentials.</p>
        </AlertDescription>
      </Alert>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="username">Username</Label>
        <Input
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
        <p id="username-hint" className="text-[0.78rem] text-muted-foreground">
          Letters, digits, dots, underscores and hyphens.
        </p>
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="email">Email</Label>
        <Input
          id="email"
          name="email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="password">Password</Label>
        <Input
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
        <p id="password-hint" className="text-[0.78rem] text-muted-foreground">
          At least {MIN_PASSWORD_LENGTH} characters.
        </p>
      </div>

      {state.kind === "error" && (
        <Alert variant="destructive">
          <AlertDescription>
            <p>{state.message}</p>
            {state.details.length > 0 && (
              <ul className="ml-4 list-disc">
                {state.details.map((detail) => (
                  <li key={detail}>{detail}</li>
                ))}
              </ul>
            )}
          </AlertDescription>
        </Alert>
      )}

      {/*
        Above the submit button, not below it and not on a separate page. This is
        the point of collection: the person deciding whether to hand over an email
        address has to be able to read what it is for before they decide, not
        after.
      */}
      <PrivacyNoticeSummary />

      <Button type="submit" disabled={isSubmitting}>
        {isSubmitting ? "Registering…" : "Register"}
      </Button>
    </form>
  );
}
