import { vi } from 'vitest'

export const API = 'http://api.test/api'

export type Route = (init: RequestInit) => Response | Promise<Response>

export function json(status: number, body: unknown, contentType = 'application/json'): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } })
}

export function problem(status: number, code: string): Response {
  return json(status, { status, code }, 'application/problem+json')
}

/**
 * Replaces the network (global fetch) with a fake API keyed by "METHOD path". Returns the spy so
 * tests can inspect what the SPA actually sent.
 */
export function fakeApi(routes: Record<string, Route>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init = {}) => {
    const url = String(input)
    const method = (init.method ?? 'GET').toUpperCase()
    const route = routes[`${method} ${url.replace(API, '')}`]
    if (!route) throw new Error(`Unexpected request ${method} ${url}`)
    return route(init)
  })
}

export const csrfRoute =
  (token = 'token-1'): Route =>
  () =>
    json(200, { headerName: 'X-CSRF-TOKEN', token })
