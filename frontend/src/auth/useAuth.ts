import { createContext, useContext } from 'react'
import type { OwnAccount, OwnAccountState } from '../api/auth'

/**
 * Who is logged in, as last learned from `GET /me`, login, logout or Password Change. `loggedOut`
 * and `passwordChanged` mark a Visitor who just logged out or changed their password, so the login
 * screen can say so.
 */
export type AuthState =
  | { kind: 'loading' }
  | OwnAccountState
  | { kind: 'anonymous'; loggedOut: true }
  | { kind: 'anonymous'; passwordChanged: true }

export type Auth = {
  state: AuthState
  /** Records a successful login. */
  loggedIn: (account: OwnAccount) => void
  /** Records a completed logout; the route guard then shows the login screen. */
  loggedOut: () => void
  /** Records a Password Change, which ended the Session; the route guard then shows the login screen. */
  passwordChanged: () => void
}

export const AuthContext = createContext<Auth | null>(null)

export function useAuth(): Auth {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth must be used inside <AuthProvider>')
  return auth
}
