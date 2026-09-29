const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

/** Shown whenever the API can't be reached or answers unexpectedly. */
export const SERVER_UNAVAILABLE = 'Unable to connect to the server. Please try again later.'

/**
 * The API runs on its own origin (build-time `VITE_API_ORIGIN`, e.g. `http://localhost:8080` in dev),
 * so every request is cross-origin and must include credentials for the SESSION cookie to travel.
 */
export const API_BASE_URL = `${import.meta.env.VITE_API_ORIGIN ?? ''}/api/v1`

interface CsrfToken {
  headerName: string
  token: string
}

interface ApiFetchOptions {
  /** Suppresses redirect handling when a 401 is an expected domain result (login and route guards). */
  expectedUnauthorized?: boolean
}

type UnauthorizedHandler = () => void

// In memory only: never persisted to cookies or storage. Holding the in-flight fetch lets
// concurrent first requests share it.
let csrfToken: Promise<CsrfToken> | undefined
let unauthorizedHandler: UnauthorizedHandler | undefined

/** The absolute URL of an API path relative to `{base}`, e.g. `/auth/me`. */
export function apiUrl(path: string): string {
  return `${API_BASE_URL}${path}`
}

/** Installs the application-level redirect for unexpected 401 responses. */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | undefined): void {
  unauthorizedHandler = handler
}

async function fetchCsrfToken(): Promise<CsrfToken> {
  const response = await fetch(apiUrl('/csrf'), { credentials: 'include' })
  if (!response.ok) {
    throw new Error(`CSRF token request failed with ${response.status}`)
  }
  return (await response.json()) as CsrfToken
}

/** The `message` of an API error body (`{"message": "..."}`), if the body has one. */
export async function errorMessage(response: Response): Promise<string | undefined> {
  try {
    const body = (await response.json()) as { message?: unknown }
    return typeof body.message === 'string' ? body.message : undefined
  } catch {
    return undefined
  }
}

/** Drops the cached CSRF token, so the next state-changing request fetches a fresh one. */
export function clearCsrfToken(): void {
  csrfToken = undefined
}

async function sendWithCsrfToken(url: string, init: RequestInit): Promise<Response> {
  if (!csrfToken) {
    const pending = fetchCsrfToken().catch((error: unknown) => {
      // A newer fetch may have replaced this one after clearCsrfToken(); keep that one.
      if (csrfToken === pending) {
        clearCsrfToken()
      }
      throw error
    })
    csrfToken = pending
  }
  const token = await csrfToken
  const headers = new Headers(init.headers)
  headers.set(token.headerName, token.token)
  return fetch(url, { ...init, headers, credentials: 'include' })
}

/**
 * fetch() for the backend API, given a path relative to `{base}` such as `/auth/me`: sends the
 * session cookie and, on state-changing requests, the session's CSRF token from `{base}/csrf`. A
 * `403` there is retried once with a fresh token. Unexpected final `401`s are handed to the
 * application-wide unauthorized handler.
 */
export async function apiFetch(
  path: string,
  init: RequestInit = {},
  options: ApiFetchOptions = {},
): Promise<Response> {
  const url = apiUrl(path)
  const method = (init.method ?? 'GET').toUpperCase()
  let response: Response
  if (SAFE_METHODS.has(method)) {
    response = await fetch(url, { ...init, credentials: 'include' })
  } else {
    response = await sendWithCsrfToken(url, init)
    if (response.status === 403) {
      clearCsrfToken()
      response = await sendWithCsrfToken(url, init)
    }
  }
  if (response.status === 401) {
    clearCsrfToken()
    if (!options.expectedUnauthorized) unauthorizedHandler?.()
  }
  return response
}
