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

export type PasswordChangeResult =
  | { ok: true }
  | { ok: false; reason: 'current_password_invalid' | 'password_history' | 'validation' | 'error' }
  | { ok: false; reason: 'password_policy'; violations: string[] }

const PASSWORD_CHANGE_REFUSALS = new Set(['current_password_invalid', 'password_history', 'validation'])

/**
 * Password Change. On success the server has ended every Session of the Account, this one and its
 * CSRF token included, so a new token is fetched for the Visitor Session that follows. The change
 * has happened either way, so a failed fetch still reports success; the next state-changing
 * request fetches a token again.
 */
export async function changePassword(currentPassword: string, newPassword: string): Promise<PasswordChangeResult> {
  const result = await apiRequest('/me/password', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ currentPassword, newPassword }),
  })
  if (result.ok) {
    await refreshCsrfToken().catch(() => undefined)
    return { ok: true }
  }
  const code = result.problem?.code
  if (result.status === 400 && code === 'password_policy') {
    return { ok: false, reason: 'password_policy', violations: result.problem?.violations ?? [] }
  }
  if (result.status === 400 && code !== undefined && PASSWORD_CHANGE_REFUSALS.has(code)) {
    return { ok: false, reason: code as 'current_password_invalid' | 'password_history' | 'validation' }
  }
  return { ok: false, reason: 'error' }
}
