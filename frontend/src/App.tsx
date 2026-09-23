import { useEffect, useState } from "react";
import { type LoginResponse } from "./api/client";
import { RegistrationForm } from "./RegistrationForm";
import { LoginForm } from "./LoginForm";
import { ProtectedGreeting } from "./ProtectedGreeting";
import { ForgotPasswordForm } from "./ForgotPasswordForm";
import { ResetPasswordForm } from "./ResetPasswordForm";
import { DevToolsBanner } from "./DevToolsBanner";

type View = "login" | "register" | "forgot-password" | "reset-password";

/**
 * Reads the reset token out of the address bar. Called only from lazy state
 * initialisers, so the URL is sampled once per mount rather than on every
 * render — and always before the effect below erases it.
 */
function readTokenFromUrl(): string | null {
  return new URLSearchParams(window.location.search).get("token");
}

function App() {
  const [loggedInAs, setLoggedInAs] = useState<LoginResponse | null>(null);
  const [resetToken, setResetToken] = useState<string | null>(readTokenFromUrl);
  const [view, setView] = useState<View>(() => (readTokenFromUrl() ? "reset-password" : "login"));
  const [registeredUsername, setRegisteredUsername] = useState<string | null>(null);

  /**
   * Treat the reset token as one-shot: now that it lives in React state, take
   * it out of the address bar. This keeps it out of browser history entries and
   * out of the `Referer` header on any outbound navigation, and stops a page
   * refresh from dropping the user back into the reset flow holding a token
   * that has already been consumed.
   *
   * This belongs in an effect rather than the render body: mutating browser
   * history is a side effect, and the render phase can be re-entered or
   * abandoned by the scheduler.
   */
  useEffect(() => {
    if (new URLSearchParams(window.location.search).has("token")) {
      window.history.replaceState({}, "", window.location.pathname);
    }
  }, []);

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
                  onClick={() => {
                    setRegisteredUsername(null);
                    setView("register");
                  }}
                >
                  Register
                </button>
              </div>

              {view === "login" ? (
                <>
                  {registeredUsername && (
                    <p className="alert alert-success" role="status">
                      Account <strong>{registeredUsername}</strong> created successfully. Log in below to continue.
                    </p>
                  )}
                  <LoginForm
                    initialUsername={registeredUsername ?? undefined}
                    onLoginSuccess={(result) => {
                      setRegisteredUsername(null);
                      setLoggedInAs(result);
                    }}
                  />
                  <p className="form-footer">
                    <button type="button" className="btn-link" onClick={() => setView("forgot-password")}>
                      Forgot password?
                    </button>
                  </p>
                </>
              ) : (
                <RegistrationForm
                  onRegistered={(data) => {
                    setRegisteredUsername(data.username);
                    setView("login");
                  }}
                />
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
              <ResetPasswordForm
                initialToken={resetToken ?? ""}
                onResetComplete={() => {
                  setResetToken(null);
                  setView("login");
                }}
              />
            </div>
          )}
        </div>
      </main>
    </>
  );
}

export default App;
