import { useEffect, useState } from "react";
import { fetchGreeting, logout as apiLogout } from "./api/client.js";
import LoginForm from "./components/LoginForm.jsx";
import RegisterForm from "./components/RegisterForm.jsx";
import ForgotPasswordForm from "./components/ForgotPasswordForm.jsx";
import ResetPasswordForm from "./components/ResetPasswordForm.jsx";
import Greeting from "./components/Greeting.jsx";
import LogoutButton from "./components/LogoutButton.jsx";
import AdminUserTable from "./components/AdminUserTable.jsx";

/**
 * App shell. Composes the auth slices into a usable UI:
 * - logged out: login + register + forgot-password (and the reset form when a
 *   ?token= is present in the URL);
 * - logged in: greeting + logout, plus the admin user table for ADMINs.
 *
 * Session is probed on load via GET /api/hello (200 => authenticated, the
 * message carries the username; 401 => logged out). Role comes from the login
 * response; on a cold page load we don't know it, so the admin table is shown
 * only after a login that reported role ADMIN. (The admin endpoints are still
 * server-guarded regardless of what the UI shows.)
 */
export default function App() {
  const [session, setSession] = useState(null); // { username, role? } | null
  const [checked, setChecked] = useState(false);
  const [showRegister, setShowRegister] = useState(false);

  const resetToken = new URLSearchParams(window.location.search).get("token");

  useEffect(() => {
    fetchGreeting()
      .then((message) => {
        if (message) {
          // "Hello, <username>" -> username
          const username = message.replace(/^Hello,\s*/, "");
          setSession({ username });
        }
      })
      .catch(() => setSession(null))
      .finally(() => setChecked(true));
  }, []);

  async function handleLogout() {
    try {
      await apiLogout();
    } finally {
      setSession(null);
    }
  }

  // A password-reset link (?token=...) takes over the whole view.
  if (resetToken) {
    return (
      <main>
        <h1>Reset your password</h1>
        <ResetPasswordForm token={resetToken} />
      </main>
    );
  }

  if (!checked) {
    return (
      <main>
        <h1>Hello World Auth</h1>
        <p data-testid="app-loading">Loading…</p>
      </main>
    );
  }

  if (session) {
    return (
      <main>
        <h1>Hello World Auth</h1>
        <Greeting />
        <LogoutButton onLoggedOut={handleLogout} />
        {session.role === "ADMIN" && (
          <section aria-label="admin">
            <h2>Admin — users</h2>
            <AdminUserTable />
          </section>
        )}
      </main>
    );
  }

  return (
    <main>
      <h1>Hello World Auth</h1>
      {showRegister ? (
        <section aria-label="register">
          <h2>Create an account</h2>
          <RegisterForm onRegistered={() => setShowRegister(false)} />
          <button type="button" onClick={() => setShowRegister(false)}>
            Back to login
          </button>
        </section>
      ) : (
        <section aria-label="login">
          <h2>Log in</h2>
          <LoginForm onLoggedIn={(s) => setSession(s)} />
          <button type="button" onClick={() => setShowRegister(true)}>
            Create an account
          </button>
          <details>
            <summary>Forgot password?</summary>
            <ForgotPasswordForm />
          </details>
        </section>
      )}
    </main>
  );
}
