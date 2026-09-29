import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { AdminUser } from '../api/client'
import { csrfResponse, json, mockApi } from '../test/mockApi'
import { renderApp } from '../test/renderApp'

const admin: AdminUser = {
  id: 'admin-id',
  username: 'boss',
  email: 'boss@example.com',
  role: 'ADMIN',
  enabled: true,
  createdAt: '2026-09-01T00:00:00Z',
}
const alice: AdminUser = { ...admin, id: 'alice-id', username: 'alice', email: 'alice@example.com', role: 'USER' }

const page = (items: AdminUser[]) => json(200, { items, page: 0, size: 20, totalItems: items.length, totalPages: 1 })

describe('admin users page', () => {
  it('offers no actions on the signed-in admin’s own account', async () => {
    mockApi({
      'GET /api/me': () => json(200, { id: admin.id, username: admin.username, role: 'ADMIN' }),
      'GET /api/admin/users': () => page([admin, alice]),
    })

    renderApp('/admin')

    expect(await screen.findByRole('button', { name: 'Delete alice' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Delete boss' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Disable boss' })).toBeDisabled()
    expect(screen.getByRole('combobox', { name: 'Role for boss' })).toBeDisabled()
  })

  it('disables a user and reloads the list', async () => {
    let current = alice
    const requests = mockApi({
      'GET /api/me': () => json(200, { id: admin.id, username: admin.username, role: 'ADMIN' }),
      'GET /api/admin/users': () => page([admin, current]),
      'GET /api/auth/csrf': csrfResponse,
      'PATCH /api/admin/users/alice-id/status': () => {
        current = { ...alice, enabled: false }
        return json(200, current)
      },
    })
    const user = userEvent.setup()
    renderApp('/admin')

    await user.click(await screen.findByRole('button', { name: 'Disable alice' }))

    expect(await screen.findByRole('status')).toHaveTextContent('alice disabled.')
    const row = screen.getByRole('row', { name: /alice/ })
    expect(within(row).getByText('Disabled')).toBeInTheDocument()
    expect(requests.find((r) => r.method === 'PATCH')?.body).toEqual({ enabled: false })
  })

  it('asks for confirmation before deleting', async () => {
    const requests = mockApi({
      'GET /api/me': () => json(200, { id: admin.id, username: admin.username, role: 'ADMIN' }),
      'GET /api/admin/users': () => page([admin, alice]),
    })
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const user = userEvent.setup()
    renderApp('/admin')

    await user.click(await screen.findByRole('button', { name: 'Delete alice' }))

    expect(window.confirm).toHaveBeenCalledWith('Delete alice? This cannot be undone.')
    expect(requests.some((r) => r.method === 'DELETE')).toBe(false)
  })

  it('shows non-admins an access denied page without calling the admin API', async () => {
    const requests = mockApi({
      'GET /api/me': () => json(200, { id: alice.id, username: alice.username, role: 'USER' }),
    })

    renderApp('/admin')

    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
    expect(requests.some((r) => r.path.startsWith('/api/admin'))).toBe(false)
  })
})
