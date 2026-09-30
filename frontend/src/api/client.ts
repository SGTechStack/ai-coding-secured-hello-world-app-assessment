/**
 * How the SPA reaches the API. Every call is cross-origin and sends credentials, and every
 * state-changing call carries the Session-bound CSRF token from GET /api/csrf (ADR 0002).
 *
 * The one exception is `POST /api/client-events`, which `telemetry.ts` sends itself, without
 * credentials and without a token: fetching one would make the server create a Session for every
 * Visitor before any interaction (ADR 0002 records the exemption).
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
const passwordChangeRequiredListeners = new Set<() => void>()

/**
 * Registers the global reaction to "not logged in" (401). The handler must not show an error: a
 * Visitor simply isn't logged in yet. Returns an unsubscribe function.
 */
export function onSessionEnded(listener: () => void): () => void {
  sessionEndedListeners.add(listener)
  return () => sessionEndedListeners.delete(listener)
}

/**
 * Registers the global reaction to "your password must be changed first" (403
 * `password_change_required`): the API refuses everything but `/me`, the Password Change, logout and
 * `/csrf` until the holder chooses a new password. Returns an unsubscribe function.
 */
export function onPasswordChangeRequired(listener: () => void): () => void {
  passwordChangeRequiredListeners.add(listener)
  return () => passwordChangeRequiredListeners.delete(listener)
}

/**
 * The Session-ended signal from the CSRF bootstrap. A 401 from `GET /api/csrf` means the server has
 * ended the Session (for example, its Account was deleted), exactly as a 401 from any other endpoint
 * does. The bootstrap has already notified the session-ended listeners when this is thrown, so
 * whoever catches it must not notify them again.
 */
class CsrfSessionEnded extends Error {
  readonly problem: Problem | null

  constructor(problem: Problem | null) {
    super('CSRF bootstrap found the Session ended')
    this.problem = problem
  }
}

function notifySessionEnded() {
  sessionEndedListeners.forEach((listener) => listener())
}

/**
 * Fetches a new token, shared by concurrent callers. A 401 notifies the session-ended listeners once
 * per fetch and rejects with {@link CsrfSessionEnded}; any other failure rejects with an `Error`,
 * because a 403 or 5xx from `/csrf` says nothing about the Session and must not become a silent logout.
 */
function fetchCsrfToken(): Promise<CsrfResponse> {
  csrf = null
  csrfRequest = fetch(`${API_BASE}/csrf`, { credentials: 'include', cache: 'no-store' })
    .then(async (response) => {
      if (response.status === 401) {
        const problem = await readProblem(response)
        notifySessionEnded()
        throw new CsrfSessionEnded(problem)
      }
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

/**
 * Fetches a fresh CSRF token. Call after login and logout, when the server issues a new one. A 401
 * ends the Session through the listeners and resolves, like a 401 anywhere else; other failures reject.
 */
export async function refreshCsrfToken(): Promise<void> {
  try {
    await fetchCsrfToken()
  } catch (error) {
    if (!(error instanceof CsrfSessionEnded)) throw error
  }
}

/**
 * Returns the cached CSRF token, fetching one if none is cached yet. Rejects on a 401 too (after
 * notifying the listeners), since there is no token to return; `apiRequest` turns that into a 401 result.
 */
export function ensureCsrfToken(): Promise<CsrfResponse> {
  if (csrf) return Promise.resolve(csrf)
  return csrfRequest ?? fetchCsrfToken()
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
  let response: Response
  try {
    response = await send(path, init, method)
    if (!SAFE_METHODS.has(method) && (await isCsrfRejection(response))) {
      // The cached token belonged to a Session the server has since ended (idle timeout, or a login
      // elsewhere). Fetch a token for the current Session and try once more.
      await fetchCsrfToken()
      response = await send(path, init, method)
    }
  } catch (error) {
    // The bootstrap already ended the Session; answer like any other 401, without notifying twice.
    if (error instanceof CsrfSessionEnded) return { ok: false, status: 401, problem: error.problem }
    throw error
  }

  if (response.ok) {
    return { ok: true, status: response.status, data: (await readBody(response)) as T }
  }

  const problem = await readProblem(response)
  // Rejected login credentials are a 401 too, but no Session has ended.
  if (response.status === 401 && sessionEndedOn401 && problem?.code !== 'authentication_failed') {
    notifySessionEnded()
  }
  // The Session is fine; the Account simply may do nothing else until its password is changed.
  if (response.status === 403 && problem?.code === 'password_change_required') {
    passwordChangeRequiredListeners.forEach((listener) => listener())
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
