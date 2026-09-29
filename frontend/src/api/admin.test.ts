import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fakeApi, json, problem } from '../test/fakeApi'

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
