import { apiRequest, refreshCsrfToken } from './client'

/** The caller's own Account, from `POST /login` and `GET /me`. */
export type OwnAccount = {
  id: string
  username: string
  email: string
  role: 'USER' | 'ADMIN'
  passwordChangeRequired: boolean
}

export type LoginResult =
  { ok: true; account: OwnAccount } | { ok: false; reason: 'rejected' | 'rate_limited' | 'error' }

export type OwnAccountState = { kind: 'authenticated'; account: OwnAccount } | { kind: 'anonymous' } | { kind: 'error' }

/**
 * Logs in. The server replaces the Session and its CSRF token at login, so a new token is fetched
 * before anything else is sent. Every credential failure looks the same.
 */
export async function login(username: string, password: string): Promise<LoginResult> {
  const result = await apiRequest<OwnAccount>('/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (result.ok) {
    await refreshCsrfToken()
    return { ok: true, account: result.data }
  }
  if (result.status === 429) return { ok: false, reason: 'rate_limited' }
  const code = result.problem?.code
  const rejected =
    (result.status === 401 && code === 'authentication_failed') || (result.status === 400 && code === 'validation')
  // Anything else (for example a CSRF rejection that survived one retry) is not a credential problem.
  return { ok: false, reason: rejected ? 'rejected' : 'error' }
}

/**
 * Logs out. A 401 or 403 means the Session had already ended (ADR 0002), which counts as logged out
 * too. Either way the server has dropped the old CSRF token, so a new one is fetched.
 * @returns whether the caller is now logged out
 */
export async function logout(): Promise<boolean> {
  const result = await apiRequest('/logout', { method: 'POST' })
  const loggedOut = result.ok || result.status === 401 || result.status === 403
  if (loggedOut) await refreshCsrfToken()
  return loggedOut
}

/** Asks `GET /me` who, if anyone, is logged in. A 401 here means a Visitor, not an ended Session. */
export async function fetchOwnAccount(): Promise<OwnAccountState> {
  const result = await apiRequest<OwnAccount>('/me', {}, { sessionEndedOn401: false })
  if (result.ok) return { kind: 'authenticated', account: result.data }
  return result.status === 401 ? { kind: 'anonymous' } : { kind: 'error' }
}
