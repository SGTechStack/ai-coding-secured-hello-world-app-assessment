import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError, api } from '../api/client'
import { AuthContext, type AuthState } from './useAuth'

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

  const sessionEnded = useCallback(() => setState({ status: 'anonymous', expired: true }), [])

  const value = useMemo(() => ({ state, login, logout, sessionEnded }), [state, login, logout, sessionEnded])
  return <AuthContext value={value}>{children}</AuthContext>
}
