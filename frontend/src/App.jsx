import { useEffect, useState } from "react";
import { fetchGreeting, logout as apiLogout } from "./api/client.js";
import LoginForm from "./components/LoginForm.jsx";
import RegisterForm from "./components/RegisterForm.jsx";
import ForgotPasswordForm from "./components/ForgotPasswordForm.jsx";
import ResetPasswordForm from "./components/ResetPasswordForm.jsx";
import Greeting from "./components/Greeting.jsx";
import LogoutButton from "./components/LogoutButton.jsx";
import AdminUserTable from "./components/AdminUserTable.jsx";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/card";

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
      <main className="flex min-h-screen items-center justify-center bg-muted/40 px-4 py-10">
        <Card className="w-full max-w-md" size="default">
          <CardHeader>
            <CardTitle className="text-xl">Reset your password</CardTitle>
            <CardDescription>Choose a new password for your account.</CardDescription>
          </CardHeader>
          <CardContent>
            <ResetPasswordForm token={resetToken} />
          </CardContent>
        </Card>
      </main>
    );
  }

  if (!checked) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-muted/40 px-4">
        <p data-testid="app-loading" className="text-sm text-muted-foreground">
          Loading…
        </p>
      </main>
    );
  }

  if (session) {
    return (
      <main className="min-h-screen bg-muted/40">
        <div className="mx-auto flex max-w-5xl flex-col gap-6 px-4 py-8">
          <header className="flex items-start justify-between gap-4">
            <div className="flex flex-col gap-1">
              <h1 className="text-sm font-medium text-muted-foreground">
                Hello World Auth
              </h1>
              <Greeting />
            </div>
            <LogoutButton onLoggedOut={handleLogout} />
          </header>
          {session.role === "ADMIN" && (
            <section aria-label="admin" className="flex flex-col gap-3">
              <h2 className="text-lg font-semibold tracking-tight">Admin — users</h2>
              <AdminUserTable />
            </section>
          )}
        </div>
      </main>
    );
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-muted/40 px-4 py-10">
      <div className="flex w-full max-w-md flex-col gap-4">
        <div className="flex flex-col items-center gap-1 text-center">
          <h1 className="text-2xl font-semibold tracking-tight">Hello World Auth</h1>
          <p className="text-sm text-muted-foreground">
            Sign in to your account to continue.
          </p>
        </div>
        {showRegister ? (
          <section aria-label="register">
            <Card size="default">
              <CardHeader>
                <CardTitle className="text-xl">Create an account</CardTitle>
                <CardDescription>Register a new account to get started.</CardDescription>
              </CardHeader>
              <CardContent className="flex flex-col gap-4">
                <RegisterForm onRegistered={() => setShowRegister(false)} />
                <Button
                  type="button"
                  variant="link"
                  className="self-center"
                  onClick={() => setShowRegister(false)}
                >
                  Back to login
                </Button>
              </CardContent>
            </Card>
          </section>
        ) : (
          <section aria-label="login">
            <Card size="default">
              <CardHeader>
                <CardTitle className="text-xl">Log in</CardTitle>
                <CardDescription>Enter your credentials to sign in.</CardDescription>
              </CardHeader>
              <CardContent className="flex flex-col gap-4">
                <LoginForm onLoggedIn={(s) => setSession(s)} />
                <Button
                  type="button"
                  variant="link"
                  className="self-center"
                  onClick={() => setShowRegister(true)}
                >
                  Create an account
                </Button>
                <details className="text-sm">
                  <summary className="cursor-pointer text-muted-foreground hover:text-foreground">
                    Forgot password?
                  </summary>
                  <ForgotPasswordForm />
                </details>
              </CardContent>
            </Card>
          </section>
        )}
      </div>
    </main>
  );
}
