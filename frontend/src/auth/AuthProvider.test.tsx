import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { AxiosError, AxiosHeaders } from 'axios'
import type { ReactNode } from 'react'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './useAuth'
import { RequireSession, RequireUserManager } from './guards'
import * as endpoints from '../api/endpoints'
import type { CurrentUser } from '../api/endpoints'

/**
 * The auth context and the route gates (story 1.23, spec.md S12).
 *
 * The second of exactly two named frontend suites. What it pins is that `role` and
 * `requirePasswordChange` are read from `GET /currentUser` and that the router acts on them in the right
 * order — the forced-change gate **before** the role gate, matching the backend, where the tier-0
 * `PasswordChangeFilter` sits ahead of the authorization matrix.
 *
 * `endpoints` is mocked rather than the transport, because the subject here is the provider's state
 * machine, not the wire format. The wire format is the backend suite's job.
 */

function user(overrides: Partial<CurrentUser> = {}): CurrentUser {
  return {
    username: 'alice',
    email: 'alice@example.com',
    role: 'USER',
    requirePasswordChange: false,
    createdAt: '2026-01-01T00:00:00Z',
    lastLoginAt: null,
    lastPasswordChangeAt: null,
    ...overrides,
  }
}

function unauthorised(): AxiosError {
  const headers = new AxiosHeaders()
  const config = { headers }
  return new AxiosError('unauthenticated', undefined, config, null, {
    status: 401,
    statusText: '',
    data: {},
    headers,
    config,
  })
}

function Probe(): ReactNode {
  const { user: current } = useAuth()
  return <span data-testid="probe">{current ? `${current.username}:${current.role}` : 'anonymous'}</span>
}

function renderApp(initialPath: string): void {
  render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>login screen</p>} />
          <Route path="/change-password" element={<p>change password screen</p>} />
          <Route element={<RequireSession />}>
            <Route
              path="/hello"
              element={
                <>
                  <p>greeting screen</p>
                  <Probe />
                </>
              }
            />
            <Route element={<RequireUserManager />}>
              <Route path="/admin/users" element={<p>user list screen</p>} />
            </Route>
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('the auth context', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('bootstraps by reading GET /currentUser and exposes the role from it', async () => {
    // Not from the login response, and not from localStorage: a role held client-side is a role a user
    // can edit. This state renders screens; it never authorises anything.
    const currentUser = vi.spyOn(endpoints, 'currentUser').mockResolvedValue(user({ role: 'USER_MANAGER' }))

    renderApp('/hello')

    await waitFor(() => expect(screen.getByTestId('probe')).toHaveTextContent('alice:USER_MANAGER'))
    expect(currentUser).toHaveBeenCalledOnce()
  })

  it('resolves a 401 from the bootstrap read to anonymous and shows the login screen', async () => {
    // A cold load with no session is the normal case, not an error. It must not blank the tree.
    vi.spyOn(endpoints, 'currentUser').mockRejectedValue(unauthorised())

    renderApp('/hello')

    await waitFor(() => expect(screen.getByText('login screen')).toBeInTheDocument())
  })

  it('routes a flagged account to change-password and blocks every other route', async () => {
    // The gate ordering that matches the backend: the tier-0 filter pre-empts the authorization matrix,
    // so a flagged USER_MANAGER must not reach the user list either.
    vi.spyOn(endpoints, 'currentUser').mockResolvedValue(
      user({ role: 'USER_MANAGER', requirePasswordChange: true }),
    )

    renderApp('/admin/users')

    await waitFor(() => expect(screen.getByText('change password screen')).toBeInTheDocument())
    expect(screen.queryByText('user list screen')).not.toBeInTheDocument()
  })

  it('lets an unflagged USER_MANAGER reach the admin route', async () => {
    vi.spyOn(endpoints, 'currentUser').mockResolvedValue(user({ role: 'USER_MANAGER' }))

    renderApp('/admin/users')

    await waitFor(() => expect(screen.getByText('user list screen')).toBeInTheDocument())
  })

  it('sends a plain USER who asks for an admin route to the greeting, not to login', async () => {
    // They are legitimately signed in. Bouncing them to a login form would read as a session failure and
    // invite them to re-enter credentials that were never the problem.
    vi.spyOn(endpoints, 'currentUser').mockResolvedValue(user({ role: 'USER' }))

    renderApp('/admin/users')

    await waitFor(() => expect(screen.getByText('greeting screen')).toBeInTheDocument())
    expect(screen.queryByText('login screen')).not.toBeInTheDocument()
  })

  it('clears local state on logout even when the server call fails', async () => {
    // A 401 from logout means the session was already gone -- which is the outcome the user asked for.
    vi.spyOn(endpoints, 'currentUser').mockResolvedValue(user())
    vi.spyOn(endpoints, 'logout').mockRejectedValue(unauthorised())

    function WithLogout(): ReactNode {
      const { logout, user: current } = useAuth()
      return (
        <>
          <span data-testid="probe">{current ? current.username : 'anonymous'}</span>
          <button type="button" onClick={() => void logout()}>
            sign out
          </button>
        </>
      )
    }

    render(
      <MemoryRouter>
        <AuthProvider>
          <WithLogout />
        </AuthProvider>
      </MemoryRouter>,
    )
    await waitFor(() => expect(screen.getByTestId('probe')).toHaveTextContent('alice'))

    await userEvent.click(screen.getByRole('button', { name: 'sign out' }))

    await waitFor(() => expect(screen.getByTestId('probe')).toHaveTextContent('anonymous'))
  })
})
