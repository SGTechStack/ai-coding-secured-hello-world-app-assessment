import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fakeApi, json, problem, type Route } from '../test/fakeApi'

let admin: typeof import('./admin')
let client: typeof import('./client')
beforeEach(async () => {
  vi.resetModules()
  client = await import('./client')
  admin = await import('./admin')
})

const accounts = [
  {
    id: '00000000-0000-0000-0000-00000000000a',
    username: 'testadmin123',
    email: 'testadmin123@test.example.com',
    role: 'ADMIN',
    enabled: true,
    locked: false,
    createdAt: '2026-01-05T01:00:00Z',
  },
  {
    id: '00000000-0000-0000-0000-000000000123',
    username: 'testuser123',
    email: 'testuser123@test.example.com',
    role: 'USER',
    enabled: false,
    locked: true,
    createdAt: '2026-01-06T02:30:00Z',
  },
]

describe('listAccounts', () => {
  it('returns every Account from GET /admin/users', async () => {
    const fetch = fakeApi({ 'GET /admin/users': () => json(200, accounts) })

    expect(await admin.listAccounts()).toEqual({ ok: true, accounts })
    const [url, init] = fetch.mock.calls[0]
    expect(new URL(String(url)).pathname).toBe('/api/admin/users')
    expect(init?.method ?? 'GET').toBe('GET')
  })

  it('reports a 403 as forbidden', async () => {
    fakeApi({ 'GET /admin/users': () => problem(403, 'access_denied') })

    expect(await admin.listAccounts()).toEqual({ ok: false, reason: 'forbidden' })
  })

  it('reports any other failure as a generic error', async () => {
    fakeApi({ 'GET /admin/users': () => problem(500, 'internal_error') })

    expect(await admin.listAccounts()).toEqual({ ok: false, reason: 'error' })
  })

  it('treats a 401 as an ended Session', async () => {
    fakeApi({ 'GET /admin/users': () => problem(401, 'authentication_required') })
    const ended = vi.fn()
    client.onSessionEnded(ended)

    expect(await admin.listAccounts()).toEqual({ ok: false, reason: 'error' })
    expect(ended).toHaveBeenCalledOnce()
  })
})

const id = accounts[1].id
const csrfRoute: Route = () => json(200, { headerName: 'X-CSRF-TOKEN', token: 'token-1' })

describe('setEnabled', () => {
  it('sends the target and the new enabled state as JSON with the CSRF token', async () => {
    const fetch = fakeApi({
      'GET /csrf': csrfRoute,
      [`PATCH /admin/users/${id}/enabled`]: () => new Response(null, { status: 200 }),
    })

    expect(await admin.setEnabled(id, false)).toEqual({ ok: true })
    const [url, init] = fetch.mock.calls.find(([callUrl]) => String(callUrl).includes('/enabled'))!
    expect(new URL(String(url)).pathname).toBe(`/api/admin/users/${id}/enabled`)
    expect(init?.method).toBe('PATCH')
    expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('token-1')
    expect(JSON.parse(String(init?.body))).toEqual({ enabled: false })
  })

  it.each([
    [problem(403, 'self_action_forbidden'), 'self_action_forbidden'],
    [problem(409, 'last_admin'), 'last_admin'],
    [problem(404, 'not_found'), 'not_found'],
    [problem(403, 'access_denied'), 'forbidden'],
    [problem(500, 'internal_error'), 'error'],
  ] as const)('maps a refusal (%#)', async (response, reason) => {
    fakeApi({ 'GET /csrf': csrfRoute, [`PATCH /admin/users/${id}/enabled`]: () => response })

    expect(await admin.setEnabled(id, false)).toEqual({ ok: false, reason })
  })
})

describe('unlock', () => {
  it('sends the target with the CSRF token and no body', async () => {
    const fetch = fakeApi({
      'GET /csrf': csrfRoute,
      [`POST /admin/users/${id}/unlock`]: () => new Response(null, { status: 200 }),
    })

    expect(await admin.unlock(id)).toEqual({ ok: true })
    const [url, init] = fetch.mock.calls.find(([callUrl]) => String(callUrl).includes('/unlock'))!
    expect(new URL(String(url)).pathname).toBe(`/api/admin/users/${id}/unlock`)
    expect(init?.method).toBe('POST')
    expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('token-1')
    expect(init?.body).toBeUndefined()
  })

  it.each([
    [problem(403, 'self_action_forbidden'), 'self_action_forbidden'],
    [problem(404, 'not_found'), 'not_found'],
    [problem(403, 'access_denied'), 'forbidden'],
    [problem(500, 'internal_error'), 'error'],
  ] as const)('maps a refusal (%#)', async (response, reason) => {
    fakeApi({ 'GET /csrf': csrfRoute, [`POST /admin/users/${id}/unlock`]: () => response })

    expect(await admin.unlock(id)).toEqual({ ok: false, reason })
  })
})

describe('requirePasswordChange', () => {
  it('sends the target with the CSRF token and no body', async () => {
    const fetch = fakeApi({
      'GET /csrf': csrfRoute,
      [`POST /admin/users/${id}/require-password-change`]: () => new Response(null, { status: 200 }),
    })

    expect(await admin.requirePasswordChange(id)).toEqual({ ok: true })
    const [url, init] = fetch.mock.calls.find(([callUrl]) => String(callUrl).includes('/require-password-change'))!
    expect(new URL(String(url)).pathname).toBe(`/api/admin/users/${id}/require-password-change`)
    expect(init?.method).toBe('POST')
    expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('token-1')
    expect(init?.body).toBeUndefined()
  })

  it.each([
    [problem(404, 'not_found'), 'not_found'],
    [problem(403, 'access_denied'), 'forbidden'],
    [problem(500, 'internal_error'), 'error'],
  ] as const)('maps a refusal (%#)', async (response, reason) => {
    fakeApi({ 'GET /csrf': csrfRoute, [`POST /admin/users/${id}/require-password-change`]: () => response })

    expect(await admin.requirePasswordChange(id)).toEqual({ ok: false, reason })
  })
})

describe('deleteAccount', () => {
  it('sends a DELETE for the target with the CSRF token and no body', async () => {
    const fetch = fakeApi({
      'GET /csrf': csrfRoute,
      [`DELETE /admin/users/${id}`]: () => new Response(null, { status: 204 }),
    })

    expect(await admin.deleteAccount(id)).toEqual({ ok: true })
    const [url, init] = fetch.mock.calls.find(([callUrl]) => String(callUrl).endsWith(`/admin/users/${id}`))!
    expect(new URL(String(url)).pathname).toBe(`/api/admin/users/${id}`)
    expect(init?.method).toBe('DELETE')
    expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('token-1')
    expect(init?.body).toBeUndefined()
  })

  it.each([
    [problem(403, 'self_action_forbidden'), 'self_action_forbidden'],
    [problem(409, 'last_admin'), 'last_admin'],
    [problem(404, 'not_found'), 'not_found'],
    [problem(403, 'access_denied'), 'forbidden'],
    [problem(500, 'internal_error'), 'error'],
  ] as const)('maps a refusal (%#)', async (response, reason) => {
    fakeApi({ 'GET /csrf': csrfRoute, [`DELETE /admin/users/${id}`]: () => response })

    expect(await admin.deleteAccount(id)).toEqual({ ok: false, reason })
  })
})

describe('changeRole', () => {
  it('sends the target and the new role as JSON with the CSRF token', async () => {
    const fetch = fakeApi({
      'GET /csrf': csrfRoute,
      [`PATCH /admin/users/${id}/role`]: () => new Response(null, { status: 200 }),
    })

    expect(await admin.changeRole(id, 'ADMIN')).toEqual({ ok: true })
    const [url, init] = fetch.mock.calls.find(([callUrl]) => String(callUrl).includes('/role'))!
    expect(new URL(String(url)).pathname).toBe(`/api/admin/users/${id}/role`)
    expect(init?.method).toBe('PATCH')
    expect(JSON.parse(String(init?.body))).toEqual({ role: 'ADMIN' })
  })

  it('reports the last-Admin rule as a 409 refusal', async () => {
    fakeApi({ 'GET /csrf': csrfRoute, [`PATCH /admin/users/${id}/role`]: () => problem(409, 'last_admin') })

    expect(await admin.changeRole(id, 'USER')).toEqual({ ok: false, reason: 'last_admin' })
  })
})
