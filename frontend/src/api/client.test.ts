import { describe, expect, it } from 'vitest'
import { csrfResponse, json, mockApi, problem } from '../test/mockApi'
import { ApiError, api } from './client'

describe('api client', () => {
  it('sends the session cookie and a CSRF header on state-changing requests', async () => {
    const requests = mockApi({
      'GET /api/auth/csrf': csrfResponse,
      'POST /api/auth/register': () => json(201, { id: '1', username: 'alice', role: 'USER' }),
    })

    await api.register('alice', 'alice@example.com', 'long-enough-password')

    expect(requests.map((r) => `${r.method} ${r.path}`)).toEqual(['GET /api/auth/csrf', 'POST /api/auth/register'])
    expect(requests.every((r) => r.credentials === 'include')).toBe(true)
    expect(requests[1].headers['X-CSRF-TOKEN']).toBe('csrf-1')
  })

  it('reuses the CSRF token until login rotates it', async () => {
    const requests = mockApi({
      'GET /api/auth/csrf': csrfResponse,
      'POST /api/auth/password-reset/request': () => json(202, { message: 'ok' }),
      'POST /api/auth/login': () => json(200, { id: '1', username: 'alice', role: 'USER' }),
    })

    await api.requestPasswordReset('a@example.com')
    await api.requestPasswordReset('a@example.com')
    await api.login('alice', 'long-enough-password')
    await api.requestPasswordReset('a@example.com')

    expect(requests.filter((r) => r.path === '/api/auth/csrf')).toHaveLength(2)
  })

  it('fetches a fresh CSRF token and retries once when the old one is rejected', async () => {
    let tokenNumber = 0
    let attempts = 0
    const requests = mockApi({
      'GET /api/auth/csrf': () => json(200, { headerName: 'X-CSRF-TOKEN', token: `csrf-${++tokenNumber}` }),
      'POST /api/auth/password-reset/request': () =>
        ++attempts === 1
          ? problem(403, 'CSRF_INVALID', 'Missing or invalid CSRF token.')
          : json(202, { message: 'ok' }),
    })

    await expect(api.requestPasswordReset('a@example.com')).resolves.toEqual({ message: 'ok' })

    const posts = requests.filter((r) => r.method === 'POST')
    expect(posts.map((r) => r.headers['X-CSRF-TOKEN'])).toEqual(['csrf-1', 'csrf-2'])
  })

  it('does not retry an authorization failure', async () => {
    const requests = mockApi({
      'GET /api/auth/csrf': csrfResponse,
      'DELETE /api/admin/users/u1': () => problem(403, 'FORBIDDEN', 'You do not have permission to perform this action.'),
    })

    await expect(api.deleteUser('u1')).rejects.toMatchObject({ status: 403, code: 'FORBIDDEN' })
    expect(requests.filter((r) => r.method === 'DELETE')).toHaveLength(1)
  })

  it('turns problem details into an ApiError with field errors and Retry-After', async () => {
    mockApi({
      'GET /api/auth/csrf': csrfResponse,
      'POST /api/auth/register': () =>
        problem(409, 'REGISTRATION_CONFLICT', 'Username or email is already registered.', {
          errors: { username: 'is already taken' },
        }),
      'POST /api/auth/login': () =>
        json(429, { status: 429, code: 'TOO_MANY_REQUESTS', detail: 'Too many attempts.' }, { 'Retry-After': '120' }),
    })

    const conflict = await api.register('alice', 'a@example.com', 'long-enough-password').catch((e: unknown) => e)
    const throttled = await api.login('alice', 'x').catch((e: unknown) => e)

    expect(conflict).toBeInstanceOf(ApiError)
    expect((conflict as ApiError).fieldErrors).toEqual({ username: 'is already taken' })
    expect((throttled as ApiError).retryAfterSeconds).toBe(120)
  })

  it('returns text responses as strings', async () => {
    mockApi({
      'GET /api/hello': () => new Response('Hello, alice', { headers: { 'Content-Type': 'text/plain' } }),
    })

    await expect(api.hello()).resolves.toBe('Hello, alice')
  })
})
