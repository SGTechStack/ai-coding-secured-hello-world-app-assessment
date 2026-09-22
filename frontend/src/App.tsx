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

type View = "login" | "register" | "forgot-password" | "reset-password";

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

  const isAuthView = view === "login" || view === "register";

  return (
    <main className="app">
      <div className="app-header">
        <h1>Hello World Auth App</h1>
        <p>A secure username/password reference implementation.</p>

        {status.kind === "loading" && (
          <span className="status-banner">
            <span className="dot" />
            Checking backend…
          </span>
        )}

        {status.kind === "success" && (
          <span className="status-banner is-ok">
            <span className="dot" />
            Backend online
          </span>
        )}

        {status.kind === "error" && (
          <span className="status-banner is-error">
            <span className="dot" />
            Could not reach backend: {status.message}
          </span>
        )}
      </div>

      <div className={`app-content${loggedInAs ? " is-wide" : ""}`}>
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

        {!loggedInAs && isAuthView && (
          <div className="card">
            <div className="tabs" role="tablist" aria-label="Authentication">
              <button
                type="button"
                role="tab"
                aria-selected={view === "login"}
                className={view === "login" ? "is-active" : ""}
                onClick={() => setView("login")}
              >
                Log in
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={view === "register"}
                className={view === "register" ? "is-active" : ""}
                onClick={() => setView("register")}
              >
                Register
              </button>
            </div>

            {view === "login" ? (
              <>
                <LoginForm onLoginSuccess={setLoggedInAs} />
                <p className="form-footer">
                  <button type="button" className="btn-link" onClick={() => setView("forgot-password")}>
                    Forgot password?
                  </button>
                </p>
              </>
            ) : (
              <RegistrationForm />
            )}
          </div>
        )}

        {!loggedInAs && view === "forgot-password" && (
          <div className="card">
            <ForgotPasswordForm
              onBackToLogin={() => setView("login")}
              onHaveToken={() => setView("reset-password")}
            />
          </div>
        )}

        {!loggedInAs && view === "reset-password" && (
          <div className="card">
            <ResetPasswordForm onResetComplete={() => setView("login")} />
          </div>
        )}
      </div>
    </main>
  );
}

export default App;
