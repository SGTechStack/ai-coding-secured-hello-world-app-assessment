import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '@/test/msw/server'
import { problemResponse } from '@/test/msw/problems'
import { apiFetch, apiUrl, REQUEST_HEADERS } from './client'
import { ApiError, isErrorCode, isProblem, UnexpectedResponseError } from './errors'

const HELLO = apiUrl('/api/hello')

describe('apiFetch', () => {
  it('T-AUTH-015: exports exactly the Accept header the server entry point is configured for', () => {
    expect(REQUEST_HEADERS).toEqual({ Accept: 'application/json, application/problem+json' })
  })

  it('T-AUTH-015: sends that Accept header, no X-Requested-With, and the session cookie', async () => {
    let seen: Request | undefined
    server.use(
      http.get(HELLO, ({ request }) => {
        seen = request
        return HttpResponse.json('Hello, alice')
      }),
    )

    await expect(apiFetch<string>('/api/hello', { headers: { Accept: 'text/html' } })).resolves.toBe('Hello, alice')
    expect(seen?.headers.get('Accept')).toBe('application/json, application/problem+json')
    expect(seen?.headers.has('X-Requested-With')).toBe(false)
    expect(seen?.credentials).toBe('include')
  })

  it('resolves undefined for an empty success body', async () => {
    server.use(http.post(apiUrl('/api/logout'), () => new HttpResponse(null, { status: 204 })))

    await expect(apiFetch('/api/logout', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('rejects an envelope with an ApiError carrying its code', async () => {
    server.use(http.get(HELLO, () => problemResponse('AUTHENTICATION_FAILED', '/api/hello')))

    const error: unknown = await apiFetch('/api/hello').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('AUTHENTICATION_FAILED')
    expect((error as ApiError).problem.instance).toBe('/api/hello')
  })

  it('branches on code, not status: two 403 codes stay distinct', async () => {
    server.use(http.get(HELLO, () => problemResponse('CSRF_TOKEN_INVALID')))

    await expect(apiFetch('/api/hello')).rejects.toMatchObject({ code: 'CSRF_TOKEN_INVALID' })
  })

  it.each([
    ['an HTML error page', () => new HttpResponse('<h1>Bad Gateway</h1>', { status: 502 })],
    ['a body with an unknown code', () => HttpResponse.json({ code: 'SOMETHING_ELSE', status: 403 }, { status: 403 })],
  ])('rejects %s with UnexpectedResponseError', async (_name, respond) => {
    server.use(http.get(HELLO, respond))

    const error: unknown = await apiFetch('/api/hello').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(UnexpectedResponseError)
    expect(error).not.toBeInstanceOf(ApiError)
  })
})

describe('error guards', () => {
  it('accepts only closed-enum codes', () => {
    expect(isErrorCode('ACCESS_DENIED')).toBe(true)
    expect(isErrorCode('access_denied')).toBe(false)
    expect(isErrorCode(403)).toBe(false)
    expect(isProblem(null)).toBe(false)
    expect(isProblem('ACCESS_DENIED')).toBe(false)
  })
})
