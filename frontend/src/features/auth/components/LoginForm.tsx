import { useState, type FormEvent } from "react";

import { Button } from "@/common/components/ui/button";
import { Input } from "@/common/components/ui/input";
import { Label } from "@/common/components/ui/label";
import { Alert, AlertDescription } from "@/common/components/ui/alert";
import { Badge } from "@/common/components/ui/badge";
import { ApiError } from "@/common/api/http";
import { login, type LoginResponse } from "../api";

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
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      {import.meta.env.DEV && (
        <div
          className="flex flex-col gap-2.5 rounded-md border border-amber-300 bg-amber-50 p-3.5 dark:border-amber-900 dark:bg-amber-950"
          data-testid="demo-admin-credentials"
        >
          <div className="flex items-center gap-2">
            <Badge className="border-amber-900/10 bg-amber-900/10 text-[0.68rem] font-bold tracking-wide text-amber-900 dark:border-amber-200/15 dark:bg-amber-200/15 dark:text-amber-200">
              DEV
            </Badge>
            <span className="text-[0.82rem] font-semibold text-amber-900 dark:text-amber-200">
              Demo admin login
            </span>
          </div>
          <dl className="flex flex-col gap-1">
            <div className="flex items-baseline gap-2 text-[0.82rem]">
              <dt className="w-[4.5rem] shrink-0 font-semibold text-amber-800 dark:text-amber-300">Username</dt>
              <dd className="m-0">
                <code className="rounded bg-amber-900/10 px-1.5 py-0.5 text-[0.82rem] dark:bg-amber-200/10">
                  {DEMO_ADMIN_USERNAME}
                </code>
              </dd>
            </div>
            <div className="flex items-baseline gap-2 text-[0.82rem]">
              <dt className="w-[4.5rem] shrink-0 font-semibold text-amber-800 dark:text-amber-300">Password</dt>
              <dd className="m-0">
                <code className="rounded bg-amber-900/10 px-1.5 py-0.5 text-[0.82rem] dark:bg-amber-200/10">
                  {DEMO_ADMIN_PASSWORD}
                </code>
              </dd>
            </div>
          </dl>
          <Button
            type="button"
            variant="link"
            className="h-auto self-start p-0 text-[0.8rem]"
            onClick={fillDemoAdminCredentials}
          >
            Fill in demo credentials
          </Button>
        </div>
      )}

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="login-username">Username</Label>
        <Input
          id="login-username"
          name="username"
          type="text"
          autoComplete="username"
          required
          value={username}
          onChange={(e) => setUsername(e.target.value)}
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="login-password">Password</Label>
        <Input
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
        <Alert variant="destructive">
          <AlertDescription>{state.message}</AlertDescription>
        </Alert>
      )}

      <Button type="submit" disabled={isSubmitting}>
        {isSubmitting ? "Logging in…" : "Log in"}
      </Button>
    </form>
  );
}
