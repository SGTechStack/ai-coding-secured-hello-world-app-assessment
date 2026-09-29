import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { fetchOwnAccount } from '../api/auth'
import { onSessionEnded } from '../api/client'
import { AuthContext, type Auth, type AuthState } from './useAuth'

/**
 * Holds the auth state for the whole SPA. On load it calls `GET /me` to decide what to show; any
 * ended Session (a global 401) makes the caller anonymous again.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ kind: 'loading' })

  useEffect(() => {
    let active = true
    fetchOwnAccount()
      .then((result) => active && setState(result))
      .catch(() => active && setState({ kind: 'error' }))
    return () => {
      active = false
    }
  }, [])

  useEffect(() => onSessionEnded(() => setState({ kind: 'anonymous' })), [])

  const auth = useMemo<Auth>(
    () => ({
      state,
      loggedIn: (account) => setState({ kind: 'authenticated', account }),
      loggedOut: () => setState({ kind: 'anonymous', loggedOut: true }),
    }),
    [state],
  )
  return <AuthContext.Provider value={auth}>{children}</AuthContext.Provider>
}
