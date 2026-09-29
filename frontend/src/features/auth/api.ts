import { apiFetch, clearCsrfToken } from '../../shared/api/http.ts'
import type { Role } from '../../shared/api/role.ts'

export interface CurrentUser {
  username: string
  role: Role
}

export type CurrentUserResult =
  { status: 'authenticated'; user: CurrentUser } | { status: 'anonymous' }

export type LoginResult =
  | { status: 'success'; user: CurrentUser }
  | { status: 'invalidCredentials' }
  | { status: 'unavailable' }

export type LogoutResult =
  | { status: 'success' }
  | { status: 'forbidden' }
  | { status: 'unauthorized' }
  | { status: 'unavailable' }

export async function getCurrentUser(): Promise<CurrentUserResult> {
  const response = await apiFetch('/auth/me', {}, { expectedUnauthorized: true })
  if (response.status === 401) return { status: 'anonymous' }
  if (!response.ok) throw new Error(`Current-user request failed with ${response.status}`)
  return { status: 'authenticated', user: (await response.json()) as CurrentUser }
}

/** The server's greeting for the signed-in account, e.g. "Hello, johndoe"; null without a session. */
export async function getGreeting(): Promise<string | null> {
  const response = await apiFetch('/hello', {}, { expectedUnauthorized: true })
  if (response.status === 401) return null
  if (!response.ok) throw new Error(`Greeting request failed with ${response.status}`)
  return response.text()
}

export async function login(username: string, password: string): Promise<LoginResult> {
  try {
    const response = await apiFetch(
      '/auth/login',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      },
      { expectedUnauthorized: true },
    )
    if (response.status === 400 || response.status === 401) {
      return { status: 'invalidCredentials' }
    }
    if (!response.ok) return { status: 'unavailable' }
    return { status: 'success', user: (await response.json()) as CurrentUser }
  } catch {
    return { status: 'unavailable' }
  } finally {
    clearCsrfToken()
  }
}

export async function logout(): Promise<LogoutResult> {
  try {
    const response = await apiFetch('/auth/logout', { method: 'POST' })
    if (response.status === 204) return { status: 'success' }
    if (response.status === 403) return { status: 'forbidden' }
    if (response.status === 401) return { status: 'unauthorized' }
    return { status: 'unavailable' }
  } catch {
    return { status: 'unavailable' }
  } finally {
    clearCsrfToken()
  }
}
