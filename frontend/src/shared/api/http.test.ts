import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { API_BASE_URL, apiFetch, apiUrl, clearCsrfToken, setUnauthorizedHandler } from './http.ts'

const API = 'http://localhost:8080/api/v1'
const CSRF_URL = `${API}/csrf`
const EXAMPLE_URL = `${API}/example`

describe('apiFetch', () => {
  const fetchMock = vi.fn<typeof fetch>()
  let issuedTokens = 0
  let apiStatuses: number[] = []

  beforeEach(() => {
    issuedTokens = 0
    apiStatuses = []
    fetchMock.mockReset()
    fetchMock.mockImplementation(async (input) => {
      if (input === CSRF_URL) {
        issuedTokens += 1
        return Response.json({ headerName: 'X-CSRF-TOKEN', token: `token-${issuedTokens}` })
      }
      return new Response(null, { status: apiStatuses.shift() ?? 204 })
    })
    vi.stubGlobal('fetch', fetchMock)
    clearCsrfToken()
  })

  afterEach(() => {
    setUnauthorizedHandler(undefined)
    vi.unstubAllGlobals()
  })

  const calls = () => fetchMock.mock.calls.map(([url, init]) => ({ url, init }))
  const apiCalls = () => calls().filter((call) => call.url !== CSRF_URL)
  const csrfCalls = () => calls().filter((call) => call.url === CSRF_URL)
  const sentHeaders = (index: number) => new Headers(apiCalls()[index].init?.headers)

  it('sends safe requests to the configured API origin with credentials included', async () => {
    await apiFetch('/example')

    expect(fetchMock).toHaveBeenCalledWith(
      EXAMPLE_URL,
      expect.objectContaining({ credentials: 'include' }),
    )
  })

  it('sends the CSRF token request and unsafe requests to the API origin with credentials included', async () => {
    await apiFetch('/example', { method: 'PATCH' })

    expect(calls()).toEqual([
      { url: CSRF_URL, init: expect.objectContaining({ credentials: 'include' }) },
      { url: EXAMPLE_URL, init: expect.objectContaining({ credentials: 'include' }) },
    ])
  })

  it('builds absolute URLs from the API origin and base path', () => {
    expect(API_BASE_URL).toBe(API)
    expect(apiUrl('/auth/me')).toBe(`${API}/auth/me`)
  })

  it('sends GET requests without fetching or attaching a CSRF token', async () => {
    await apiFetch('/example')

    expect(csrfCalls()).toHaveLength(0)
    expect(sentHeaders(0).has('X-CSRF-TOKEN')).toBe(false)
  })

  it('fetches the token before the first state-changing request and sends it in the named header', async () => {
    const response = await apiFetch('/example', {
      method: 'post',
      headers: { 'Content-Type': 'application/json' },
    })

    expect(response.status).toBe(204)
    expect(calls().map((call) => call.url)).toEqual([CSRF_URL, EXAMPLE_URL])
    expect(sentHeaders(0).get('X-CSRF-TOKEN')).toBe('token-1')
    expect(sentHeaders(0).get('Content-Type')).toBe('application/json')
  })

  it('reuses the cached token for later state-changing requests', async () => {
    await apiFetch('/example', { method: 'POST' })
    await apiFetch('/example', { method: 'DELETE' })

    expect(csrfCalls()).toHaveLength(1)
    expect(sentHeaders(1).get('X-CSRF-TOKEN')).toBe('token-1')
  })

  it('shares one token fetch between concurrent first requests', async () => {
    await Promise.all([
      apiFetch('/example', { method: 'POST' }),
      apiFetch('/example', { method: 'PUT' }),
    ])

    expect(csrfCalls()).toHaveLength(1)
  })

  it('fetches a fresh token after the cached one is dropped', async () => {
    await apiFetch('/example', { method: 'POST' })
    clearCsrfToken()
    await apiFetch('/example', { method: 'POST' })

    expect(csrfCalls()).toHaveLength(2)
    expect(sentHeaders(1).get('X-CSRF-TOKEN')).toBe('token-2')
  })

  it('retries a rejected state-changing request once with a fresh token', async () => {
    apiStatuses = [403, 204]

    const response = await apiFetch('/example', { method: 'POST', body: '{}' })

    expect(response.status).toBe(204)
    expect(apiCalls()).toHaveLength(2)
    expect(sentHeaders(1).get('X-CSRF-TOKEN')).toBe('token-2')
    expect(apiCalls()[1].init?.body).toBe('{}')
  })

  it('returns the second 403 instead of retrying again', async () => {
    apiStatuses = [403, 403]

    const response = await apiFetch('/example', { method: 'POST' })

    expect(response.status).toBe(403)
    expect(apiCalls()).toHaveLength(2)
  })

  it('does not retry a GET that gets a 403', async () => {
    apiStatuses = [403]

    const response = await apiFetch('/example')

    expect(response.status).toBe(403)
    expect(apiCalls()).toHaveLength(1)
  })

  it('rejects without sending the request when the token cannot be fetched', async () => {
    fetchMock.mockImplementation(async () => new Response(null, { status: 503 }))

    await expect(apiFetch('/example', { method: 'POST' })).rejects.toThrow()
    expect(apiCalls()).toHaveLength(0)
  })

  it('keeps a newer token when an older, dropped token fetch fails', async () => {
    let failFirstFetch: (reason: Error) => void = () => {}
    fetchMock.mockImplementationOnce(
      () => new Promise<Response>((_resolve, reject) => (failFirstFetch = reject)),
    )
    const first = apiFetch('/example', { method: 'POST' })
    clearCsrfToken()
    await apiFetch('/example', { method: 'POST' })

    failFirstFetch(new Error('network down'))
    await expect(first).rejects.toThrow()
    await apiFetch('/example', { method: 'POST' })

    expect(csrfCalls()).toHaveLength(2)
  })

  it('fetches the token again after a failed token fetch', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 503 }))
    await expect(apiFetch('/example', { method: 'POST' })).rejects.toThrow()

    const response = await apiFetch('/example', { method: 'POST' })

    expect(response.status).toBe(204)
    expect(sentHeaders(0).get('X-CSRF-TOKEN')).toBe('token-1')
  })

  it('handles an unexpected 401 and drops the cached token', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    apiStatuses = [401, 204]

    await apiFetch('/example', { method: 'POST' })
    await apiFetch('/example', { method: 'POST' })

    expect(handler).toHaveBeenCalledOnce()
    expect(csrfCalls()).toHaveLength(2)
    expect(sentHeaders(1).get('X-CSRF-TOKEN')).toBe('token-2')
  })

  it('handles only the final 401 after a 403 retry', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    apiStatuses = [403, 401]

    const response = await apiFetch('/example', { method: 'POST' })

    expect(response.status).toBe(401)
    expect(handler).toHaveBeenCalledOnce()
    expect(apiCalls()).toHaveLength(2)
  })

  it('allows login and guard calls to treat 401 as an expected result', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    apiStatuses = [401]

    const response = await apiFetch('/auth/me', {}, { expectedUnauthorized: true })

    expect(response.status).toBe(401)
    expect(handler).not.toHaveBeenCalled()
  })
})
