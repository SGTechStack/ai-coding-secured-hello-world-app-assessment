import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { FullPageSpinner } from "../components/ui/Spinner";
import { useAuth } from "./useAuth";

/**
 * Route guards.
 *
 * These decide what a visitor *sees*, not what they are *allowed* to do. Every one of them is
 * cosmetic: the server enforces the same rules again on every request, and would refuse an admin
 * endpoint to a `USER` whatever the router did. Treating a guard as the enforcement point is how
 * client-side authorization bugs happen.
 */

/** Waits for the first session check rather than flashing a login page at someone already logged in. */
function whileResolving(isLoading: boolean) {
  return isLoading ? <FullPageSpinner label="Checking your session" /> : null;
}

export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  const pending = whileResolving(isLoading);
  if (pending) return pending;

  if (!isAuthenticated) {
    // Remember where they were headed, so login can send them onward instead of to the home page.
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <>{children}</>;
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  const { isAuthenticated, isAdmin, isLoading } = useAuth();
  const location = useLocation();

  const pending = whileResolving(isLoading);
  if (pending) return pending;

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (!isAdmin) {
    // Home, not a 403 page: a logged-in user who wandered onto an admin URL is not in trouble.
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}

/** For the login and registration pages, which make no sense to someone already signed in. */
export function RequireVisitor({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth();

  const pending = whileResolving(isLoading);
  if (pending) return pending;

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
