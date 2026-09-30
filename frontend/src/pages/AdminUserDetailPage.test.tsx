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

const alice: AdminUser = {
  id: verifiedAdmin.id,
  username: 'alice-admin',
  email: 'alice@example.test',
  role: 'ADMIN',
  enabled: true,
  createdAt: '2026-09-01T08:00:00Z',
}

const bob: AdminUser = {
  id: '00000000-0000-4000-8000-000000000002',
  username: 'bob-admin',
  email: 'bob@example.test',
  role: 'ADMIN',
  enabled: true,
  createdAt: '2026-09-02T09:30:00Z',
}

let stored: Record<string, AdminUser>
let requests: { id: string; body: unknown }[]

beforeEach(() => {
  clearCsrfToken()
  stored = { [alice.id]: { ...alice }, [bob.id]: { ...bob } }
  requests = []
  server.use(
    http.get(apiUrl('/api/profile'), () => HttpResponse.json(verifiedAdmin)),
    http.get(apiUrl('/api/admin/users'), () => HttpResponse.json(Object.values(stored))),
    http.get(apiUrl('/api/admin/users/:id'), ({ params }) => HttpResponse.json(stored[String(params.id)])),
    http.put(apiUrl('/api/admin/users/:id/enabled'), async ({ params, request }) => {
      const body = (await request.json()) as { enabled: boolean }
      requests.push({ id: String(params.id), body })
      stored[String(params.id)] = { ...stored[String(params.id)], enabled: body.enabled }
      return HttpResponse.json(stored[String(params.id)])
    }),
  )
})

describe('/admin/users/:id enable and disable', () => {
  it('disables another account and shows the new status', async () => {
    renderApp(`/admin/users/${bob.id}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Disable account' }))

    expect(await screen.findByText('Account disabled. The user has been signed out.')).toHaveAttribute('role', 'status')
    expect(screen.getByText('Disabled')).toBeInTheDocument()
    expect(requests).toEqual([{ id: bob.id, body: { enabled: false } }])
    expect(screen.getByRole('button', { name: 'Enable account' })).toBeEnabled()
  })

  it('re-enables an account and says the user must change their password', async () => {
    stored[bob.id].enabled = false
    renderApp(`/admin/users/${bob.id}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Enable account' }))

    expect(
      await screen.findByText('Account enabled. The user must change their password when they next sign in.'),
    ).toHaveAttribute('role', 'status')
    expect(requests).toEqual([{ id: bob.id, body: { enabled: true } }])
  })

  it('a two-admin refusal names the next steps and leaves the account as it was', async () => {
    server.use(
      http.put(apiUrl('/api/admin/users/:id/enabled'), () =>
        problemResponse('TWO_ADMIN_INVARIANT', `/api/admin/users/${bob.id}/enabled`),
      ),
    )
    renderApp(`/admin/users/${bob.id}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Disable account' }))

    const alert = await screen.findByText(/invite a new user/)
    expect(alert).toHaveAttribute('role', 'alert')
    expect(alert).toHaveTextContent(/invite a new user/)
    expect(alert).toHaveTextContent(/redeem the invitation/)
    expect(alert).toHaveTextContent(/promote them to administrator/)
    expect(alert).toHaveTextContent(/set up their authenticator app/)
    expect(screen.getByText('Enabled')).toBeInTheDocument()
  })

  it('offers no control on the administrator’s own account', async () => {
    renderApp(`/admin/users/${alice.id}`)

    expect(await screen.findByText('You cannot change your own account.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /account/ })).not.toBeInTheDocument()
  })

  it('a refusal the server still makes on its own is shown, not followed', async () => {
    server.use(
      http.put(apiUrl('/api/admin/users/:id/enabled'), () =>
        problemResponse('ACCESS_DENIED', `/api/admin/users/${bob.id}/enabled`),
      ),
    )
    const { router } = renderApp(`/admin/users/${bob.id}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Disable account' }))

    expect(await screen.findByText('This account cannot be changed from here.')).toHaveAttribute('role', 'alert')
    expect(router.state.location.pathname).toBe(`/admin/users/${bob.id}`)
  })
})
