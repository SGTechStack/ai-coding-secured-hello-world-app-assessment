import { screen, waitFor } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
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

const carol: AdminUser = {
  id: '00000000-0000-4000-8000-000000000003',
  username: 'carol-user',
  email: 'carol@example.test',
  role: 'USER',
  enabled: true,
  createdAt: '2026-09-03T10:00:00Z',
}

let stored: Record<string, AdminUser>
let requests: { id: string; body: unknown }[]
let changes: { method: string; path: string; body?: unknown }[]

beforeEach(() => {
  clearCsrfToken()
  stored = { [alice.id]: { ...alice }, [bob.id]: { ...bob }, [carol.id]: { ...carol } }
  requests = []
  changes = []
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
    http.put(apiUrl('/api/admin/users/:id/role'), async ({ params, request }) => {
      const body = (await request.json()) as { role: AdminUser['role'] }
      changes.push({ method: 'PUT', path: `${String(params.id)}/role`, body })
      stored[String(params.id)] = { ...stored[String(params.id)], role: body.role }
      return HttpResponse.json(stored[String(params.id)])
    }),
    http.delete(apiUrl('/api/admin/users/:id'), ({ params }) => {
      changes.push({ method: 'DELETE', path: String(params.id) })
      delete stored[String(params.id)]
      return new HttpResponse(null, { status: 204 })
    }),
  )
})

/** Presses Tab until `element` has the focus, as a keyboard user would; fails if it is never reached. */
async function tabTo(user: UserEvent, element: HTMLElement) {
  for (let presses = 0; presses < 30 && document.activeElement !== element; presses++) {
    await user.tab()
  }
  expect(element).toHaveFocus()
}

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

describe('/admin/users/:id role change and delete', () => {
  it('T-FE-009: the user table, the enable control and the role and delete dialogs are operable by keyboard', async () => {
    renderApp('/admin/users')
    const user = userEvent.setup()

    await tabTo(user, await screen.findByRole('link', { name: bob.username }))
    await user.keyboard('{Enter}')
    await tabTo(user, await screen.findByRole('button', { name: 'Disable account' }))
    await user.keyboard('{Enter}')
    expect(await screen.findByText('Account disabled. The user has been signed out.')).toBeInTheDocument()
    expect(requests).toEqual([{ id: bob.id, body: { enabled: false } }])

    // The role dialog opens on Cancel; Escape closes it, changes nothing and returns the focus to its trigger.
    const roleTrigger = screen.getByRole('button', { name: 'Make user' })
    await tabTo(user, roleTrigger)
    await user.keyboard('{Enter}')
    const roleDialog = await screen.findByRole('alertdialog', { name: `Make ${bob.username} a user?` })
    await waitFor(() => expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus())
    await user.keyboard('{Escape}')
    await waitFor(() => expect(roleDialog).not.toBeInTheDocument())
    await waitFor(() => expect(roleTrigger).toHaveFocus())
    expect(changes).toEqual([])

    await user.keyboard('{Enter}')
    await screen.findByRole('alertdialog', { name: `Make ${bob.username} a user?` })
    await tabTo(user, screen.getByRole('button', { name: 'Change role' }))
    await user.keyboard('{Enter}')
    expect(await screen.findByText('Role changed to user. The user has been signed out.')).toHaveAttribute(
      'role',
      'status',
    )
    expect(screen.getByText('USER')).toBeInTheDocument()

    await tabTo(user, screen.getByRole('button', { name: 'Delete account' }))
    await user.keyboard('{Enter}')
    await screen.findByRole('alertdialog', { name: `Delete ${bob.username}?` })
    await tabTo(user, screen.getByRole('button', { name: 'Delete' }))
    await user.keyboard('{Enter}')

    expect(await screen.findByText(/^Account deleted\./)).toHaveAttribute('role', 'status')
    expect(screen.getByRole('heading', { name: bob.username })).toHaveFocus()
    expect(screen.queryByRole('button', { name: /account/ })).not.toBeInTheDocument()
    expect(changes).toEqual([
      { method: 'PUT', path: `${bob.id}/role`, body: { role: 'USER' } },
      { method: 'DELETE', path: bob.id },
    ])
  })

  it('promotes a user after confirmation', async () => {
    renderApp(`/admin/users/${carol.id}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Make administrator' }))
    await user.click(await screen.findByRole('button', { name: 'Change role' }))

    expect(await screen.findByText('Role changed to administrator. The user has been signed out.')).toBeInTheDocument()
    expect(changes).toEqual([{ method: 'PUT', path: `${carol.id}/role`, body: { role: 'ADMIN' } }])
    expect(screen.getByRole('button', { name: 'Make user' })).toBeEnabled()
  })

  it('cancelling the delete dialog deletes nothing', async () => {
    renderApp(`/admin/users/${carol.id}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Delete account' }))
    await user.click(await screen.findByRole('button', { name: 'Cancel' }))

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
    expect(changes).toEqual([])
    expect(screen.getByText(carol.email)).toBeInTheDocument()
  })

  it.each([
    ['Delete account', 'Delete', 'deleted'],
    ['Make user', 'Change role', 'demoted'],
  ])('a two-admin refusal of "%s" names the next steps and leaves the account', async (open, confirm, refused) => {
    server.use(
      http.delete(apiUrl('/api/admin/users/:id'), () =>
        problemResponse('TWO_ADMIN_INVARIANT', `/api/admin/users/${bob.id}`),
      ),
      http.put(apiUrl('/api/admin/users/:id/role'), () =>
        problemResponse('TWO_ADMIN_INVARIANT', `/api/admin/users/${bob.id}/role`),
      ),
    )
    renderApp(`/admin/users/${bob.id}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: open }))
    await user.click(await screen.findByRole('button', { name: confirm }))

    const alert = await screen.findByText(new RegExp(`cannot be ${refused} yet`))
    expect(alert).toHaveAttribute('role', 'alert')
    expect(alert).toHaveTextContent(/invite a new user/)
    expect(screen.getByText('ADMIN')).toBeInTheDocument()
    expect(screen.getByText(bob.email)).toBeInTheDocument()
  })
})
