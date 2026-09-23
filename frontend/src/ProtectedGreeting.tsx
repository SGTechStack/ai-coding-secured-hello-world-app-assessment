import { useEffect, useState } from "react";
import { fetchGreeting, isAbortError, logout, type UserRole } from "./api/client";
import { AdminUserList } from "./AdminUserList";

interface ProtectedGreetingProps {
  username: string;
  role: UserRole;
  onLoggedOut: () => void;
}

type GreetingState =
  | { kind: "loading" }
  | { kind: "success"; greeting: string }
  | { kind: "error"; message: string };

export function ProtectedGreeting({ username, role, onLoggedOut }: ProtectedGreetingProps) {
  const [greeting, setGreeting] = useState<GreetingState>({ kind: "loading" });
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  /**
   * Real cancellation rather than an `isMounted` flag: aborting also tears down
   * the in-flight request instead of merely ignoring its response, which matters
   * under StrictMode's deliberate double-invocation of effects in development.
   *
   * Empty deps, not `[username]`: the request's only input is the session
   * cookie, and this component is unmounted on logout, so a new session always
   * arrives as a fresh mount.
   */
  useEffect(() => {
    const controller = new AbortController();

    fetchGreeting(controller.signal)
      .then((text) => setGreeting({ kind: "success", greeting: text }))
      .catch((error: unknown) => {
        if (isAbortError(error)) return;
        setGreeting({
          kind: "error",
          message: error instanceof Error ? error.message : "Could not load greeting",
        });
      });

    return () => controller.abort();
  }, []);

  async function handleLogout() {
    setIsLoggingOut(true);
    try {
      await logout();
    } finally {
      onLoggedOut();
    }
  }

  return (
    <>
      <div className="card welcome-card">
        <div className="greeting">
          <span className={`role-badge${role === "ADMIN" ? " is-admin" : ""}`}>{role}</span>

          {greeting.kind === "loading" && <p className="greeting-text skeleton-text">Loading greeting…</p>}
          {greeting.kind === "success" && (
            <p className="greeting-text" role="status">
              {greeting.greeting}
            </p>
          )}
          {greeting.kind === "error" && (
            <p className="alert alert-error" role="alert">
              {greeting.message}
            </p>
          )}
        </div>

        <button type="button" className="btn-secondary" onClick={handleLogout} disabled={isLoggingOut}>
          {isLoggingOut ? "Logging out…" : "Log out"}
        </button>
      </div>

      {role === "ADMIN" && (
        <div className="card">
          <AdminUserList currentUsername={username} />
        </div>
      )}
    </>
  );
}
