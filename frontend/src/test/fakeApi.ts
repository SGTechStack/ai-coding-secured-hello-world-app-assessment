import { vi } from 'vitest'

/** The API base URL the SPA calls in tests (`VITE_API_ORIGIN` from vitest.config.ts + `{base}`). */
const API = 'http://localhost:8080/api/v1'

interface ApiRequest {
  method: string
  path: string
  url: string
  headers: Headers
  body: unknown
  init?: RequestInit
}

type Handler = (request: ApiRequest) => Response | Promise<Response>

/** Routes keyed by `"<METHOD> <path relative to {base}>"`, e.g. `"POST /auth/login"`. */
export type ApiRoutes = Record<string, Handler>

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

export function textResponse(text: string, status = 200): Response {
  return new Response(text, { status, headers: { 'Content-Type': 'text/plain;charset=UTF-8' } })
}

export function emptyResponse(status: number): Response {
  return new Response(null, { status })
}

function urlOf(input: RequestInfo | URL): string {
  if (typeof input === 'string') return input
  if (input instanceof URL) return input.href
  return input.url
}

function parseBody(init?: RequestInit): unknown {
  if (typeof init?.body !== 'string') return undefined
  try {
    return JSON.parse(init.body) as unknown
  } catch {
    return init.body
  }
}

/**
 * Stubs global fetch with a fake API. Every request must target {@link API}; an unrouted request
 * fails the test. `GET /csrf` answers with a token unless the routes override it.
 */
export function stubApi(routes: ApiRoutes) {
  const requests: ApiRequest[] = []
  const all: ApiRoutes = {
    'GET /csrf': () => jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf-token' }),
    ...routes,
  }
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = urlOf(input)
    if (!url.startsWith(`${API}/`)) throw new Error(`Request outside the API: ${url}`)
    const method = (init?.method ?? (input instanceof Request ? input.method : 'GET')).toUpperCase()
    const path = url.slice(API.length)
    const request: ApiRequest = {
      method,
      path,
      url,
      headers: new Headers(init?.headers),
      body: parseBody(init),
      init,
    }
    requests.push(request)
    const handler = all[`${method} ${path}`] ?? all[`${method} ${path.split('?')[0]}`]
    if (!handler) throw new Error(`Unexpected request: ${method} ${path}`)
    return handler(request)
  })
  vi.stubGlobal('fetch', fetchMock)
  return {
    fetchMock,
    requests,
    /** Requests matching `"<METHOD> <path>"`. */
    calls: (key: string) =>
      requests.filter((request) => `${request.method} ${request.path}` === key),
  }
}
