import { createContext, use } from 'react'
import type { CurrentUser } from '../api/client'

export type AuthState =
  | { status: 'loading' }
  /** `expired` marks a session that ended server-side (timeout, revocation) rather than a fresh visit. */
  | { status: 'anonymous'; expired?: boolean }
  | { status: 'authenticated'; user: CurrentUser }

export interface AuthContextValue {
  state: AuthState
  login: (username: string, password: string) => Promise<CurrentUser>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const context = use(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return context
}
