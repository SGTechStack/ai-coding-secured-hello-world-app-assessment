import { beforeEach, describe, expect, it, vi } from 'vitest'
import { csrfRoute, fakeApi, json, problem } from '../test/fakeApi'

// The client caches the CSRF token at module level, so each test gets a fresh module.
let client: typeof import('./client')
beforeEach(async () => {
  vi.resetModules()
  client = await import('./client')
})

const headersOf = (init: RequestInit | undefined) => new Headers(init?.headers)

describe('API client', () => {
  it('sends credentials on a GET without fetching or attaching a CSRF token', async () => {
    const fetch = fakeApi({ 'GET /hello': () => json(200, { greeting: 'hi' }) })

    const result = await client.apiRequest<{ greeting: string }>('/hello')

    expect(result).toEqual({ ok: true, status: 200, data: { greeting: 'hi' } })
    expect(fetch).toHaveBeenCalledTimes(1)
    const [url, init] = fetch.mock.calls[0]
    expect(url).toBe('http://api.test/api/hello')
    expect(init?.credentials).toBe('include')
    expect(headersOf(init).has('X-CSRF-TOKEN')).toBe(false)
  })

  it('attaches the cached CSRF token to state-changing requests, fetching it only once', async () => {
    const fetch = fakeApi({
      'GET /csrf': csrfRoute('token-1'),
      'POST /things': () => new Response(null, { status: 204 }),
    })

    const first = await client.apiRequest('/things', { method: 'POST' })
    await client.apiRequest('/things', { method: 'post' })

    expect(first).toEqual({ ok: true, status: 204, data: null })
    const csrfCalls = fetch.mock.calls.filter(([url]) => String(url).endsWith('/csrf'))
    expect(csrfCalls).toHaveLength(1)
    expect(csrfCalls[0][1]).toMatchObject({ credentials: 'include', cache: 'no-store' })
    const posts = fetch.mock.calls.filter(([, init]) => init?.method === 'POST')
    expect(posts.map(([, init]) => headersOf(init).get('X-CSRF-TOKEN'))).toEqual(['token-1', 'token-1'])
    expect(posts.every(([, init]) => init?.credentials === 'include')).toBe(true)
  })

  it('uses a new token after refreshCsrfToken, as after login or logout', async () => {
    let issued = 0
    const fetch = fakeApi({
      'GET /csrf': () => json(200, { headerName: 'X-CSRF-TOKEN', token: `token-${++issued}` }),
      'DELETE /things': () => new Response(null, { status: 204 }),
    })

    await client.ensureCsrfToken()
    await client.refreshCsrfToken()
    await client.apiRequest('/things', { method: 'DELETE' })

    const [, init] = fetch.mock.calls.at(-1)!
    expect(headersOf(init).get('X-CSRF-TOKEN')).toBe('token-2')
  })

  it('fetches a new CSRF token and retries once when the server rejects a stale one', async () => {
    let issued = 0
    const fetch = fakeApi({
      'GET /csrf': () => json(200, { headerName: 'X-CSRF-TOKEN', token: `token-${++issued}` }),
      'POST /things': (init) =>
        new Headers(init.headers).get('X-CSRF-TOKEN') === 'token-2'
          ? new Response(null, { status: 204 })
          : problem(403, 'csrf_invalid'),
    })

    const result = await client.apiRequest('/things', { method: 'POST' })

    expect(result).toEqual({ ok: true, status: 204, data: null })
    const posts = fetch.mock.calls.filter(([, init]) => init?.method === 'POST')
    expect(posts.map(([, init]) => headersOf(init).get('X-CSRF-TOKEN'))).toEqual(['token-1', 'token-2'])
  })

  it('retries a rejected CSRF token only once', async () => {
    const fetch = fakeApi({ 'GET /csrf': csrfRoute(), 'POST /things': () => problem(403, 'csrf_invalid') })

    const result = await client.apiRequest('/things', { method: 'POST' })

    expect(result).toMatchObject({ ok: false, status: 403, problem: { code: 'csrf_invalid' } })
    expect(fetch.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(2)
  })

  it('does not retry other 403s', async () => {
    const fetch = fakeApi({ 'GET /csrf': csrfRoute(), 'POST /things': () => problem(403, 'access_denied') })

    await client.apiRequest('/things', { method: 'POST' })

    expect(fetch.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(1)
  })

  it('shares one in-flight CSRF request between concurrent callers', async () => {
    const fetch = fakeApi({ 'GET /csrf': csrfRoute() })

    const [a, b] = await Promise.all([client.ensureCsrfToken(), client.ensureCsrfToken()])

    expect(a).toEqual(b)
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('rejects when the CSRF bootstrap fails', async () => {
    fakeApi({ 'GET /csrf': () => problem(500, 'internal_error') })

    await expect(client.ensureCsrfToken()).rejects.toThrow('CSRF bootstrap failed with status 500')
  })

  describe('when GET /csrf answers 401 (the Session ended on the server)', () => {
    it('ends the Session once and returns the 401 instead of rejecting, with no cached token', async () => {
      const fetch = fakeApi({ 'GET /csrf': () => problem(401, 'authentication_required') })
      const listener = vi.fn()
      client.onSessionEnded(listener)

      const result = await client.apiRequest('/things', { method: 'POST' })

      expect(result).toEqual({ ok: false, status: 401, problem: { status: 401, code: 'authentication_required' } })
      expect(listener).toHaveBeenCalledOnce()
      // The state-changing request itself is never sent without a token.
      expect(fetch.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(0)
    })

    it('ends the Session once on the stale-token retry path, without rejecting', async () => {
      let csrfCalls = 0
      fakeApi({
        'GET /csrf': () => (++csrfCalls === 1 ? csrfRoute()({}) : problem(401, 'authentication_required')),
        'POST /things': () => problem(403, 'csrf_invalid'),
      })
      const listener = vi.fn()
      client.onSessionEnded(listener)

      const result = await client.apiRequest('/things', { method: 'POST' })

      expect(result).toMatchObject({ ok: false, status: 401 })
      expect(listener).toHaveBeenCalledOnce()
      expect(csrfCalls).toBe(2)
    })

    it('ends the Session once for concurrent callers sharing one bootstrap', async () => {
      fakeApi({ 'GET /csrf': () => problem(401, 'authentication_required') })
      const listener = vi.fn()
      client.onSessionEnded(listener)

      const results = await Promise.all([
        client.apiRequest('/things', { method: 'POST' }),
        client.apiRequest('/things', { method: 'DELETE' }),
      ])

      expect(results.map((r) => r.status)).toEqual([401, 401])
      expect(listener).toHaveBeenCalledOnce()
    })

    it('ends the Session from refreshCsrfToken without rejecting', async () => {
      fakeApi({ 'GET /csrf': () => problem(401, 'authentication_required') })
      const listener = vi.fn()
      client.onSessionEnded(listener)

      await expect(client.refreshCsrfToken()).resolves.toBeUndefined()
      expect(listener).toHaveBeenCalledOnce()
    })
  })

  it.each([403, 500])('still rejects, without ending the Session, when GET /csrf answers %i', async (status) => {
    fakeApi({ 'GET /csrf': () => problem(status, 'any') })
    const listener = vi.fn()
    client.onSessionEnded(listener)

    await expect(client.apiRequest('/things', { method: 'POST' })).rejects.toThrow(
      `CSRF bootstrap failed with status ${status}`,
    )
    await expect(client.refreshCsrfToken()).rejects.toThrow(`CSRF bootstrap failed with status ${status}`)
    expect(listener).not.toHaveBeenCalled()
  })

  it('treats 401 as "not logged in": notifies listeners and returns the problem', async () => {
    fakeApi({ 'GET /hello': () => problem(401, 'authentication_required') })
    const listener = vi.fn()
    const unsubscribe = client.onSessionEnded(listener)

    const result = await client.apiRequest('/hello')
    unsubscribe()
    await client.apiRequest('/hello')

    expect(result).toEqual({
      ok: false,
      status: 401,
      problem: { status: 401, code: 'authentication_required' },
    })
    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('does not treat other errors as a lost session', async () => {
    fakeApi({ 'GET /hello': () => problem(403, 'access_denied') })
    const listener = vi.fn()
    client.onSessionEnded(listener)

    const result = await client.apiRequest('/hello')

    expect(result).toMatchObject({ ok: false, status: 403, problem: { code: 'access_denied' } })
    expect(listener).not.toHaveBeenCalled()
  })

  it('notifies the global handler when a request is refused until the password is changed', async () => {
    fakeApi({ 'GET /hello': () => problem(403, 'password_change_required') })
    const listener = vi.fn()
    client.onPasswordChangeRequired(listener)

    const result = await client.apiRequest('/hello')

    expect(result).toMatchObject({ ok: false, status: 403, problem: { code: 'password_change_required' } })
    expect(listener).toHaveBeenCalledOnce()
  })

  it('does not treat another 403 as a required password change, and unsubscribes cleanly', async () => {
    fakeApi({ 'GET /hello': () => problem(403, 'access_denied') })
    const listener = vi.fn()
    const unsubscribe = client.onPasswordChangeRequired(listener)

    await client.apiRequest('/hello')
    unsubscribe()
    await client.apiRequest('/hello')

    expect(listener).not.toHaveBeenCalled()
  })

  it('returns text bodies as text', async () => {
    fakeApi({ 'GET /hello': () => new Response('Hello, testuser123', { headers: { 'Content-Type': 'text/plain' } }) })

    expect(await client.apiRequest('/hello')).toEqual({ ok: true, status: 200, data: 'Hello, testuser123' })
  })

  it('returns a null problem for error bodies that are not JSON or cannot be parsed', async () => {
    fakeApi({
      'GET /text-error': () => new Response('Forbidden', { status: 403, headers: { 'Content-Type': 'text/plain' } }),
      'GET /no-type': () => new Response(null, { status: 502 }),
      'GET /bad-json': () =>
        new Response('{not json', { status: 500, headers: { 'Content-Type': 'application/problem+json' } }),
    })

    expect(await client.apiRequest('/text-error')).toEqual({ ok: false, status: 403, problem: null })
    expect(await client.apiRequest('/no-type')).toEqual({ ok: false, status: 502, problem: null })
    expect(await client.apiRequest('/bad-json')).toEqual({ ok: false, status: 500, problem: null })
  })

  it('returns an empty-typed body as text', async () => {
    fakeApi({ 'GET /plain': () => new Response('ok') })

    expect(await client.apiRequest('/plain')).toEqual({ ok: true, status: 200, data: 'ok' })
  })
})
