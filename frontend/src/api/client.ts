/**
 * The only module that talks to the backend.
 *
 * Authentication is an HttpOnly session cookie the browser sends automatically
 * (`credentials: 'include'`); JavaScript never sees it. The CSRF token is kept in memory
 * only (never localStorage) and sent in a header on every state-changing request.
 */

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/+$/, '')

type HttpMethod = 'GET' | 'POST' | 'PATCH' | 'DELETE'

interface CsrfToken {
  headerName: string
  token: string
}

/** Problem details (RFC 9457) as returned by the API. */
interface Problem {
  status?: number
  detail?: string
  code?: string
  errors?: Record<string, string>
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string | undefined
  readonly fieldErrors: Record<string, string>
  readonly retryAfterSeconds: number | undefined

  constructor(status: number, problem: Problem, retryAfterSeconds?: number) {
    super(problem.detail ?? `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = problem.code
    this.fieldErrors = problem.errors ?? {}
    this.retryAfterSeconds = retryAfterSeconds
  }
}

let csrfToken: CsrfToken | null = null

const unauthenticatedListeners = new Set<() => void>()

/**
 * Registers a callback for any response saying the request had no valid session (expired,
 * revoked, or signed out elsewhere), so one place reacts instead of every page.
 * @returns a function that unregisters the callback
 */
export function onUnauthenticated(listener: () => void): () => void {
  unauthenticatedListeners.add(listener)
  return () => {
    unauthenticatedListeners.delete(listener)
  }
}

/** The server rotates the token on login and discards it on logout. */
export function forgetCsrfToken(): void {
  csrfToken = null
}

async function loadCsrfToken(): Promise<CsrfToken> {
  csrfToken = await send<CsrfToken>('GET', '/api/auth/csrf')
  return csrfToken
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: Problem = {}
  try {
    problem = (await response.json()) as Problem
  } catch {
    // Not a problem-details body (e.g. a proxy error page); fall back to the status alone.
  }
  const retryAfter = Number(response.headers.get('Retry-After'))
  return new ApiError(response.status, problem, Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : undefined)
}

async function send<T>(method: HttpMethod, path: string, body?: unknown, headers: Record<string, string> = {}): Promise<T> {
  const init: RequestInit = { method, credentials: 'include', headers: { ...headers } }
  if (body !== undefined) {
    init.headers = { ...headers, 'Content-Type': 'application/json' }
    init.body = JSON.stringify(body)
  }
  const response = await fetch(`${API_BASE_URL}${path}`, init)
  if (!response.ok) {
    const error = await toApiError(response)
    if (error.code === 'UNAUTHENTICATED') {
      unauthenticatedListeners.forEach((listener) => listener())
    }
    throw error
  }
  if (response.status === 204) {
    return undefined as T
  }
  const contentType = response.headers.get('Content-Type') ?? ''
  return (contentType.includes('json') ? await response.json() : await response.text()) as T
}

async function request<T>(method: HttpMethod, path: string, body?: unknown): Promise<T> {
  if (method === 'GET') {
    return send<T>(method, path)
  }
  const token = csrfToken ?? (await loadCsrfToken())
  try {
    return await send<T>(method, path, body, { [token.headerName]: token.token })
  } catch (error) {
    // The token dies with its session (expiry, logout elsewhere): fetch a fresh one and retry once.
    if (error instanceof ApiError && error.code === 'CSRF_INVALID') {
      const fresh = await loadCsrfToken()
      return send<T>(method, path, body, { [fresh.headerName]: fresh.token })
    }
    throw error
  }
}

export type Role = 'USER' | 'ADMIN'

export interface CurrentUser {
  id: string
  username: string
  role: Role
}

export interface AdminUser {
  id: string
  username: string
  email: string
  role: Role
  enabled: boolean
  createdAt: string
}

export interface UserPage {
  items: AdminUser[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export const api = {
  me: () => request<CurrentUser>('GET', '/api/me'),

  login: async (username: string, password: string) => {
    const user = await request<CurrentUser>('POST', '/api/auth/login', { username, password })
    forgetCsrfToken()
    return user
  },

  logout: async () => {
    try {
      await request<void>('POST', '/api/auth/logout')
    } finally {
      forgetCsrfToken()
    }
  },

  register: (username: string, email: string, password: string) =>
    request<CurrentUser>('POST', '/api/auth/register', { username, email, password }),

  requestPasswordReset: (email: string) =>
    request<{ message: string }>('POST', '/api/auth/password-reset/request', { email }),

  confirmPasswordReset: (token: string, newPassword: string) =>
    request<void>('POST', '/api/auth/password-reset/confirm', { token, newPassword }),

  hello: () => request<string>('GET', '/api/hello'),

  listUsers: (page: number, size = 20) => request<UserPage>('GET', `/api/admin/users?page=${page}&size=${size}`),

  setUserEnabled: (id: string, enabled: boolean) =>
    request<AdminUser>('PATCH', `/api/admin/users/${encodeURIComponent(id)}/status`, { enabled }),

  setUserRole: (id: string, role: Role) =>
    request<AdminUser>('PATCH', `/api/admin/users/${encodeURIComponent(id)}/role`, { role }),

  deleteUser: (id: string) => request<void>('DELETE', `/api/admin/users/${encodeURIComponent(id)}`),
}
