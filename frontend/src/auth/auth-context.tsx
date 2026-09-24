import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  bootstrapCsrf,
  getMe,
  login as apiLogin,
  logout as apiLogout,
  register as apiRegister,
  type Principal,
} from '@/lib/api'

interface AuthContextValue {
  /** The signed-in principal, or null when anonymous. */
  user: Principal | null
  /** True while the initial /api/auth/me probe is in flight. */
  loading: boolean
  signIn: (username: string, password: string) => Promise<Principal>
  signUp: (username: string, email: string, password: string) => Promise<Principal>
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

/**
 * Session state for the SPA. On mount it bootstraps the CSRF token and probes
 * GET /api/auth/me — a valid session cookie restores the session across page
 * reloads without any client-side token storage.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Principal | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        await bootstrapCsrf()
        const me = await getMe()
        if (!cancelled) {
          setUser(me)
        }
      } catch {
        if (!cancelled) {
          setUser(null)
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const signIn = useCallback(async (username: string, password: string) => {
    const principal = await apiLogin(username, password)
    setUser(principal)
    return principal
  }, [])

  const signUp = useCallback(
    async (username: string, email: string, password: string) => {
      await apiRegister(username, email, password)
      // Registration doesn't create a session — sign straight in so the
      // register → login → hello flow is a single user gesture.
      return signIn(username, password)
    },
    [signIn],
  )

  const signOut = useCallback(async () => {
    try {
      // POST /api/auth/logout (with CSRF) kills the server-side session row
      // and re-bootstraps the CSRF token the server cleared.
      await apiLogout()
    } finally {
      // Drop local state even if the call failed — the route guards then
      // land the user on /login. A failed call leaves the server session
      // alive, but it still expires on its own.
      setUser(null)
    }
  }, [])

  const value = useMemo(
    () => ({ user, loading, signIn, signUp, signOut }),
    [user, loading, signIn, signUp, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}
