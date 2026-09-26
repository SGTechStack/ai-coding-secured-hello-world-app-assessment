/**
 * Fetch wrapper for the Spring Boot API on the second origin.
 *
 * Always sends cookies (`credentials: 'include'`) — the session id lives in an
 * HttpOnly cookie. CSRF uses the Synchronizer Token pattern: the token is held
 * in the server-side session and delivered only in the JSON body of
 * `GET /api/auth/csrf`. It is kept here in memory (never in a cookie or
 * storage) and sent as the `X-XSRF-TOKEN` header on every mutating request.
 * Login and logout rotate/clear the server copy, so both re-bootstrap.
 */
export const API_BASE: string =
  import.meta.env.VITE_API_BASE ?? 'http://localhost:8080'

/** In-memory CSRF token for the current server session; null until fetched. */
let csrfToken: string | null = null

/** Problem title the API uses for a missing/invalid CSRF token (403). */
const CSRF_REJECTED_TITLE = 'Invalid CSRF token'

function isMutating(method: string): boolean {
  return method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS'
}

/** True when a 403 is the API's CSRF rejection, not an authorization denial. */
async function isCsrfRejection(response: Response): Promise<boolean> {
  if (response.status !== 403) {
    return false
  }
  try {
    const body = (await response.clone().json()) as { title?: string }
    return body.title === CSRF_REJECTED_TITLE
  } catch {
    return false
  }
}

function send(path: string, init: RequestInit, method: string): Promise<Response> {
  const headers = new Headers(init.headers)
  if (isMutating(method) && csrfToken) {
    headers.set('X-XSRF-TOKEN', csrfToken)
  }
  return fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    ...init,
    headers,
  })
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase()
  if (!isMutating(method)) {
    return send(path, init, method)
  }
  if (!csrfToken) {
    await bootstrapCsrf()
  }
  const response = await send(path, init, method)
  // The server-side token disappears with its session (idle expiry, a
  // restart, an admin action). The request was rejected by the CSRF filter
  // before any handler ran, so fetching a fresh token and retrying once is
  // safe.
  if (await isCsrfRejection(response)) {
    await bootstrapCsrf()
    return send(path, init, method)
  }
  return response
}

/** Error carrying the RFC 7807 detail message when the API provides one. */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function problemDetail(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { detail?: string; title?: string }
    return body.detail ?? body.title ?? `Request failed (${response.status})`
  } catch {
    return `Request failed (${response.status})`
  }
}

function jsonRequest(method: string, path: string, body: unknown): Promise<Response> {
  return apiFetch(path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

function postJson(path: string, body: unknown): Promise<Response> {
  return jsonRequest('POST', path, body)
}

function patchJson(path: string, body: unknown): Promise<Response> {
  return jsonRequest('PATCH', path, body)
}

/** Account roles — mirrors the server's `Role` enum. */
export type Role = 'USER' | 'ADMIN'

/** Current principal — the ratified `{username, role}` shape. */
export interface Principal {
  username: string
  role: Role
}

export interface HelloResponse {
  message: string
}

/**
 * Fetches a CSRF token for the current server session and keeps it in memory.
 * Called at app start and after login/logout — the token is rotated on each —
 * and automatically by {@link apiFetch} when the server rejects a stale one.
 */
export async function bootstrapCsrf(): Promise<void> {
  // Share one in-flight fetch: concurrent first requests would otherwise
  // each create a server session and race for the SESSION cookie.
  csrfBootstrap ??= fetchCsrfToken().finally(() => {
    csrfBootstrap = null
  })
  return csrfBootstrap
}

let csrfBootstrap: Promise<void> | null = null

async function fetchCsrfToken(): Promise<void> {
  csrfToken = null
  const response = await fetch(`${API_BASE}/api/auth/csrf`, { credentials: 'include' })
  if (!response.ok) {
    throw new ApiError(response.status, `CSRF bootstrap failed (${response.status})`)
  }
  csrfToken = ((await response.json()) as { token: string }).token
}

/** Session probe — null when anonymous (401), the principal otherwise. */
export async function getMe(): Promise<Principal | null> {
  const response = await apiFetch('/api/auth/me')
  if (response.status === 401) {
    return null
  }
  if (!response.ok) {
    throw new ApiError(response.status, await problemDetail(response))
  }
  return (await response.json()) as Principal
}

export async function register(
  username: string,
  email: string,
  password: string,
): Promise<Principal> {
  const response = await postJson('/api/auth/register', { username, email, password })
  if (!response.ok) {
    throw new ApiError(response.status, await problemDetail(response))
  }
  return (await response.json()) as Principal
}

export async function login(username: string, password: string): Promise<Principal> {
  const response = await postJson('/api/auth/login', { username, password })
  if (!response.ok) {
    throw new ApiError(response.status, await problemDetail(response))
  }
  // Login rotates the CSRF token — re-bootstrap so later mutations succeed.
  await bootstrapCsrf()
  return (await response.json()) as Principal
}

/**
 * Ends the server-side session — the session row is deleted, so a replayed
 * cookie is rejected. The server also clears the CSRF token on logout
 * (CsrfLogoutHandler), so re-bootstrap afterwards: the next mutation (e.g.
 * signing back in) needs a fresh token.
 */
export async function logout(): Promise<void> {
  const response = await apiFetch('/api/auth/logout', { method: 'POST' })
  if (!response.ok) {
    throw new ApiError(response.status, await problemDetail(response))
  }
  await bootstrapCsrf()
}

export async function getHello(): Promise<HelloResponse> {
  const response = await apiFetch('/api/hello')
  if (!response.ok) {
    throw new ApiError(response.status, await problemDetail(response))
  }
  return response.json() as Promise<HelloResponse>
}

/**
 * Asks for a reset link. The API always answers with the same generic
 * message — registered or not — so the response is enumeration-safe and can
 * be shown verbatim. (There is no SMTP: the link lands in the API's logs.)
 */
export async function requestPasswordReset(email: string): Promise<string> {
  const response = await postJson('/api/auth/password-reset/request', { email })
  if (!response.ok) {
    throw new ApiError(response.status, await problemDetail(response))
  }
  return ((await response.json()) as { message: string }).message
}

/**
 * Consumes a single-use reset token. 400 with a generic detail for unknown,
 * expired, or spent tokens; 400 "Password too short" under the length
 * policy — the ApiError message is shown verbatim.
 */
export async function confirmPasswordReset(
  token: string,
  newPassword: string,
): Promise<void> {
  const response = await postJson('/api/auth/password-reset/confirm', {
    token,
    newPassword,
  })
  if (!response.ok) {
    throw new ApiError(response.status, await problemDetail(response))
  }
}

// ---------------------------------------------------------------------
// Admin user management — every call requires an ADMIN session; a USER
// gets 403 from the API. All mutations carry the CSRF header via apiFetch.
// ---------------------------------------------------------------------

/** Admin user-list row — the ratified DTO shape (never a password hash). */
export interface AdminUser {
  id: number
  username: string
  email: string
  role: Role
  enabled: boolean
  createdAt: string
}

export async function listAdminUsers(): Promise<AdminUser[]> {
  const response = await apiFetch('/api/admin/users')
  if (!response.ok) {
    throw new ApiError(response.status, await problemDetail(response))
  }
  return response.json() as Promise<AdminUser[]>
}

/** Enable/disable another account — returns the updated row. */
export async function setUserEnabled(id: number, enabled: boolean): Promise<AdminUser> {
  const response = await patchJson(`/api/admin/users/${id}/status`, { enabled })
  if (!response.ok) {
    throw new ApiError(response.status, await problemDetail(response))
  }
  return response.json() as Promise<AdminUser>
}

/** Switch a role between USER and ADMIN — returns the updated row. */
export async function setUserRole(id: number, role: Role): Promise<AdminUser> {
  const response = await patchJson(`/api/admin/users/${id}/role`, { role })
  if (!response.ok) {
    throw new ApiError(response.status, await problemDetail(response))
  }
  return response.json() as Promise<AdminUser>
}

/** Remove an account entirely (204). */
export async function deleteAdminUser(id: number): Promise<void> {
  const response = await apiFetch(`/api/admin/users/${id}`, { method: 'DELETE' })
  if (!response.ok) {
    throw new ApiError(response.status, await problemDetail(response))
  }
}
