import { useEffect, useState } from "react";
import { fetchHealth, type HealthResponse, type LoginResponse } from "./api/client";
import { RegistrationForm } from "./RegistrationForm";
import { LoginForm } from "./LoginForm";
import { ProtectedGreeting } from "./ProtectedGreeting";
import { ForgotPasswordForm } from "./ForgotPasswordForm";
import { ResetPasswordForm } from "./ResetPasswordForm";

type Status =
  | { kind: "loading" }
  | { kind: "success"; data: HealthResponse }
  | { kind: "error"; message: string };

type View = "login" | "forgot-password" | "reset-password";

function initialView(): View {
  const params = new URLSearchParams(window.location.search);
  return params.has("token") ? "reset-password" : "login";
}

function App() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [loggedInAs, setLoggedInAs] = useState<LoginResponse | null>(null);
  const [view, setView] = useState<View>(initialView);

  useEffect(() => {
    let isMounted = true;

    fetchHealth()
      .then((data) => {
        if (isMounted) setStatus({ kind: "success", data });
      })
      .catch((error: unknown) => {
        if (isMounted) {
          setStatus({
            kind: "error",
            message: error instanceof Error ? error.message : "Unknown error",
          });
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <main className="app">
      <h1>Hello World Auth App</h1>
      <p>Frontend origin talking to the backend across origins.</p>

      {status.kind === "loading" && <p role="status">Checking backend health…</p>}

      {status.kind === "success" && (
        <p role="status">
          Backend says: <strong>{status.data.status}</strong> (as of{" "}
          {status.data.timestamp})
        </p>
      )}

      {status.kind === "error" && (
        <p role="alert" style={{ color: "crimson" }}>
          Could not reach backend: {status.message}
        </p>
      )}

      {loggedInAs && (
        <ProtectedGreeting
          username={loggedInAs.username}
          role={loggedInAs.role}
          onLoggedOut={() => {
            setLoggedInAs(null);
            setView("login");
          }}
        />
      )}

      {!loggedInAs && view === "login" && (
        <>
          <LoginForm onLoginSuccess={setLoggedInAs} />
          <button type="button" onClick={() => setView("forgot-password")}>
            Forgot password?
          </button>
          <RegistrationForm />
        </>
      )}

      {!loggedInAs && view === "forgot-password" && (
        <ForgotPasswordForm
          onBackToLogin={() => setView("login")}
          onHaveToken={() => setView("reset-password")}
        />
      )}

      {!loggedInAs && view === "reset-password" && (
        <ResetPasswordForm onResetComplete={() => setView("login")} />
      )}
    </main>
  );
}

export default App;
