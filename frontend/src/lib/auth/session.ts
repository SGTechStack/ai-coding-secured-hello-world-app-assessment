import type { QueryClient } from '@tanstack/react-query'
import { apiFetch, clearCsrfToken, refreshCsrfToken } from '@/lib/api/client'

/** The self-read, `GET /api/profile`. It drives routing (belief); an envelope `code` overrides it (authority). */
export interface Profile {
  id: string
  username: string
  role: 'USER' | 'ADMIN'
  factors: { held: boolean; required: boolean; enrolled: boolean; rebindRequired: boolean }
}

/** `GET /api/hello`. */
export interface Greeting {
  message: string
}

/** The query key of the self-read. */
export const PROFILE_KEY = ['profile'] as const

export const fetchProfile = () => apiFetch<Profile>('/api/profile')

export const fetchGreeting = () => apiFetch<Greeting>('/api/hello')

/**
 * Signs in and returns the self-read. The login rotated the session id and with it the CSRF token, so the token is
 * fetched again at once rather than left to the retry backstop (ADR-040).
 */
export async function signIn(username: string, password: string): Promise<Profile> {
  const profile = await apiFetch<Profile>('/api/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  await refreshCsrfToken()
  return profile
}

/**
 * Signs out. Terminal whatever the answer: 204, 401 and 403 alike, or no answer at all, end with every piece of local
 * state cleared, and the request is never retried or re-bootstrapped (REJ-051; T-FE-017). The caller routes to
 * sign-in afterwards.
 */
export async function signOut(queryClient: QueryClient): Promise<void> {
  try {
    await apiFetch('/api/logout', { method: 'POST' }, { csrfRetry: false })
  } catch {
    // Terminal: a refused or failed sign-out still ends the local session.
  } finally {
    clearCsrfToken()
    queryClient.clear()
  }
}

/**
 * Changes the signed-in user's password, `PATCH /api/profile/password` (ADR-008). The current password is always
 * required. The server rotated the session id and with it the CSRF token, so the token is fetched again at once.
 */
export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await apiFetch<void>('/api/profile/password', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ currentPassword, newPassword }),
  })
  await refreshCsrfToken()
}
