import { fireEvent, render, screen, within } from '@testing-library/react'
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

function renderApp(me: object, entry: string, listRoute: Route = () => json(200, accounts)) {
  const fetch = fakeApi({
    'GET /me': () => json(200, me),
    'GET /hello': () => new Response('Hello, someone', { headers: { 'Content-Type': 'text/plain' } }),
    'GET /admin/users': listRoute,
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
})
