import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import type { Role } from '../api/client'
import { AccessDeniedPage } from '../pages/AccessDeniedPage'
import { useAuth } from './useAuth'

/** State passed to /login by redirects. */
export interface LoginRedirectState {
  from?: string
  notice?: string
}

/** Only same-app paths; anything else (e.g. "//evil.example") falls back to home. */
function safeRedirectTarget(path: string | undefined): string {
  return path && path.startsWith('/') && !path.startsWith('//') ? path : '/'
}

/**
 * Client-side routing only; it improves navigation but protects nothing. Every rule is
 * enforced again by the API.
 */
export function RequireAuth({ role, children }: { role?: Role; children: ReactNode }) {
  const { state } = useAuth()
  const location = useLocation()

  if (state.status === 'loading') {
    return <p className="muted">Loading…</p>
  }
  if (state.status === 'anonymous') {
    const redirect: LoginRedirectState = {
      from: location.pathname,
      notice: state.expired ? 'Your session has ended. Please sign in again.' : undefined,
    }
    return <Navigate to="/login" replace state={redirect} />
  }
  if (role && state.user.role !== role) {
    return <AccessDeniedPage />
  }
  return children
}

/**
 * For sign-in and registration. Once signed in (including right after a successful login)
 * the user is sent back to where they were going.
 */
export function GuestOnly({ children }: { children: ReactNode }) {
  const { state } = useAuth()
  const location = useLocation()

  if (state.status === 'loading') {
    return <p className="muted">Loading…</p>
  }
  if (state.status === 'authenticated') {
    const from = (location.state as LoginRedirectState | null)?.from
    return <Navigate to={safeRedirectTarget(from)} replace />
  }
  return children
}
