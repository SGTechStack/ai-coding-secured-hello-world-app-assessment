import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fakeApi, json, problem, type Route } from '../test/fakeApi'

let App: typeof import('../App').default
beforeEach(async () => {
  vi.resetModules()
  App = (await import('../App')).default
})

const adminAccount = {
  id: '00000000-0000-0000-0000-00000000000a',
  username: 'testadmin123',
  email: 'testadmin123@test.example.com',
  role: 'ADMIN',
  passwordChangeRequired: false,
}

const userAccount = {
  ...adminAccount,
  id: '00000000-0000-0000-0000-000000000123',
  username: 'testuser123',
  role: 'USER',
}

const accounts = [
  {
    id: adminAccount.id,
    username: 'testadmin123',
    email: 'testadmin123@test.example.com',
    role: 'ADMIN',
    enabled: true,
    locked: false,
    createdAt: '2026-01-05T01:00:00Z',
  },
  {
    id: userAccount.id,
    username: '<b>testuser123</b>',
    email: 'testuser123@test.example.com',
    role: 'USER',
    enabled: false,
    locked: true,
    createdAt: '2026-01-06T02:30:00Z',
  },
]

function renderApp(
  me: object,
  entry: string,
  listRoute: Route = () => json(200, accounts),
  extraRoutes: Record<string, Route> = {},
) {
  const fetch = fakeApi({
    'GET /me': () => json(200, me),
    'GET /hello': () => new Response('Hello, someone', { headers: { 'Content-Type': 'text/plain' } }),
    'GET /csrf': () => json(200, { headerName: 'X-CSRF-TOKEN', token: 'token-1' }),
    'GET /admin/users': listRoute,
    ...extraRoutes,
  })
  render(
    <MemoryRouter initialEntries={[entry]}>
      <App />
    </MemoryRouter>,
  )
  return fetch
}

const requestedPaths = (fetch: ReturnType<typeof fakeApi>) =>
  fetch.mock.calls.map(([url]) => new URL(String(url)).pathname)

describe('admin users screen', () => {
  it('is linked from hello for an Admin and lists every Account with its role and state', async () => {
    renderApp(adminAccount, '/')

    fireEvent.click(await screen.findByRole('link', { name: 'Manage Accounts' }))

    expect(await screen.findByRole('heading', { name: 'Accounts' })).toBeInTheDocument()
    const rows = await screen.findAllByRole('row')
    expect(rows).toHaveLength(3)
    const admin = within(rows[1])
    expect(admin.getByText('testadmin123')).toBeInTheDocument()
    expect(admin.getByText('testadmin123@test.example.com')).toBeInTheDocument()
    expect(admin.getByText('Admin')).toBeInTheDocument()
    expect(admin.getByText('Enabled')).toBeInTheDocument()
    const user = within(rows[2])
    expect(user.getByText('User')).toBeInTheDocument()
    expect(user.getByText('Disabled')).toBeInTheDocument()
    expect(user.getByText('Locked')).toBeInTheDocument()
    expect(rows[2].querySelector('time')).toHaveAttribute('dateTime', '2026-01-06T02:30:00Z')
  })

  it('renders Account data as text, never as HTML', async () => {
    renderApp(adminAccount, '/admin/users')

    expect(await screen.findByText('<b>testuser123</b>')).toBeInTheDocument()
    expect(document.querySelector('td b')).toBeNull()
  })

  it('hides the link and the screen from a User without asking the API', async () => {
    const fetch = renderApp(userAccount, '/admin/users')

    expect(await screen.findByRole('heading', { name: 'Hello, someone' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Manage Accounts' })).not.toBeInTheDocument()
    expect(requestedPaths(fetch)).not.toContain('/api/admin/users')
  })

  it('sends a Visitor to login', async () => {
    fakeApi({ 'GET /me': () => problem(401, 'authentication_required') })
    render(
      <MemoryRouter initialEntries={['/admin/users']}>
        <App />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
  })

  it('says so when the API refuses the list', async () => {
    renderApp(adminAccount, '/admin/users', () => problem(403, 'access_denied'))

    expect(await screen.findByRole('alert')).toHaveTextContent('not allowed')
  })

  it('shows a generic error when the list fails', async () => {
    renderApp(adminAccount, '/admin/users', () => problem(500, 'internal_error'))

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
  })

  it('disables an Account and reloads the list to show the new state', async () => {
    const target = accounts[0]
    let disabled = false
    const listRoute: Route = () =>
      json(
        200,
        accounts.map((account) => (account.id === target.id ? { ...account, enabled: !disabled } : account)),
      )
    const fetch = renderApp(adminAccount, '/admin/users', listRoute, {
      [`PATCH /admin/users/${target.id}/enabled`]: (init) => {
        disabled = true
        expect(JSON.parse(String(init.body))).toEqual({ enabled: false })
        return new Response(null, { status: 200 })
      },
    })

    fireEvent.click(await screen.findByRole('button', { name: 'Disable' }))

    await waitFor(() => expect(screen.getAllByRole('button', { name: 'Enable' })).toHaveLength(2))
    expect(fetch.mock.calls.some(([url]) => String(url).endsWith(`/admin/users/${target.id}/enabled`))).toBe(true)
  })

  it('changes an Account role and reloads the list to show the new state', async () => {
    const target = accounts[1]
    let changed = false
    const listRoute: Route = () =>
      json(
        200,
        accounts.map((account) => (changed && account.id === target.id ? { ...account, role: 'ADMIN' } : account)),
      )
    renderApp(adminAccount, '/admin/users', listRoute, {
      [`PATCH /admin/users/${target.id}/role`]: (init) => {
        changed = true
        expect(JSON.parse(String(init.body))).toEqual({ role: 'ADMIN' })
        return new Response(null, { status: 200 })
      },
    })

    fireEvent.click(await screen.findByRole('button', { name: 'Make Admin' }))

    await waitFor(() => expect(screen.getAllByRole('button', { name: 'Make User' })).toHaveLength(2))
  })

  it('offers Unlock only for a Locked Account, and it clears after a successful unlock', async () => {
    const target = accounts[1]
    let unlocked = false
    const listRoute: Route = () =>
      json(
        200,
        accounts.map((account) => (account.id === target.id ? { ...account, locked: !unlocked } : account)),
      )
    renderApp(adminAccount, '/admin/users', listRoute, {
      [`POST /admin/users/${target.id}/unlock`]: () => {
        unlocked = true
        return new Response(null, { status: 200 })
      },
    })
    await screen.findAllByRole('row')
    expect(screen.queryByRole('button', { name: 'Unlock' })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Unlock' })).toHaveLength(1)

    fireEvent.click(screen.getByRole('button', { name: 'Unlock' }))

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Unlock' })).not.toBeInTheDocument())
  })

  it('requires a password change on an Account and reloads the list', async () => {
    const target = accounts[1]
    let required = false
    const fetch = renderApp(adminAccount, '/admin/users', () => json(200, accounts), {
      [`POST /admin/users/${target.id}/require-password-change`]: (init) => {
        required = true
        expect(init.body).toBeUndefined()
        return new Response(null, { status: 200 })
      },
    })
    const rows = await screen.findAllByRole('row')

    fireEvent.click(within(rows[2]).getByRole('button', { name: 'Require password change' }))

    await waitFor(() => expect(required).toBe(true))
    expect(fetch.mock.calls.filter(([url]) => String(url).endsWith('/api/admin/users')).length).toBeGreaterThanOrEqual(
      2,
    )
  })

  it('shows a clear message when requiring a password change on an unknown Account', async () => {
    const target = accounts[1]
    renderApp(adminAccount, '/admin/users', undefined, {
      [`POST /admin/users/${target.id}/require-password-change`]: () => problem(404, 'not_found'),
    })
    const rows = await screen.findAllByRole('row')

    fireEvent.click(within(rows[2]).getByRole('button', { name: 'Require password change' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('no longer exists')
  })

  it('shows a clear message when an Admin cannot unlock their own Account', async () => {
    const target = accounts[0]
    renderApp(
      adminAccount,
      '/admin/users',
      () =>
        json(200, [
          { ...target, locked: true },
          { ...accounts[1], locked: false },
        ]),
      {
        [`POST /admin/users/${target.id}/unlock`]: () => problem(403, 'self_action_forbidden'),
      },
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Unlock' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('cannot perform this action on their own Account')
  })

  it('shows a clear message when unlocking an unknown Account', async () => {
    const target = accounts[1]
    renderApp(adminAccount, '/admin/users', undefined, {
      [`POST /admin/users/${target.id}/unlock`]: () => problem(404, 'not_found'),
    })

    fireEvent.click(await screen.findByRole('button', { name: 'Unlock' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('no longer exists')
  })

  it('asks for confirmation before deleting, and does not call the API until it is given', async () => {
    const target = accounts[1]
    let deleted = false
    const fetch = renderApp(adminAccount, '/admin/users', undefined, {
      [`DELETE /admin/users/${target.id}`]: () => {
        deleted = true
        return new Response(null, { status: 204 })
      },
    })
    const rows = await screen.findAllByRole('row')

    fireEvent.click(within(rows[2]).getByRole('button', { name: 'Delete' }))

    const confirmation = within(rows[2]).getByRole('group', { name: 'Confirm deleting <b>testuser123</b>' })
    expect(confirmation).toHaveTextContent('can never be registered again')
    expect(confirmation).toHaveAttribute('aria-live', 'polite')
    // Focus must not fall to the document when the Delete button it replaced unmounts.
    expect(within(rows[2]).getByRole('button', { name: 'Cancel' })).toHaveFocus()
    expect(deleted).toBe(false)
    fireEvent.click(within(rows[2]).getByRole('button', { name: 'Cancel' }))
    expect(within(rows[2]).getByRole('button', { name: 'Delete' })).toBeInTheDocument()
    expect(deleted).toBe(false)
    expect(requestedPaths(fetch)).not.toContain(`/api/admin/users/${target.id}`)
  })

  it('deletes the Account once confirmed and reloads the list without it', async () => {
    const target = accounts[1]
    let deleted = false
    const listRoute: Route = () => json(200, deleted ? [accounts[0]] : accounts)
    renderApp(adminAccount, '/admin/users', listRoute, {
      [`DELETE /admin/users/${target.id}`]: () => {
        deleted = true
        return new Response(null, { status: 204 })
      },
    })
    const rows = await screen.findAllByRole('row')

    fireEvent.click(within(rows[2]).getByRole('button', { name: 'Delete' }))
    fireEvent.click(within(rows[2]).getByRole('button', { name: 'Confirm delete' }))

    await waitFor(() => expect(screen.getAllByRole('row')).toHaveLength(2))
    expect(screen.queryByText('<b>testuser123</b>')).not.toBeInTheDocument()
  })

  it('shows a clear message when an Admin confirms deleting their own Account', async () => {
    const target = accounts[0]
    renderApp(adminAccount, '/admin/users', undefined, {
      [`DELETE /admin/users/${target.id}`]: () => problem(403, 'self_action_forbidden'),
    })
    const rows = await screen.findAllByRole('row')

    fireEvent.click(within(rows[1]).getByRole('button', { name: 'Delete' }))
    fireEvent.click(within(rows[1]).getByRole('button', { name: 'Confirm delete' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('cannot perform this action on their own Account')
  })

  it('shows a clear message when deleting would leave no enabled Admin', async () => {
    const target = accounts[0]
    renderApp(adminAccount, '/admin/users', undefined, {
      [`DELETE /admin/users/${target.id}`]: () => problem(409, 'last_admin'),
    })
    const rows = await screen.findAllByRole('row')

    fireEvent.click(within(rows[1]).getByRole('button', { name: 'Delete' }))
    fireEvent.click(within(rows[1]).getByRole('button', { name: 'Confirm delete' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('leave no enabled Admin')
  })

  it('shows a clear message when the server refuses a self-action', async () => {
    const target = accounts[0]
    renderApp(adminAccount, '/admin/users', undefined, {
      [`PATCH /admin/users/${target.id}/enabled`]: () => problem(403, 'self_action_forbidden'),
    })

    fireEvent.click(await screen.findByRole('button', { name: 'Disable' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('cannot perform this action on their own Account')
  })

  it('shows a clear message for the last-Admin rule', async () => {
    const target = accounts[0]
    renderApp(adminAccount, '/admin/users', undefined, {
      [`PATCH /admin/users/${target.id}/role`]: () => problem(409, 'last_admin'),
    })

    fireEvent.click(await screen.findByRole('button', { name: 'Make User' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('leave no enabled Admin')
  })
})
