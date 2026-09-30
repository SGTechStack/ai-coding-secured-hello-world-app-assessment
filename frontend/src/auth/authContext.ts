import { createContext } from 'react'
import type { CurrentUser } from '../api/endpoints'

/**
 * The session, read from `GET /currentUser` and nowhere else.
 *
 * `role` and `requirePasswordChange` are the two fields the router gates on, and both come from the
 * server on every bootstrap rather than being inferred from a login response or cached in
 * `localStorage`. That is not incidental: a role kept client-side is a role a user can edit, and a
 * `requirePasswordChange` kept client-side is a forced change a user can skip with devtools. The server
 * enforces both regardless — the tier-0 filter and the authorization matrix never consult the client —
 * so this state exists to render the right screen, never to authorise anything.
 *
 * In its own module rather than beside the provider so that the provider file exports components only,
 * which is what keeps Vite's fast refresh working on it.
 */
export interface AuthState {
  /** `undefined` while the bootstrap is in flight; `null` once it has resolved to "nobody". */
  user: CurrentUser | null | undefined
  loading: boolean
  login: (username: string, password: string) => Promise<CurrentUser>
  logout: () => Promise<void>
  refresh: () => Promise<void>
  /** Drops local state without calling the server — for a session the server has already ended. */
  forgetSession: () => void
}

export const AuthContext = createContext<AuthState | undefined>(undefined)
