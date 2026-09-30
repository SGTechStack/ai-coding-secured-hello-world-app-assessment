import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { apiUrl, clearCsrfToken } from '@/lib/api/client'
import type { AdminUser } from '@/lib/admin/users'
import type { Profile } from '@/lib/auth/session'
import { problemResponse } from '@/test/msw/problems'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/renderApp'

const verifiedAdmin: Profile = {
  id: '00000000-0000-4000-8000-000000000001',
  username: 'alice-admin',
  role: 'ADMIN',
  passwordChangeRequired: false,
  factors: { held: true, required: true, enrolled: true, rebindRequired: false },
}

const account = (id: string, username: string, role: AdminUser['role']): AdminUser => ({
  id,
  username,
  email: `${username}@example.test`,
  role,
  enabled: true,
  createdAt: '2026-09-02T09:30:00Z',
})

const alice = account(verifiedAdmin.id, 'alice-admin', 'ADMIN')
const bob = account('00000000-0000-4000-8000-000000000002', 'bob-admin', 'ADMIN')
const carol = account('00000000-0000-4000-8000-000000000003', 'carol', 'USER')

let resets: string[]

beforeEach(() => {
  clearCsrfToken()
  resets = []
  const stored: Record<string, AdminUser> = { [alice.id]: alice, [bob.id]: bob, [carol.id]: carol }
  server.use(
    http.get(apiUrl('/api/profile'), () => HttpResponse.json(verifiedAdmin)),
    http.get(apiUrl('/api/admin/users/:id'), ({ params }) => HttpResponse.json(stored[String(params.id)])),
    http.delete(apiUrl('/api/admin/users/:id/totp'), ({ params }) => {
      resets.push(String(params.id))
      return new HttpResponse(null, { status: 204 })
    }),
  )
})

describe('/admin/users/:id authenticator reset', () => {
  it("resets another administrator's authenticator app after a confirmation", async () => {
    renderApp(`/admin/users/${bob.id}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Reset authenticator app' }))
    expect(resets).toEqual([])
    expect(screen.getByText(/They will be signed out and must set it up again/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Confirm reset' }))

    expect(
      await screen.findByText(
        'Authenticator app reset. The user has been signed out and must set it up again when they next sign in.',
      ),
    ).toHaveAttribute('role', 'status')
    expect(resets).toEqual([bob.id])
    expect(screen.getByRole('button', { name: 'Reset authenticator app' })).toBeEnabled()
  })

  it('sends nothing when the confirmation is cancelled', async () => {
    renderApp(`/admin/users/${bob.id}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Reset authenticator app' }))
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(screen.getByRole('button', { name: 'Reset authenticator app' })).toBeInTheDocument()
    expect(resets).toEqual([])
  })

  it("is not offered on the admin's own account", async () => {
    renderApp(`/admin/users/${alice.id}`)
    expect(await screen.findByText('You cannot change your own account.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reset authenticator app' })).not.toBeInTheDocument()
  })

  it('is not offered on a user account, which has no authenticator app', async () => {
    renderApp(`/admin/users/${carol.id}`)
    expect(await screen.findByRole('button', { name: 'Disable account' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reset authenticator app' })).not.toBeInTheDocument()
  })

  it('shows a refusal instead of following it', async () => {
    server.use(http.delete(apiUrl('/api/admin/users/:id/totp'), () => problemResponse('ACCESS_DENIED')))
    renderApp(`/admin/users/${bob.id}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Reset authenticator app' }))
    await user.click(screen.getByRole('button', { name: 'Confirm reset' }))

    expect(await screen.findByText("This account's authenticator app cannot be reset from here.")).toHaveAttribute(
      'role',
      'alert',
    )
    expect(screen.getByRole('heading', { name: 'bob-admin' })).toBeInTheDocument()
  })

  it('a factor too old for a change sends the session to the challenge', async () => {
    server.use(
      http.delete(apiUrl('/api/admin/users/:id/totp'), () =>
        problemResponse('MISSING_FACTOR', `/api/admin/users/${bob.id}/totp`, { factor: 'TOTP', reason: 'EXPIRED' }),
      ),
    )
    const { router } = renderApp(`/admin/users/${bob.id}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Reset authenticator app' }))
    await user.click(screen.getByRole('button', { name: 'Confirm reset' }))

    expect(await screen.findByRole('heading', { name: 'TOTP Verification' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/verify')
  })
})
