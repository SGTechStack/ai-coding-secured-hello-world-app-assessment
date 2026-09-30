import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { setSessionLostHandler, statusOf } from '../api/client'
import { AuthContext, type AuthState } from './authContext'
import * as endpoints from '../api/endpoints'
import type { CurrentUser } from '../api/endpoints'

export function AuthProvider({ children }: { children: ReactNode }): ReactNode {
  const [user, setUser] = useState<CurrentUser | null | undefined>(undefined)
  const [loading, setLoading] = useState(true)

  const forgetSession = useCallback(() => {
    setUser(null)
  }, [])

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      setUser(await endpoints.currentUser())
    } catch (error) {
      // A 401 here is the normal answer for "nobody is logged in" on a cold load, so it resolves the
      // bootstrap to null rather than surfacing an error. Anything else is also treated as no session:
      // there is no useful half-authenticated state to render.
      if (statusOf(error) !== 401) {
        // Deliberately not rethrown. The app's response to "we could not establish a session" is the
        // login screen in every case, and a thrown error here would blank the whole tree on a
        // transient failure.
      }
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const signedIn = await endpoints.login(username, password)
    setUser(signedIn)
    return signedIn
  }, [])

  /**
   * Signs out, and **never rejects**.
   *
   * The swallow is deliberate rather than lazy. Logout is CSRF-protected on purpose, so logging out of an
   * already-expired session answers 401 — and `Std:438` forbids "fixing" that. A rejected promise here
   * would mean every caller had to write a catch for a condition with no remedy: the local session is
   * over either way, and there is nothing to retry and nothing useful to tell the user. Leaving it
   * rejecting also produced an unhandled rejection from the sign-out button, which `void logout()` cannot
   * absorb.
   */
  const logout = useCallback(async () => {
    try {
      await endpoints.logout()
    } catch {
      // Intentionally ignored — see above.
    } finally {
      setUser(null)
    }
  }, [])

  // A ref so the handler registered with the axios module is stable while still seeing current state.
  const forgetRef = useRef(forgetSession)
  forgetRef.current = forgetSession

  useEffect(() => {
    // The interceptor lives outside React and cannot import this module without a cycle, so the
    // provider hands it the one callback it needs.
    setSessionLostHandler(() => forgetRef.current())
    return () => setSessionLostHandler(() => {})
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const value = useMemo<AuthState>(
    () => ({ user, loading, login, logout, refresh, forgetSession }),
    [user, loading, login, logout, refresh, forgetSession],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
