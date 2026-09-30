import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './useAuth'

/** Shown while the bootstrap `GET /currentUser` is in flight. */
function Bootstrapping(): ReactNode {
  return <p className="muted">Loading…</p>
}

/**
 * Requires a session, and **pre-empts every route when a password change is forced**.
 *
 * The ordering is the contract. `requirePasswordChange` is checked before the route is rendered and
 * before any role check, because the backend's tier-0 `PasswordChangeFilter` sits ahead of the
 * authorization matrix and answers 403 `PASSWORD_CHANGE_REQUIRED` on everything except four paths. A SPA
 * that rendered the user list first would show an empty table and an error rather than the one screen
 * the user can actually use.
 *
 * This is presentation, not enforcement: the filter refuses those requests whatever the client renders.
 */
export function RequireSession(): ReactNode {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading || user === undefined) {
    return <Bootstrapping />
  }
  if (user === null) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (user.requirePasswordChange && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />
  }
  return <Outlet />
}

/**
 * Requires `USER_MANAGER`.
 *
 * Nested inside {@link RequireSession} so the forced-change gate is already satisfied by the time this
 * runs. A plain `USER` reaching an admin route is sent to the greeting rather than to login: they are
 * legitimately signed in, and bouncing them to a login form would read as a session failure.
 */
export function RequireUserManager(): ReactNode {
  const { user } = useAuth()
  if (!user) {
    return <Bootstrapping />
  }
  if (user.role !== 'USER_MANAGER') {
    return <Navigate to="/hello" replace />
  }
  return <Outlet />
}

/** The inverse, for login and registration: a signed-in user has no business on those screens. */
export function RequireAnonymous(): ReactNode {
  const { user, loading } = useAuth()
  if (loading || user === undefined) {
    return <Bootstrapping />
  }
  if (user !== null) {
    return <Navigate to={user.requirePasswordChange ? '/change-password' : '/hello'} replace />
  }
  return <Outlet />
}
