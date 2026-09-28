import { ApiError, isProblem, UnexpectedResponseError } from './errors'

/**
 * The headers every API request carries. The server's entry point is configured against exactly this `Accept`
 * (T-AUTH-015): change it and the backend test that pins it must change too. No `X-Requested-With`.
 */
export const REQUEST_HEADERS: Readonly<Record<string, string>> = Object.freeze({
  Accept: 'application/json, application/problem+json',
})

/** Builds an absolute API URL from a path under `/api`. */
export function apiUrl(path: string): string {
  return new URL(path, import.meta.env.VITE_API_ORIGIN).toString()
}

/**
 * Calls the API with the session cookie and the fixed headers. Resolves with the parsed JSON body (or `undefined` for
 * an empty one), and rejects with {@link ApiError} for an envelope or {@link UnexpectedResponseError} for any other
 * failure status. A network failure rejects with the `fetch` error unchanged.
 */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
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
