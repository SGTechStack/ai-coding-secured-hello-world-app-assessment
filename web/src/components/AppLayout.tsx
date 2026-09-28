import { useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { Button } from "./ui/Button";
import { Logo } from "./ui/Logo";

/** The shell for authenticated pages: who you are, where you can go, and how to leave. */
export function AppLayout() {
  const { username, role, isAdmin, logOut } = useAuth();
  const navigate = useNavigate();
  const [loggingOut, setLoggingOut] = useState(false);

  async function handleLogOut() {
    setLoggingOut(true);
    try {
      await logOut();
      navigate("/login", { replace: true });
    } finally {
      setLoggingOut(false);
    }
  }

  return (
    <div className="min-h-screen bg-canvas">
      <header className="border-b border-edge bg-panel">
        <div className="mx-auto flex max-w-4xl flex-wrap items-center gap-4 px-4 py-3">
          {/* The logo doubles as the home link, which is the convention people already expect. */}
          <Link
            to="/"
            aria-label="Helloworld Auth, go to the home page"
            className="rounded-md focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent"
          >
            <Logo />
          </Link>
          <nav aria-label="Main" className="flex items-center gap-3 text-sm">
            <NavLink to="/" className={navLinkClass} end>
              Home
            </NavLink>
            {/* Hidden for non-admins as a courtesy. The server refuses the endpoints regardless. */}
            {isAdmin ? (
              <NavLink to="/admin" className={navLinkClass}>
                Accounts
              </NavLink>
            ) : null}
          </nav>
          <div className="ml-auto flex items-center gap-3 text-sm text-ink-muted">
            <span>
              {username}
              {/*
               * Soft-filled rather than solid orange, for two reasons. The brand badge two inches to
               * the left is a solid orange pill, and a status indicator that looks identical to the
               * logo reads as part of it. It also now matches the ADMIN badge in the accounts table,
               * so the same fact looks the same wherever it appears.
               */}
              {role === "ADMIN" ? (
                <span className="ml-2 rounded border border-accent/40 bg-accent-soft px-1.5 py-0.5 text-xs font-medium text-accent">
                  Admin
                </span>
              ) : null}
            </span>
            <Button variant="secondary" onClick={handleLogOut} busy={loggingOut} busyLabel="Logging out">
              Log out
            </Button>
          </div>
        </div>
      </header>
      {/* Wider than the header's content width because the accounts table needs the room: six columns
          plus three action buttons per row does not fit comfortably in 4xl. */}
      <main className="mx-auto max-w-6xl p-4">
        <Outlet />
      </main>
    </div>
  );
}

/**
 * The active link is marked by colour *and* an underline. Colour alone would leave anyone who cannot
 * distinguish orange from grey with no idea which page they are on.
 */
function navLinkClass({ isActive }: { isActive: boolean }) {
  return isActive
    ? "font-medium text-accent underline underline-offset-4"
    : "text-ink-muted hover:text-ink";
}
