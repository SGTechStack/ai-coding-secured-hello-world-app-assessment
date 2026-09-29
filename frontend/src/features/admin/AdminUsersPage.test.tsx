import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearCsrfToken } from '../../shared/api/http.ts'
import { type ApiRoutes, emptyResponse, jsonResponse, stubApi } from '../../test/fakeApi.ts'
import { renderApp, signedIn } from '../../test/renderApp.tsx'
import type { AdminUser } from './api.ts'

const ADMIN: AdminUser = {
  id: 'a0000000-0000-4000-8000-000000000001',
  username: 'admin',
  email: 'admin@example.com',
  role: 'ADMIN',
  enabled: true,
  createdAt: '2026-01-02T03:04:05.123456Z',
}
const JOHNDOE: AdminUser = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'johndoe',
  email: 'john@example.com',
  role: 'USER',
  enabled: true,
  createdAt: '2026-02-03T23:59:59Z',
}
const JANEDOE: AdminUser = {
  id: 'b0000000-0000-4000-8000-000000000002',
  username: 'janedoe',
  email: 'jane@example.com',
  role: 'USER',
  enabled: false,
  createdAt: '2026-03-04T00:00:00Z',
}

function adminApi(extra: ApiRoutes = {}) {
  return stubApi({
    ...signedIn('admin', 'ADMIN'),
    'GET /admin/users': () => jsonResponse([ADMIN, JOHNDOE, JANEDOE]),
    ...extra,
  })
}

async function rowFor(username: string) {
  const header = await screen.findByRole('rowheader', { name: username })
  return header.closest('tr') as HTMLTableRowElement
}

beforeEach(() => {
  clearCsrfToken()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('admin user management', () => {
  it('[assessment/story8-ac5] an admin opens the user management page from the landing page', async () => {
    adminApi()
    const user = userEvent.setup()
    const router = renderApp('/')

    await user.click(await screen.findByRole('link', { name: 'Manage users' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/users'))
    expect(await screen.findByRole('heading', { name: 'Manage users' })).toBeInTheDocument()
    expect(screen.getAllByRole('columnheader').map((header) => header.textContent)).toEqual([
      'Username',
      'Email',
      'Role',
      'Status',
      'Created',
      'Actions',
    ])
    const jane = within(await rowFor('janedoe'))
    expect(jane.getByText('jane@example.com')).toBeInTheDocument()
    expect(jane.getByText('USER', { selector: 'td' })).toBeInTheDocument()
    expect(jane.getByText('Disabled')).toBeInTheDocument()
    expect(jane.getByText('2026-03-04')).toBeInTheDocument()
    expect(within(await rowFor('admin')).getByText('2026-01-02')).toBeInTheDocument()
    expect(within(await rowFor('johndoe')).getByText('Enabled')).toBeInTheDocument()
  })

  it('[assessment/story8-ac6] a USER sees no link and is sent home from the admin page', async () => {
    const api = stubApi(signedIn('johndoe', 'USER'))
    const router = renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Hello, johndoe' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Manage users' })).not.toBeInTheDocument()

    await router.navigate('/admin/users')

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(api.calls('GET /admin/users')).toHaveLength(0)
  })

  it('sends an anonymous visitor to log in', async () => {
    stubApi({ 'GET /auth/me': () => jsonResponse({}, 401) })
    const router = renderApp('/admin/users')

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
  })

  it('[assessment/story9-ac7] disables a user from the table', async () => {
    const api = adminApi({
      'PATCH /admin/users/11111111-1111-1111-1111-111111111111/status': () =>
        jsonResponse({ ...JOHNDOE, enabled: false }),
    })
    const user = userEvent.setup()
    renderApp('/admin/users')

    await user.click(within(await rowFor('johndoe')).getByRole('button', { name: 'Disable' }))

    const johndoe = within(await rowFor('johndoe'))
    expect(await johndoe.findByText('Disabled')).toBeInTheDocument()
    expect(johndoe.getByRole('button', { name: 'Enable' })).toBeInTheDocument()
    expect(
      within(await rowFor('admin')).queryByRole('button', { name: /Enable|Disable/ }),
    ).not.toBeInTheDocument()
    const [request] = api.calls('PATCH /admin/users/11111111-1111-1111-1111-111111111111/status')
    expect(request.body).toEqual({ enabled: false })
    expect(request.headers.get('X-CSRF-TOKEN')).toBe('csrf-token')
  })

  it('enables a disabled user', async () => {
    const api = adminApi({
      [`PATCH /admin/users/${JANEDOE.id}/status`]: () =>
        jsonResponse({ ...JANEDOE, enabled: true }),
    })
    const user = userEvent.setup()
    renderApp('/admin/users')

    await user.click(within(await rowFor('janedoe')).getByRole('button', { name: 'Enable' }))

    expect(await within(await rowFor('janedoe')).findByText('Enabled')).toBeInTheDocument()
    expect(api.calls(`PATCH /admin/users/${JANEDOE.id}/status`)[0].body).toEqual({ enabled: true })
  })

  it('[assessment/story10-ac6] changes a user role from the table', async () => {
    const api = adminApi({
      [`PATCH /admin/users/${JOHNDOE.id}/role`]: () => jsonResponse({ ...JOHNDOE, role: 'ADMIN' }),
    })
    const user = userEvent.setup()
    renderApp('/admin/users')

    await user.selectOptions(await screen.findByLabelText('Role for johndoe'), 'ADMIN')

    expect(
      await within(await rowFor('johndoe')).findByText('ADMIN', { selector: 'td' }),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Role for johndoe')).toHaveValue('ADMIN')
    expect(screen.queryByLabelText('Role for admin')).not.toBeInTheDocument()
    expect(api.calls(`PATCH /admin/users/${JOHNDOE.id}/role`)[0].body).toEqual({ role: 'ADMIN' })
  })

  it('[assessment/story11-ac5] deletes a user after confirming', async () => {
    const api = adminApi({ [`DELETE /admin/users/${JOHNDOE.id}`]: () => emptyResponse(204) })
    const user = userEvent.setup()
    renderApp('/admin/users')

    await user.click(within(await rowFor('johndoe')).getByRole('button', { name: 'Delete' }))

    const dialog = screen.getByRole('alertdialog', {
      name: 'Delete user johndoe? This cannot be undone.',
    })
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(dialog).toContainElement(document.activeElement as HTMLElement)
    expect(api.calls(`DELETE /admin/users/${JOHNDOE.id}`)).toHaveLength(0)

    await user.click(within(dialog).getByRole('button', { name: 'Delete' }))

    await waitFor(() =>
      expect(screen.queryByRole('rowheader', { name: 'johndoe' })).not.toBeInTheDocument(),
    )
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(api.calls(`DELETE /admin/users/${JOHNDOE.id}`)).toHaveLength(1)
    expect(
      within(await rowFor('admin')).queryByRole('button', { name: 'Delete' }),
    ).not.toBeInTheDocument()
  })

  it.each([
    [
      'Cancel',
      async (user: ReturnType<typeof userEvent.setup>) => {
        await user.click(
          within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Cancel' }),
        )
      },
    ],
    [
      'Escape',
      async (user: ReturnType<typeof userEvent.setup>) => {
        await user.keyboard('{Escape}')
      },
    ],
  ])('closes the delete dialog with %s without a request', async (_label, close) => {
    const api = adminApi()
    const user = userEvent.setup()
    renderApp('/admin/users')
    const trigger = within(await rowFor('johndoe')).getByRole('button', { name: 'Delete' })

    await user.click(trigger)
    await close(user)

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(await rowFor('johndoe')).toBeInTheDocument()
    expect(trigger).toHaveFocus()
    expect(api.calls(`DELETE /admin/users/${JOHNDOE.id}`)).toHaveLength(0)
  })

  it('keeps keyboard focus inside the delete dialog', async () => {
    adminApi()
    const user = userEvent.setup()
    renderApp('/admin/users')
    await user.click(within(await rowFor('johndoe')).getByRole('button', { name: 'Delete' }))
    const dialog = within(screen.getByRole('alertdialog'))

    expect(dialog.getByRole('button', { name: 'Cancel' })).toHaveFocus()
    await user.tab()
    expect(dialog.getByRole('button', { name: 'Delete' })).toHaveFocus()
    await user.tab()
    expect(dialog.getByRole('button', { name: 'Cancel' })).toHaveFocus()
  })

  it.each([
    [
      'the server message',
      () => jsonResponse({ message: 'User not found' }, 404),
      'User not found',
    ],
    [
      'the unavailable message without one',
      () => emptyResponse(500),
      'Unable to connect to the server. Please try again later.',
    ],
    [
      'the unavailable message on a network failure',
      () => Promise.reject(new TypeError('offline')),
      'Unable to connect to the server. Please try again later.',
    ],
  ])('shows %s when an action fails', async (_label, response, message) => {
    adminApi({ [`PATCH /admin/users/${JOHNDOE.id}/status`]: response })
    const user = userEvent.setup()
    renderApp('/admin/users')

    await user.click(within(await rowFor('johndoe')).getByRole('button', { name: 'Disable' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(within(await rowFor('johndoe')).getByText('Enabled')).toBeInTheDocument()
  })

  it('shows the error page when the user list cannot be loaded', async () => {
    stubApi({ ...signedIn('admin', 'ADMIN'), 'GET /admin/users': () => emptyResponse(500) })
    renderApp('/admin/users')

    expect(await screen.findByRole('heading', { name: 'Something went wrong' })).toBeInTheDocument()
  })
})
