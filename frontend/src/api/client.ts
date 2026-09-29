/**
 * The SPA's only way to reach the API. Every call is cross-origin and sends credentials, and every
 * state-changing call carries the Session-bound CSRF token from GET /api/csrf (ADR 0002).
 */

const API_BASE = `${import.meta.env.VITE_API_ORIGIN}/api`
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

/** RFC 9457 problem body returned by the API for every error. */
export type Problem = {
  status: number
  code: string
  detail?: string
  title?: string
  /** Credential policy rules broken (`password_policy`). */
  violations?: string[]
  /** Names of the input fields that failed (`validation`). */
  fields?: string[]
}

export type ApiResult<T> =
  { ok: true; status: number; data: T } | { ok: false; status: number; problem: Problem | null }

type CsrfResponse = { headerName: string; token: string }

let csrf: CsrfResponse | null = null
let csrfRequest: Promise<CsrfResponse> | null = null
const sessionEndedListeners = new Set<() => void>()

/**
 * Registers the global reaction to "not logged in" (401). The handler must not show an error: a
 * Visitor simply isn't logged in yet. Returns an unsubscribe function.
 */
export function onSessionEnded(listener: () => void): () => void {
  sessionEndedListeners.add(listener)
  return () => sessionEndedListeners.delete(listener)
}

/** Fetches a fresh CSRF token. Call after login and logout, when the server issues a new one. */
export function refreshCsrfToken(): Promise<CsrfResponse> {
  csrf = null
  csrfRequest = fetch(`${API_BASE}/csrf`, { credentials: 'include', cache: 'no-store' })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`CSRF bootstrap failed with status ${response.status}`)
      }
      csrf = (await response.json()) as CsrfResponse
      return csrf
    })
    .finally(() => {
      csrfRequest = null
    })
  return csrfRequest
}

/** Returns the cached CSRF token, fetching one if none is cached yet. */
export function ensureCsrfToken(): Promise<CsrfResponse> {
  if (csrf) return Promise.resolve(csrf)
  return csrfRequest ?? refreshCsrfToken()
}

export type RequestOptions = {
  /**
   * Whether a 401 counts as "Session ended" and notifies the global handler (default true). Off for
   * the start-up `GET /me`, where a 401 just means a Visitor.
   */
  sessionEndedOn401?: boolean
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
  { sessionEndedOn401 = true }: RequestOptions = {},
): Promise<ApiResult<T>> {
  const method = (init.method ?? 'GET').toUpperCase()
  let response = await send(path, init, method)
  if (!SAFE_METHODS.has(method) && (await isCsrfRejection(response))) {
    // The cached token belonged to a Session the server has since ended (idle timeout, or a login
    // elsewhere). Fetch a token for the current Session and try once more.
    await refreshCsrfToken()
    response = await send(path, init, method)
  }

  if (response.ok) {
    return { ok: true, status: response.status, data: (await readBody(response)) as T }
  }

  const problem = await readProblem(response)
  // Rejected login credentials are a 401 too, but no Session has ended.
  if (response.status === 401 && sessionEndedOn401 && problem?.code !== 'authentication_failed') {
    sessionEndedListeners.forEach((listener) => listener())
  }
  return { ok: false, status: response.status, problem }
}

async function send(path: string, init: RequestInit, method: string): Promise<Response> {
  const headers = new Headers(init.headers)
  if (!SAFE_METHODS.has(method)) {
    const token = await ensureCsrfToken()
    headers.set(token.headerName, token.token)
  }
  return fetch(`${API_BASE}${path}`, { ...init, method, headers, credentials: 'include' })
}

async function isCsrfRejection(response: Response): Promise<boolean> {
  if (response.status !== 403) return false
  return (await readProblem(response.clone()))?.code === 'csrf_invalid'
}

async function readBody(response: Response): Promise<unknown> {
  if (response.status === 204) return null
  const type = response.headers.get('Content-Type') ?? ''
  return type.includes('json') ? response.json() : response.text()
}

async function readProblem(response: Response): Promise<Problem | null> {
  const type = response.headers.get('Content-Type') ?? ''
  if (!type.includes('json')) return null
  try {
    return (await response.json()) as Problem
  } catch {
    return null
  }
}
