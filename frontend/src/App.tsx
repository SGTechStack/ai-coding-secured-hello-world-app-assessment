import { useState } from "react";
import { type LoginResponse } from "./api/client";
import { RegistrationForm } from "./RegistrationForm";
import { LoginForm } from "./LoginForm";
import { ProtectedGreeting } from "./ProtectedGreeting";
import { ForgotPasswordForm } from "./ForgotPasswordForm";
import { ResetPasswordForm } from "./ResetPasswordForm";
import { DevToolsBanner } from "./DevToolsBanner";

type View = "login" | "register" | "forgot-password" | "reset-password";

function initialView(): View {
  const params = new URLSearchParams(window.location.search);
  return params.has("token") ? "reset-password" : "login";
}

function App() {
  const [loggedInAs, setLoggedInAs] = useState<LoginResponse | null>(null);
  const [view, setView] = useState<View>(initialView);

  const isAuthView = view === "login" || view === "register";

  return (
    <>
      <DevToolsBanner />
      <main className="app">
        <div className="app-header">
          <h1>Hello World Auth App</h1>
          <p>A secure username/password reference implementation.</p>
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
    </>
  );
}

export default App;
