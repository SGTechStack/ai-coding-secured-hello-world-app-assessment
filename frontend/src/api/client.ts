import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { IN_APP_FORBIDDEN_CODES, type ProblemDetail } from './errors'

/**
 * The single axios instance, its CSRF bootstrap, and the global 401/403 interceptor.
 *
 * axios specifically, rather than fetch: `Std:438` requires a global interceptor that distinguishes a
 * 401 from a 403, and axios is the library the standard's own recipe is written against.
 */

/** Same-origin through the Vite dev proxy, and same-origin again when the built bundle is served. */
export const API_BASE = '/api/v1'

/**
 * `withCredentials` is required even same-origin-looking, because the dev server and the API are
 * different ports as far as XHR is concerned until the proxy rewrites it.
 */
export const api: AxiosInstance = axios.create({
  baseURL: API_BASE,
  withCredentials: true,
  headers: { Accept: 'application/json' },
})

// ---------------------------------------------------------------------------- CSRF

interface CsrfTokenResponse {
  token: string
  headerName: string
  parameterName: string
}

/**
 * The cached token and the header it goes in.
 *
 * Cached rather than fetched per call because the token is bound to the session and does not change
 * while the session lives. It is cleared whenever the session id rotates — login, logout and a password
 * change all do that — which is why {@link clearCsrfToken} exists and is called from those three places.
 *
 * There is no `XSRF-TOKEN` cookie to read: `CookieCsrfTokenRepository` is strictly prohibited
 * (`Std:238`), so `GET /csrf` is the only source, and axios's built-in `xsrfCookieName` support is
 * useless here. That is why this is hand-rolled rather than configured.
 */
let csrfToken: string | null = null
let csrfHeaderName = 'X-CSRF-TOKEN'

export function clearCsrfToken(): void {
  csrfToken = null
}

/** Fetches a token, replacing any cached one. */
export async function fetchCsrfToken(): Promise<string> {
  const response = await axios.get<CsrfTokenResponse>(`${API_BASE}/csrf`, {
    withCredentials: true,
  })
  csrfToken = response.data.token
  // Read from the response rather than hard-coded: the backend returns it so no client has to guess.
  csrfHeaderName = response.data.headerName || csrfHeaderName
  return csrfToken
}

const MUTATING_METHODS = new Set(['post', 'put', 'patch', 'delete'])

function isMutating(config: InternalAxiosRequestConfig): boolean {
  return MUTATING_METHODS.has((config.method ?? 'get').toLowerCase())
}

/**
 * Attaches a CSRF token to every mutating request, fetching one first if the cache is empty.
 *
 * **No endpoint in this application is CSRF-exempt** — not login, not register, not either reset
 * endpoint. So this cannot be scoped to authenticated calls: a login with no token is a 403 before it
 * is anything else.
 */
api.interceptors.request.use(async (config) => {
  if (!isMutating(config)) {
    return config
  }
  const token = csrfToken ?? (await fetchCsrfToken())
  config.headers.set(csrfHeaderName, token)
  return config
})

// ---------------------------------------------------------------------------- the 401/403 interceptor

/**
 * What the app does when the session is gone: clear local state and show the login screen.
 *
 * Injected rather than imported so this module stays free of React and of the router. The auth provider
 * installs it on mount.
 */
type SessionLostHandler = () => void

let onSessionLost: SessionLostHandler = () => {}

export function setSessionLostHandler(handler: SessionLostHandler): void {
  onSessionLost = handler
}

/**
 * Requests whose 401 must NOT be treated as losing the session.
 *
 * Logout is CSRF-protected deliberately, which means logging out of an already-expired session answers
 * 401 — and `Std:438` forbids "fixing" that. A 401 here means the session is already gone, which is
 * exactly what the caller wanted, so it is absorbed rather than reported. `GET /currentUser` is the
 * other one: during bootstrap a 401 is the normal answer for "nobody is logged in", not a session that
 * was lost mid-use, and routing on it would put the app in a redirect loop on first load.
 */
function isExpectedUnauthenticated(config: InternalAxiosRequestConfig | undefined): boolean {
  const url = config?.url ?? ''
  return url.endsWith('/auth/logout') || url.endsWith('/currentUser')
}

/** Login's own 401 is the credential answer, and the login screen renders it itself. */
function isLogin(config: InternalAxiosRequestConfig | undefined): boolean {
  return (config?.url ?? '').endsWith('/auth/login')
}

/**
 * The global interceptor. **It inspects the code before it acts** — that ordering is the whole point.
 *
 * - A bare **401** is a dead session: clear local state, go to login.
 * - A **403 carrying `PASSWORD_CHANGE_REQUIRED` or `SELF_ACTION_NOT_ALLOWED`** is an in-app condition.
 *   It is rejected to the caller untouched and the user stays logged in. Logging out here would eject a
 *   user who merely needs to change their password, or who clicked the wrong row in the user list.
 * - A **403 with any other code** (`ACCESS_DENIED`) is also surfaced rather than logged out of: the
 *   session is valid, the request was not allowed.
 */
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ProblemDetail>) => {
    const status = error.response?.status
    const code = error.response?.data?.code

    if (status === 401 && !isExpectedUnauthenticated(error.config) && !isLogin(error.config)) {
      // The session is gone, so the token bound to it is gone too.
      clearCsrfToken()
      onSessionLost()
    }

    if (status === 403 && code && IN_APP_FORBIDDEN_CODES.includes(code)) {
      // Deliberately nothing: the caller decides how to show it. Enumerated as a branch rather than
      // left to fall through, because "we considered this and chose to do nothing" is the assertion
      // story 1.23 makes and a fall-through cannot be told apart from an oversight.
      return Promise.reject(error)
    }

    return Promise.reject(error)
  },
)

/** The problem document from a rejected call, or undefined if the failure was not an HTTP error. */
export function problemOf(error: unknown): ProblemDetail | undefined {
  if (axios.isAxiosError<ProblemDetail>(error)) {
    return error.response?.data
  }
  return undefined
}

export function statusOf(error: unknown): number | undefined {
  return axios.isAxiosError(error) ? error.response?.status : undefined
}
