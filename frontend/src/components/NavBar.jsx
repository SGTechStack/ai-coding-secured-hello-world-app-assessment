import { Button } from "@/components/ui/button";
import LogoutButton from "./LogoutButton.jsx";

/**
 * Navigation bar for authenticated users, rendered as a card-width bar above
 * the active view. Shows Home always and Admin only to ADMIN accounts (the
 * admin API is server-guarded regardless). `current` is the active view key;
 * `onNavigate(view)` switches views; `onLoggedOut` ends the session.
 */
export default function NavBar({ username, role, current, onNavigate, onLoggedOut }) {
  const isAdmin = role === "ADMIN";
  return (
    <nav
      aria-label="main"
      className="flex items-center justify-between gap-4 rounded-xl bg-card/60 px-3 py-2 ring-1 ring-foreground/10 backdrop-blur supports-[backdrop-filter]:bg-card/40"
    >
      <div className="flex items-center gap-1">
        <span className="mr-3 text-sm font-semibold tracking-tight">Hello World Auth</span>
        <Button
          type="button"
          size="sm"
          variant={current === "home" ? "secondary" : "ghost"}
          aria-current={current === "home" ? "page" : undefined}
          onClick={() => onNavigate("home")}
          className={
            current === "home"
              ? "bg-accent text-accent-foreground shadow-[inset_0_0_0_1px_var(--brand)] hover:bg-accent"
              : undefined
          }
        >
          Home
        </Button>
        {isAdmin && (
          <Button
            type="button"
            size="sm"
            variant={current === "admin" ? "secondary" : "ghost"}
            data-testid="nav-admin"
            aria-current={current === "admin" ? "page" : undefined}
            onClick={() => onNavigate("admin")}
            className={
              current === "admin"
                ? "bg-accent text-accent-foreground shadow-[inset_0_0_0_1px_var(--brand)] hover:bg-accent"
                : undefined
            }
          >
            Admin
          </Button>
        )}
      </div>
      <div className="flex items-center gap-3">
        {username && (
          <span className="text-sm text-muted-foreground" data-testid="nav-username">
            {username}
          </span>
        )}
        <LogoutButton onLoggedOut={onLoggedOut} />
      </div>
    </nav>
  );
}
