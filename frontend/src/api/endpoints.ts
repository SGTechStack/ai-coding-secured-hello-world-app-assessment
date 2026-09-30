import { api, clearCsrfToken, fetchCsrfToken } from './client'

/**
 * Every call this application makes, in one place.
 *
 * One module rather than a hook per resource: there are eighteen endpoints and no caching layer, so a
 * per-resource abstraction would add indirection without removing anything.
 */

// ---------------------------------------------------------------------------- shapes

/** `GET /currentUser`. Carries no lock state and no failed-attempt count, by design (Q23a:603). */
export interface CurrentUser {
  username: string
  email: string
  role: 'USER' | 'USER_MANAGER'
  requirePasswordChange: boolean
  createdAt: string
  lastLoginAt: string | null
  lastPasswordChangeAt: string | null
}

/** A row of `GET /users`. Narrower than the detail projection, deliberately. */
export interface UserSummary {
  id: string
  username: string
  email: string
  role: string
  enabled: boolean
  createdAt: string
}

/** `GET /users/{id}`. A distinct type from {@link CurrentUser}: widening one must not widen the other. */
export interface UserDetail extends UserSummary {
  requirePasswordChange: boolean
  failedLoginAttempts: number
  lockedUntil: string | null
  lastLoginAt: string | null
  disabledAt: string | null
}

/** `PATCH /users/{id}/resetPassword`. The token is present only because there is no mail server. */
export interface IssuedReset {
  token?: string
  expiresAt?: string
}

// ---------------------------------------------------------------------------- session

/**
 * The bootstrap: CSRF token, then login, then read the session back.
 *
 * Three steps and all three are required. The token must exist before the login POST because nothing is
 * CSRF-exempt; and `GET /currentUser` is how the app learns its role and its forced-change state, which
 * no part of the login response carries.
 */
export async function login(username: string, password: string): Promise<CurrentUser> {
  await fetchCsrfToken()
  await api.post('/auth/login', { username, password })
  // The session id rotated on success, so the token bound to the old session is dead.
  clearCsrfToken()
  return currentUser()
}

export async function logout(): Promise<void> {
  try {
    await api.post('/auth/logout')
  } finally {
    // Even a failed logout means the local session is over as far as this app is concerned.
    clearCsrfToken()
  }
}

export async function currentUser(): Promise<CurrentUser> {
  const response = await api.get<CurrentUser>('/currentUser')
  return response.data
}

// ---------------------------------------------------------------------------- self-service

export async function register(
  username: string,
  email: string,
  password: string,
): Promise<void> {
  await api.post('/auth/register', { username, email, password })
}

export async function greeting(): Promise<string> {
  const response = await api.get<{ message: string }>('/hello')
  return response.data.message
}

/**
 * A successful change invalidates **every** session including this one, so the caller must treat the
 * 204 as a logout. That is also why the first boot is three steps.
 */
export async function changePassword(
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  await api.patch('/currentUser/changePassword', { currentPassword, newPassword })
  clearCsrfToken()
}

export async function requestPasswordReset(email: string): Promise<void> {
  await api.post('/auth/password-reset/request', { email })
}

export async function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  await api.post('/auth/password-reset/confirm', { token, newPassword })
  clearCsrfToken()
}

// ---------------------------------------------------------------------------- administration

export async function listUsers(): Promise<UserSummary[]> {
  const response = await api.get<UserSummary[]>('/users')
  return response.data
}

export async function getUser(userId: string): Promise<UserDetail> {
  const response = await api.get<UserDetail>(`/users/${userId}`)
  return response.data
}

export async function listRoles(): Promise<string[]> {
  const response = await api.get<string[]>('/roles')
  return response.data
}

export async function createUser(input: {
  username: string
  email: string
  password: string
  role: string
}): Promise<void> {
  await api.post('/users', input)
}

export async function setUserEnabled(userId: string, enabled: boolean): Promise<void> {
  await api.patch(`/users/${userId}/status`, { enabled })
}

export async function setUserRole(userId: string, role: string): Promise<void> {
  await api.patch(`/users/${userId}/role`, { role })
}

export async function unlockUser(userId: string): Promise<void> {
  // No body at all: an unlock takes no justification field (Qs:384 over :386).
  await api.patch(`/users/${userId}/unlock`)
}

export async function deleteUser(userId: string): Promise<void> {
  await api.delete(`/users/${userId}`)
}

export async function issuePasswordReset(userId: string): Promise<IssuedReset> {
  const response = await api.patch<IssuedReset>(`/users/${userId}/resetPassword`)
  return response.data ?? {}
}
