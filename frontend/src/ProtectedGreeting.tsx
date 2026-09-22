import { useEffect, useState } from "react";
import { fetchGreeting, logout, type UserRole } from "./api/client";
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

  useEffect(() => {
    let isMounted = true;

    fetchGreeting()
      .then((text) => {
        if (isMounted) setGreeting({ kind: "success", greeting: text });
      })
      .catch((error: unknown) => {
        if (isMounted) {
          setGreeting({
            kind: "error",
            message: error instanceof Error ? error.message : "Could not load greeting",
          });
        }
      });

    return () => {
      isMounted = false;
    };
  }, [username]);

  async function handleLogout() {
    setIsLoggingOut(true);
    try {
      await logout();
    } finally {
      onLoggedOut();
    }
  }

  return (
    <section>
      <h2>Welcome</h2>

      {greeting.kind === "loading" && <p role="status">Loading greeting…</p>}
      {greeting.kind === "success" && <p role="status">{greeting.greeting}</p>}
      {greeting.kind === "error" && (
        <p role="alert" style={{ color: "crimson" }}>
          {greeting.message}
        </p>
      )}

      <button type="button" onClick={handleLogout} disabled={isLoggingOut}>
        {isLoggingOut ? "Logging out…" : "Log out"}
      </button>

      {role === "ADMIN" && <AdminUserList />}
    </section>
  );
}
