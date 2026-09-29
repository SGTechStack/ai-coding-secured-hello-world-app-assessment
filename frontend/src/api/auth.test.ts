import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fakeApi, json, problem } from '../test/fakeApi'

// The client caches the CSRF token at module level, so each test gets fresh modules.
let auth: typeof import('./auth')
let client: typeof import('./client')
beforeEach(async () => {
  vi.resetModules()
  client = await import('./client')
  auth = await import('./auth')
})

const account = {
  id: '00000000-0000-0000-0000-000000000123',
  username: 'testuser123',
  email: 'testuser123@test.example.com',
  role: 'USER',
  passwordChangeRequired: false,
}

/** A CSRF route that issues token-1, token-2, … so tests can see when a new one was fetched. */
function rotatingCsrf() {
  let issued = 0
  return () => json(200, { headerName: 'X-CSRF-TOKEN', token: `token-${++issued}` })
}

const csrfHeaderOf = (init: RequestInit | undefined) => new Headers(init?.headers).get('X-CSRF-TOKEN')

describe('login', () => {
  it('posts the credentials as JSON with the CSRF token, then fetches a new token', async () => {
    const fetch = fakeApi({
      'GET /csrf': rotatingCsrf(),
      'POST /login': () => json(200, account),
      'POST /logout': () => new Response(null, { status: 200 }),
    })

    const result = await auth.login('testuser123', 'Synthetic-Pass-42')

    expect(result).toEqual({ ok: true, account })
    const [, init] = fetch.mock.calls.find(([url]) => String(url).endsWith('/login'))!
    expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json')
    expect(csrfHeaderOf(init)).toBe('token-1')
    expect(JSON.parse(String(init?.body))).toEqual({ username: 'testuser123', password: 'Synthetic-Pass-42' })

    await client.apiRequest('/logout', { method: 'POST' })
    expect(csrfHeaderOf(fetch.mock.calls.at(-1)![1])).toBe('token-2')
  })

  it('reports rejected credentials without treating them as an ended Session', async () => {
    fakeApi({ 'GET /csrf': rotatingCsrf(), 'POST /login': () => problem(401, 'authentication_failed') })
    const sessionEnded = vi.fn()
    client.onSessionEnded(sessionEnded)

    expect(await auth.login('testuser123', 'wrong')).toEqual({ ok: false, reason: 'rejected' })
    expect(sessionEnded).not.toHaveBeenCalled()
  })

  it('recovers from a CSRF token whose Session ended on the server', async () => {
    const fetch = fakeApi({
      'GET /csrf': rotatingCsrf(),
      'POST /login': (init) => (csrfHeaderOf(init) === 'token-2' ? json(200, account) : problem(403, 'csrf_invalid')),
    })

    expect(await auth.login('testuser123', 'Synthetic-Pass-42')).toEqual({ ok: true, account })
    expect(fetch.mock.calls.filter(([url]) => String(url).endsWith('/login'))).toHaveLength(2)
  })

  it.each([
    [problem(429, 'too_many_requests'), 'rate_limited'],
    [problem(500, 'internal_error'), 'error'],
    [problem(400, 'validation'), 'rejected'],
    [problem(403, 'csrf_invalid'), 'error'],
    [problem(403, 'access_denied'), 'error'],
    [problem(401, 'authentication_required'), 'error'],
  ] as const)('maps other failures (%#)', async (response, reason) => {
    fakeApi({ 'GET /csrf': rotatingCsrf(), 'POST /login': () => response })

    expect(await auth.login('testuser123', 'Synthetic-Pass-42')).toEqual({ ok: false, reason })
  })
})

describe('logout', () => {
  it.each([200, 401, 403])('treats %i as logged out and fetches a new CSRF token', async (status) => {
    const fetch = fakeApi({
      'GET /csrf': rotatingCsrf(),
      'POST /logout': () => (status === 200 ? new Response(null, { status }) : problem(status, 'any')),
    })

    expect(await auth.logout()).toBe(true)

    const csrfCalls = fetch.mock.calls.filter(([url]) => String(url).endsWith('/csrf'))
    expect(csrfCalls).toHaveLength(2)
  })

  it('reports a server failure', async () => {
    fakeApi({ 'GET /csrf': rotatingCsrf(), 'POST /logout': () => problem(500, 'internal_error') })

    expect(await auth.logout()).toBe(false)
  })
})

describe('fetchOwnAccount', () => {
  it('returns the Account for a logged-in caller', async () => {
    fakeApi({ 'GET /me': () => json(200, account) })

    expect(await auth.fetchOwnAccount()).toEqual({ kind: 'authenticated', account })
  })

  it('returns anonymous for a Visitor without treating it as an ended Session', async () => {
    fakeApi({ 'GET /me': () => problem(401, 'authentication_required') })
    const sessionEnded = vi.fn()
    client.onSessionEnded(sessionEnded)

    expect(await auth.fetchOwnAccount()).toEqual({ kind: 'anonymous' })
    expect(sessionEnded).not.toHaveBeenCalled()
  })

  it('returns an error for other failures', async () => {
    fakeApi({ 'GET /me': () => problem(500, 'internal_error') })

    expect(await auth.fetchOwnAccount()).toEqual({ kind: 'error' })
  })
})

describe('requestPasswordReset', () => {
  it('posts the email as JSON with the CSRF token', async () => {
    const fetch = fakeApi({
      'GET /csrf': rotatingCsrf(),
      'POST /password-reset/request': () => json(202, { message: 'generic' }),
    })

    const result = await auth.requestPasswordReset('testuser123@test.example.com')

    expect(result).toEqual({ ok: true })
    const [, init] = fetch.mock.calls.find(([url]) => String(url).endsWith('/password-reset/request'))!
    expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json')
    expect(csrfHeaderOf(init)).toBe('token-1')
    expect(JSON.parse(String(init?.body))).toEqual({ email: 'testuser123@test.example.com' })
  })

  it.each([
    [problem(429, 'too_many_requests'), 'rate_limited'],
    [problem(400, 'validation'), 'validation'],
    [problem(500, 'internal_error'), 'error'],
  ] as const)('maps failures (%#)', async (response, reason) => {
    fakeApi({ 'GET /csrf': rotatingCsrf(), 'POST /password-reset/request': () => response })

    expect(await auth.requestPasswordReset('testuser123@test.example.com')).toEqual({ ok: false, reason })
  })
})

describe('confirmPasswordReset', () => {
  it('posts the token and new password as JSON with the CSRF token', async () => {
    const fetch = fakeApi({
      'GET /csrf': rotatingCsrf(),
      'POST /password-reset/confirm': () => new Response(null, { status: 200 }),
    })

    const result = await auth.confirmPasswordReset('synthetic-token', 'Synthetic-Pass-42')

    expect(result).toEqual({ ok: true })
    const [, init] = fetch.mock.calls.find(([url]) => String(url).endsWith('/password-reset/confirm'))!
    expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json')
    expect(csrfHeaderOf(init)).toBe('token-1')
    expect(JSON.parse(String(init?.body))).toEqual({ token: 'synthetic-token', newPassword: 'Synthetic-Pass-42' })
  })

  it('reports every password-policy violation', async () => {
    fakeApi({
      'GET /csrf': rotatingCsrf(),
      'POST /password-reset/confirm': () =>
        json(400, { status: 400, code: 'password_policy', violations: ['min_length'] }, 'application/problem+json'),
    })

    expect(await auth.confirmPasswordReset('synthetic-token', 'weak')).toEqual({
      ok: false,
      reason: 'password_policy',
      violations: ['min_length'],
    })
  })

  it.each([
    [problem(400, 'token_invalid'), 'token_invalid'],
    [problem(400, 'password_history'), 'password_history'],
    [problem(400, 'validation'), 'validation'],
    [problem(429, 'too_many_requests'), 'rate_limited'],
    [problem(500, 'internal_error'), 'error'],
  ] as const)('maps other failures (%#)', async (response, reason) => {
    fakeApi({ 'GET /csrf': rotatingCsrf(), 'POST /password-reset/confirm': () => response })

    expect(await auth.confirmPasswordReset('synthetic-token', 'Synthetic-Pass-42')).toEqual({ ok: false, reason })
  })
})
