import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError, api, onUnauthenticated } from '../api/client'
import { AuthContext, type AuthState } from './useAuth'
import { useIdleTimeout } from './useIdleTimeout'

/** Matches the server's session idle timeout (spring.session.timeout). */
const IDLE_TIMEOUT_MS = 15 * 60 * 1000

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' })

  useEffect(() => {
    let cancelled = false
    api
      .me()
      .then((user) => {
        if (!cancelled) setState({ status: 'authenticated', user })
      })
      .catch((error: unknown) => {
        if (cancelled) return
        // Development only: console output in production builds can reveal internals.
        if (import.meta.env.DEV && !(error instanceof ApiError && error.status === 401)) {
          console.error('Could not load the current session', error)
        }
        setState({ status: 'anonymous' })
      })
    return () => {
      cancelled = true
    }
  }, [])

  // Whichever call first hears that the session is gone (expired, revoked, signed out in another
  // tab) ends it here; RequireAuth then redirects to sign in with a notice.
  useEffect(
    () =>
      onUnauthenticated(() =>
        setState((current) => (current.status === 'authenticated' ? { status: 'anonymous', expired: true } : current)),
      ),
    [],
  )

  const login = useCallback(async (username: string, password: string) => {
    const user = await api.login(username, password)
    setState({ status: 'authenticated', user })
    return user
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.logout()
    } finally {
      setState({ status: 'anonymous' })
    }
  }, [])

  const signOutIdleUser = useCallback(() => {
    api
      .logout()
      .catch(() => {
        // The server session expires on its own; the UI is signed out either way.
      })
      .finally(() => setState({ status: 'anonymous', expired: true }))
  }, [])
  useIdleTimeout(state.status === 'authenticated', IDLE_TIMEOUT_MS, signOutIdleUser)

  const value = useMemo(() => ({ state, login, logout }), [state, login, logout])
  return <AuthContext value={value}>{children}</AuthContext>
}
