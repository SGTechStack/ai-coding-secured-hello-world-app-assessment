import { ApiError, isProblem, UnexpectedResponseError } from './errors'

/**
 * The headers every API request carries. The server's entry point is configured against exactly this `Accept`
 * (T-AUTH-015): change it and the backend test that pins it must change too. No `X-Requested-With`.
 */
export const REQUEST_HEADERS: Readonly<Record<string, string>> = Object.freeze({
  Accept: 'application/json, application/problem+json',
})

/** The header the CSRF token travels in. The server reads it from nowhere else (ADR-036). */
export const CSRF_HEADER = 'X-CSRF-TOKEN'

const SAFE_METHODS: ReadonlySet<string> = new Set(['GET', 'HEAD', 'OPTIONS'])
const MAX_NETWORK_RETRIES = 2

/** What `GET /api/csrf` returns. */
interface CsrfToken {
  headerName: string
  token: string
}

/** The session's token, fetched once on first need and shared by concurrent requests. */
let csrfToken: Promise<CsrfToken> | undefined

/** Builds an absolute API URL from a path under `/api`. */
export function apiUrl(path: string): string {
  return new URL(path, import.meta.env.VITE_API_ORIGIN).toString()
}

/** Forgets the token; the next unsafe request fetches a new one. */
export function clearCsrfToken(): void {
  csrfToken = undefined
}

/**
 * Fetches a new token now. Call it after every rotation of the session id, which rotates the token too: sign-in,
 * sign-out, factor verification and enrolment confirmation (ADR-040). The retry in {@link apiFetch} is only the
 * backstop.
 */
export function refreshCsrfToken(): Promise<CsrfToken> {
  clearCsrfToken()
  return bootstrapCsrf()
}

/** Per-call options for {@link apiFetch}. */
export interface ApiFetchOptions {
  /**
   * Whether a `CSRF_TOKEN_INVALID` refusal is retried once after a re-bootstrap; default `true`. Sign-out turns it
   * off: it is terminal on 204, 401 and 403 alike, and never retried (REJ-051; T-FE-017).
   */
  csrfRetry?: boolean
}

/**
 * Calls the API with the session cookie and the fixed headers. Resolves with the parsed JSON body (or `undefined` for
 * an empty one), and rejects with {@link ApiError} for an envelope or {@link UnexpectedResponseError} for any other
 * failure status. A network failure rejects with the `fetch` error unchanged.
 *
 * An unsafe request carries the CSRF token, fetched lazily (ADR-040). If the server still answers
 * `CSRF_TOKEN_INVALID`, the token is fetched again and the request retried once, silently; a second refusal is
 * returned to the caller. A caller that must not be retried passes `{ csrfRetry: false }`.
 */
export async function apiFetch<T>(path: string, init: RequestInit = {}, options: ApiFetchOptions = {}): Promise<T> {
  if (SAFE_METHODS.has((init.method ?? 'GET').toUpperCase())) {
    return send<T>(path, init)
  }
  try {
    return await send<T>(path, withToken(init, await bootstrapCsrf()))
  } catch (error) {
    if (options.csrfRetry === false || !(error instanceof ApiError && error.code === 'CSRF_TOKEN_INVALID')) {
      throw error
    }
    return send<T>(path, withToken(init, await refreshCsrfToken()))
  }
}

/**
 * The retry predicate for queries: only a request that got no status, a network failure, is retried, and only a
 * bounded number of times (REJ-052). Anything the server or a proxy answered is final.
 */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  return failureCount < MAX_NETWORK_RETRIES && !(error instanceof ApiError || error instanceof UnexpectedResponseError)
}

function bootstrapCsrf(): Promise<CsrfToken> {
  csrfToken ??= send<CsrfToken>('/api/csrf').catch((error: unknown) => {
    clearCsrfToken()
    throw error
  })
  return csrfToken
}

function withToken(init: RequestInit, { headerName, token }: CsrfToken): RequestInit {
  const headers = new Headers(init.headers)
  headers.set(headerName, token)
  return { ...init, headers }
}

async function send<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  for (const [name, value] of Object.entries(REQUEST_HEADERS)) {
    headers.set(name, value)
  }
  const response = await fetch(apiUrl(path), { ...init, headers, credentials: 'include' })

  if (!response.ok) {
    const body: unknown = await response.json().catch(() => undefined)
    throw isProblem(body) ? new ApiError(body) : new UnexpectedResponseError(response.status)
  }
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}
