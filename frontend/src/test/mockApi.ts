import { vi } from 'vitest'

export interface RecordedRequest {
  method: string
  path: string
  headers: Record<string, string>
  body: unknown
  credentials: RequestCredentials | undefined
}

type Handler = (request: RecordedRequest) => Response | Promise<Response>

export function json(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  const contentType = status >= 400 ? 'application/problem+json' : 'application/json'
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType, ...headers } })
}

export function problem(status: number, code: string, detail: string, extra: Record<string, unknown> = {}) {
  return json(status, { status, code, detail, ...extra })
}

export const csrfResponse = () => json(200, { headerName: 'X-CSRF-TOKEN', token: 'csrf-1' })

/**
 * Stubs global fetch with handlers keyed by "METHOD /path". Unexpected requests fail the test.
 */
export function mockApi(routes: Record<string, Handler>) {
  const requests: RecordedRequest[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
      const url = new URL(String(input))
      const request: RecordedRequest = {
        method: init.method ?? 'GET',
        path: url.pathname + url.search,
        headers: (init.headers ?? {}) as Record<string, string>,
        body: typeof init.body === 'string' ? JSON.parse(init.body) : undefined,
        credentials: init.credentials,
      }
      requests.push(request)
      const handler = routes[`${request.method} ${url.pathname}`]
      if (!handler) {
        throw new Error(`Unexpected request: ${request.method} ${request.path}`)
      }
      return handler(request)
    }),
  )
  return requests
}
