import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { server } from '@/test/msw/server'
import { problemResponse } from '@/test/msw/problems'
import { apiFetch, apiUrl, clearCsrfToken, CSRF_HEADER, refreshCsrfToken, shouldRetry } from './client'
import { ApiError, UnexpectedResponseError } from './errors'

const CSRF = apiUrl('/api/csrf')
const CHANGE = apiUrl('/api/profile/password')

/** Serves a new token on every bootstrap (`token-1`, `token-2`, ...) and counts the bootstraps. */
function tokenServer() {
  const state = { bootstraps: 0, credentials: [] as RequestCredentials[] }
  server.use(
    http.get(CSRF, ({ request }) => {
      state.bootstraps += 1
      state.credentials.push(request.credentials)
      return HttpResponse.json({ headerName: CSRF_HEADER, token: `token-${state.bootstraps}` })
    }),
  )
  return state
}

/** Records the CSRF header of every request to the change route and answers with `respond`, in turn. */
function changeRoute(...responses: Array<() => Response>) {
  const tokens: Array<string | null> = []
  server.use(
    http.patch(CHANGE, ({ request }) => {
      tokens.push(request.headers.get(CSRF_HEADER))
      const respond = responses[Math.min(tokens.length, responses.length) - 1]
      return respond()
    }),
  )
  return tokens
}

const noContent = () => new HttpResponse(null, { status: 204 })
const change = () => apiFetch('/api/profile/password', { method: 'PATCH', body: '{}' })

beforeEach(() => clearCsrfToken())

describe('CSRF bootstrap', () => {
  it('does not fetch a token for a safe request', async () => {
    const state = tokenServer()
    server.use(http.get(apiUrl('/api/hello'), () => HttpResponse.json('Hello, alice')))

    await apiFetch('/api/hello')

    expect(state.bootstraps).toBe(0)
  })

  it('fetches the token on first need, with the session cookie, and sends it in the header', async () => {
    const state = tokenServer()
    const tokens = changeRoute(noContent)

    await change()

    expect(state.bootstraps).toBe(1)
    expect(state.credentials).toEqual(['include'])
    expect(tokens).toEqual(['token-1'])
  })

  it('reuses the token until it is refreshed', async () => {
    const state = tokenServer()
    const tokens = changeRoute(noContent)

    await change()
    await change()
    await refreshCsrfToken()
    await change()

    expect(state.bootstraps).toBe(2)
    expect(tokens).toEqual(['token-1', 'token-1', 'token-2'])
  })

  it('shares one bootstrap between concurrent unsafe requests', async () => {
    const state = tokenServer()
    changeRoute(noContent)

    await Promise.all([change(), change()])

    expect(state.bootstraps).toBe(1)
  })

  it('fetches again after a failed bootstrap', async () => {
    const state = tokenServer()
    server.use(http.get(CSRF, () => HttpResponse.error(), { once: true }))
    const tokens = changeRoute(noContent)

    await expect(change()).rejects.toThrow(TypeError)
    await change()

    expect(state.bootstraps).toBe(1)
    expect(tokens).toEqual(['token-1'])
  })
})

describe('CSRF_TOKEN_INVALID backstop', () => {
  it('recovers from one refusal with a single silent re-bootstrap and retry', async () => {
    const state = tokenServer()
    const tokens = changeRoute(() => problemResponse('CSRF_TOKEN_INVALID'), noContent)

    await expect(change()).resolves.toBeUndefined()

    expect(state.bootstraps).toBe(2)
    expect(tokens).toEqual(['token-1', 'token-2'])
  })

  it('gives up on a second refusal', async () => {
    const state = tokenServer()
    const tokens = changeRoute(() => problemResponse('CSRF_TOKEN_INVALID'))

    const error: unknown = await change().catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('CSRF_TOKEN_INVALID')
    expect(state.bootstraps).toBe(2)
    expect(tokens).toHaveLength(2)
  })

  it('is not applied to a caller that opts out', async () => {
    const state = tokenServer()
    const tokens = changeRoute(() => problemResponse('CSRF_TOKEN_INVALID'), noContent)

    await expect(
      apiFetch('/api/profile/password', { method: 'PATCH', body: '{}' }, { csrfRetry: false }),
    ).rejects.toMatchObject({ code: 'CSRF_TOKEN_INVALID' })

    expect(state.bootstraps).toBe(1)
    expect(tokens).toHaveLength(1)
  })

  it('does not retry any other refusal', async () => {
    const state = tokenServer()
    const tokens = changeRoute(() => problemResponse('ACCESS_DENIED'))

    await expect(change()).rejects.toMatchObject({ code: 'ACCESS_DENIED' })

    expect(state.bootstraps).toBe(1)
    expect(tokens).toHaveLength(1)
  })
})

describe('shouldRetry (REJ-052)', () => {
  const problem = new ApiError({
    type: 't',
    title: 't',
    status: 503,
    detail: 'd',
    instance: '/api',
    traceId: '0',
    code: 'INTERNAL_ERROR',
  })

  it('retries only a request that got no status', () => {
    expect(shouldRetry(0, new TypeError('Failed to fetch'))).toBe(true)
    expect(shouldRetry(0, problem)).toBe(false)
    expect(shouldRetry(0, new UnexpectedResponseError(502))).toBe(false)
  })

  it('stops after a bounded number of attempts', () => {
    expect(shouldRetry(2, new TypeError('Failed to fetch'))).toBe(false)
  })
})
