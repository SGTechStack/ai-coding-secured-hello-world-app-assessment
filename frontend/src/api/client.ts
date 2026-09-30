// Credentialed API client: every request sends cookies (`credentials: "include"`), and every
// state-changing request echoes the XSRF-TOKEN cookie back as a header, per the backend's
// cookie-based CSRF configuration (SecurityConfig, ticket 01). This is the ONLY place that logic
// lives, so every page/feature gets it for free rather than re-implementing it per call site.

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fields?: Record<string, string>

  constructor(status: number, code: string, message: string, fields?: Record<string, string>) {
    super(message)
    this.status = status
    this.code = code
    this.fields = fields
  }
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(^|; )' + name + '=([^;]*)'))
  return match ? decodeURIComponent(match[2]) : null
}

/** Called once at app startup so the XSRF-TOKEN cookie exists before any state-changing call. */
export async function primeCsrfCookie(): Promise<void> {
  await fetch(`${BASE_URL}/api/csrf`, { credentials: 'include' })
}

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)

  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (!SAFE_METHODS.has(method)) {
    const csrfToken = readCookie('XSRF-TOKEN')
    if (csrfToken) {
      headers.set('X-XSRF-TOKEN', csrfToken)
    }
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    method,
    headers,
    credentials: 'include',
  })

  if (response.status === 204) {
    return undefined as T
  }

  const isJson = response.headers.get('content-type')?.includes('application/json')
  const body = isJson ? await response.json() : undefined

  if (!response.ok) {
    throw new ApiError(
      response.status,
      body?.error ?? 'UNKNOWN_ERROR',
      body?.message ?? 'Something went wrong',
      body?.fields,
    )
  }

  return body as T
}

export const api = {
  get: <T>(path: string) => apiFetch<T>(path),
  post: <T>(path: string, data?: unknown) =>
    apiFetch<T>(path, { method: 'POST', body: data !== undefined ? JSON.stringify(data) : undefined }),
  patch: <T>(path: string, data?: unknown) =>
    apiFetch<T>(path, { method: 'PATCH', body: data !== undefined ? JSON.stringify(data) : undefined }),
  delete: <T>(path: string) => apiFetch<T>(path, { method: 'DELETE' }),
}
