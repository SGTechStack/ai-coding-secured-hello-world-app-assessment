import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { apiUrl, clearCsrfToken, CSRF_HEADER } from '@/lib/api/client'
import { type AdminUser, setAdminUserEnabled } from '@/lib/admin/users'
import type { Profile } from '@/lib/auth/session'
import { problemResponse } from '@/test/msw/problems'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/renderApp'

const VERIFICATION = apiUrl('/api/mfa/totp/verification')

const verifiedAdmin: Profile = {
  id: '00000000-0000-4000-8000-000000000001',
  username: 'alice-admin',
  role: 'ADMIN',
  passwordChangeRequired: false,
  factors: { held: true, required: true, enrolled: true, rebindRequired: false },
}

const account = (n: number, username: string): AdminUser => ({
  id: `00000000-0000-4000-8000-00000000000${n}`,
  username,
  email: `${username}@example.test`,
  role: 'USER',
  enabled: true,
  createdAt: '2026-09-02T09:30:00Z',
})
const bob = account(2, 'bob')
const carol = account(3, 'carol')

let stored: Record<string, AdminUser>
/** Every change request that reached the server: whose, with which CSRF token. */
let changes: { id: string; token: string | null }[]
let verifications: unknown[]
/** Whether the session's factor is fresh enough for a change; a verified code makes it so. */
let fresh: boolean

beforeEach(() => {
  clearCsrfToken()
  stored = { [bob.id]: { ...bob }, [carol.id]: { ...carol } }
  changes = []
  verifications = []
  fresh = false
  let bootstraps = 0
  server.use(
    // The token rotates with the session id, as a factor grant rotates it (ADR-040).
    http.get(apiUrl('/api/csrf'), () => {
      bootstraps += 1
      return HttpResponse.json({ headerName: CSRF_HEADER, token: `token-${bootstraps}` })
    }),
    http.get(apiUrl('/api/profile'), () => HttpResponse.json(verifiedAdmin)),
    http.get(apiUrl('/api/admin/users'), () => HttpResponse.json(Object.values(stored))),
    http.get(apiUrl('/api/admin/users/:id'), ({ params }) => HttpResponse.json(stored[String(params.id)])),
    http.put(apiUrl('/api/admin/users/:id/enabled'), async ({ params, request }) => {
      const id = String(params.id)
      changes.push({ id, token: request.headers.get(CSRF_HEADER) })
      if (!fresh) {
        return problemResponse('MISSING_FACTOR', `/api/admin/users/${id}/enabled`, {
          factor: 'TOTP',
          reason: 'EXPIRED',
        })
      }
      const { enabled } = (await request.json()) as { enabled: boolean }
      stored[id] = { ...stored[id], enabled }
      return HttpResponse.json(stored[id])
    }),
    http.post(VERIFICATION, async ({ request }) => {
      verifications.push(await request.json())
      fresh = true
      return new HttpResponse(null, { status: 204 })
    }),
  )
})

/** Opens bob's page and presses Disable, whose request is refused for a stale factor; returns the open dialog. */
async function disableBob() {
  const rendered = renderApp(`/admin/users/${bob.id}`)
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Disable account' }))
  const dialog = await screen.findByRole('dialog', { name: 'TOTP Verification' })
  return { ...rendered, user, dialog }
}

describe('the step-up challenge', () => {
  it('opens one challenge for two concurrent refused changes and replays each once with a fresh token', async () => {
    const { user, router, dialog } = await disableBob()
    // Settled into a value at once, so a rejection (the dialog unmounting on a timeout) is never left unhandled.
    const carolChange = setAdminUserEnabled(carol.id, false).then(
      (value) => ({ value }),
      (error: unknown) => ({ error }),
    )
    await waitFor(() => expect(changes).toHaveLength(2))

    expect(screen.getAllByRole('dialog')).toHaveLength(1)
    expect(router.state.location.pathname).toBe(`/admin/users/${bob.id}`)

    await user.type(within(dialog).getByLabelText('Code from the app'), '123456')
    await user.click(within(dialog).getByRole('button', { name: 'Verify' }))

    expect(await screen.findByText('Account disabled. The user has been signed out.')).toHaveAttribute('role', 'status')
    expect(await carolChange).toEqual({ value: expect.objectContaining({ id: carol.id, enabled: false }) })
    expect(verifications).toEqual([{ code: '123456' }])
    expect(changes.slice(0, 2).map(({ token }) => token)).toEqual(['token-1', 'token-1'])
    expect(changes.slice(2)).toEqual(
      expect.arrayContaining([
        { id: bob.id, token: 'token-2' },
        { id: carol.id, token: 'token-2' },
      ]),
    )
    expect(changes).toHaveLength(4)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('T-FE-010: the code field has focus when the prompt opens', async () => {
    const { dialog } = await disableBob()

    await waitFor(() => expect(within(dialog).getByLabelText('Code from the app')).toHaveFocus())
  })

  it('T-FE-004: a refused code is announced as an alert, and the challenge stays open', async () => {
    server.use(http.post(VERIFICATION, () => problemResponse('INVALID_FACTOR', '/api/mfa/totp/verification')))
    const { user, dialog } = await disableBob()

    await user.type(within(dialog).getByLabelText('Code from the app'), '654321')
    await user.click(within(dialog).getByRole('button', { name: 'Verify' }))

    expect(await within(dialog).findByText(/That code was not accepted/)).toHaveAttribute('role', 'alert')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(changes).toHaveLength(1)
  })

  it('T-FE-014: Esc closes the challenge and drops the queued change, which is not replayed', async () => {
    const { user } = await disableBob()

    await user.keyboard('{Escape}')

    expect(await screen.findByText('Verification was cancelled, so nothing was changed.')).toHaveAttribute(
      'role',
      'alert',
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(changes).toHaveLength(1)
    expect(verifications).toEqual([])
    expect(screen.getByText('Enabled')).toBeInTheDocument()
  })

  it('T-FE-014: a press outside the challenge closes it and drops the queued change', async () => {
    const { user } = await disableBob()

    await user.click(document.body)

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByText('Verification was cancelled, so nothing was changed.')).toBeInTheDocument()
    expect(changes).toHaveLength(1)
  })

  it('T-FE-007: Cancel closes the challenge and returns focus to the control that opened it', async () => {
    const { user, dialog } = await disableBob()

    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))

    await waitFor(() => expect(screen.getByRole('button', { name: 'Disable account' })).toHaveFocus())
    expect(changes).toHaveLength(1)
  })

  it('a code refused because the factor is disabled drops the queue and follows the code', async () => {
    server.use(http.post(VERIFICATION, () => problemResponse('FACTOR_DISABLED', '/api/mfa/totp/verification')))
    const { user, router, dialog } = await disableBob()

    await user.type(within(dialog).getByLabelText('Code from the app'), '123456')
    await user.click(within(dialog).getByRole('button', { name: 'Verify' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/factor-disabled'))
    expect(changes).toHaveLength(1)
  })

  it('a locked factor says how long and holds Verify, keeping the change queued', async () => {
    server.use(
      http.post(VERIFICATION, () => {
        const locked = problemResponse('TOO_MANY_REQUESTS', '/api/mfa/totp/verification', {
          factor: 'TOTP',
          reason: 'LOCKED',
        })
        locked.headers.set('Retry-After', '120')
        return locked
      }),
    )
    const { user, dialog } = await disableBob()

    await user.type(within(dialog).getByLabelText('Code from the app'), '123456')
    await user.click(within(dialog).getByRole('button', { name: 'Verify' }))

    expect(await within(dialog).findByText('Too many attempts. Try again in 2 minutes.')).toHaveAttribute(
      'role',
      'alert',
    )
    expect(within(dialog).getByRole('button', { name: 'Verify' })).toBeDisabled()
    expect(changes).toHaveLength(1)
  })

  it('a failure with no route of its own keeps the challenge open to try again', async () => {
    let failing = true
    server.use(
      http.post(VERIFICATION, () => {
        if (failing) {
          return problemResponse('INTERNAL_ERROR', '/api/mfa/totp/verification')
        }
        fresh = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { user, dialog } = await disableBob()

    await user.type(within(dialog).getByLabelText('Code from the app'), '123456')
    await user.click(within(dialog).getByRole('button', { name: 'Verify' }))
    expect(await within(dialog).findByText('The code could not be checked. Try again.')).toHaveAttribute(
      'role',
      'alert',
    )

    failing = false
    await user.type(within(dialog).getByLabelText('Code from the app'), '234567')
    await user.click(within(dialog).getByRole('button', { name: 'Verify' }))
    expect(await screen.findByText('Account disabled. The user has been signed out.')).toBeInTheDocument()
  })

  it('a throttle without Retry-After keeps the challenge open with the throttle copy', async () => {
    server.use(http.post(VERIFICATION, () => problemResponse('TOO_MANY_REQUESTS', '/api/mfa/totp/verification')))
    const { user, dialog } = await disableBob()

    await user.type(within(dialog).getByLabelText('Code from the app'), '123456')
    await user.click(within(dialog).getByRole('button', { name: 'Verify' }))

    expect(await within(dialog).findByText('Too many attempts. Wait a moment, then try again.')).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })
})
