import { useEffect, useState } from "react";
import { fetchMe, logout as apiLogout } from "./api/client.js";
import LoginForm from "./components/LoginForm.jsx";
import RegisterForm from "./components/RegisterForm.jsx";
import ForgotPasswordForm from "./components/ForgotPasswordForm.jsx";
import ResetPasswordForm from "./components/ResetPasswordForm.jsx";
import Greeting from "./components/Greeting.jsx";
import NavBar from "./components/NavBar.jsx";
import AdminPage from "./components/AdminPage.jsx";
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
 * - logged in: a top nav (Home / Admin) + the active view — Home shows the
 *   greeting; Admin shows the user-management page, and the Admin nav item is
 *   shown only to ADMIN accounts.
 *
 * Session (including ROLE) is probed on load via GET /api/me, so an admin who
 * reloads the page still sees the Admin nav. Authorization is enforced
 * server-side by the /api/admin/** guard regardless of what the UI shows.
 */
export default function App() {
  const [session, setSession] = useState(null); // { username, role } | null
  const [checked, setChecked] = useState(false);
  const [showRegister, setShowRegister] = useState(false);
  const [view, setView] = useState("home"); // "home" | "admin"

  const resetToken = new URLSearchParams(window.location.search).get("token");

  useEffect(() => {
    fetchMe()
      .then((me) => {
        if (me) setSession(me);
      })
      .catch(() => setSession(null))
      .finally(() => setChecked(true));
  }, []);

  async function handleLogout() {
    try {
      await apiLogout();
    } finally {
      setSession(null);
      setView("home");
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
    // Guard against a stale "admin" view if role somehow isn't ADMIN.
    const activeView = view === "admin" && session.role === "ADMIN" ? "admin" : "home";
    return (
      <div className="min-h-screen bg-muted/40">
        <NavBar
          username={session.username}
          role={session.role}
          current={activeView}
          onNavigate={setView}
          onLoggedOut={handleLogout}
        />
        <main className="mx-auto flex max-w-5xl flex-col gap-6 px-4 py-8">
          {activeView === "admin" ? (
            <AdminPage />
          ) : (
            <section aria-label="home" className="flex flex-col gap-2">
              <Greeting />
              <p className="text-sm text-muted-foreground">
                You are signed in{session.role === "ADMIN" ? " as an administrator" : ""}.
              </p>
            </section>
          )}
        </main>
      </div>
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
