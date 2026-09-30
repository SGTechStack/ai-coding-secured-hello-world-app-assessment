import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import { apiUrl, clearCsrfToken } from '@/lib/api/client'
import type { AdminUser } from '@/lib/admin/users'
import type { Profile } from '@/lib/auth/session'
import { adminRoutes, createQueryClient } from '@/routes'
import { FactorChallengePage } from '@/pages/FactorChallengePage'
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

const users: AdminUser[] = [
  {
    id: '00000000-0000-4000-8000-000000000001',
    username: 'alice-admin',
    email: 'alice@example.test',
    role: 'ADMIN',
    enabled: true,
    activated: true,
    createdAt: '2026-09-01T08:00:00Z',
  },
  {
    id: '00000000-0000-4000-8000-000000000002',
    username: 'bob',
    email: 'bob@example.test',
    role: 'USER',
    enabled: false,
    activated: true,
    createdAt: '2026-09-02T09:30:00Z',
  },
]

beforeEach(() => {
  clearCsrfToken()
  server.use(
    http.get(apiUrl('/api/profile'), () => HttpResponse.json(verifiedAdmin)),
    http.get(apiUrl('/api/admin/users'), () => HttpResponse.json(users)),
    http.get(apiUrl('/api/admin/users/:id'), ({ params }) => {
      const found = users.find((user) => user.id === params.id)
      return found ? HttpResponse.json(found) : problemResponse('ACCESS_DENIED', `/api/admin/users/${params.id}`)
    }),
  )
})

describe('/admin/users', () => {
  it('lists every user with the PRD fields in a table with column headers', async () => {
    renderApp('/admin/users')

    const table = await screen.findByRole('table')
    expect(
      within(table)
        .getAllByRole('columnheader')
        .map((header) => header.textContent),
    ).toEqual(['Username', 'Email', 'Role', 'Status', 'Created'])
    const bob = within(table).getByRole('row', { name: /bob/ })
    expect(within(bob).getByText('bob@example.test')).toBeInTheDocument()
    expect(within(bob).getByText('USER')).toBeInTheDocument()
    expect(within(bob).getByText('Disabled')).toBeInTheDocument()
    expect(within(bob).getByText('2026-09-02')).toHaveAttribute('datetime', '2026-09-02T09:30:00Z')
  })

  it('opens a user from the table by keyboard alone and comes back', async () => {
    const { router } = renderApp('/admin/users')
    const user = userEvent.setup()
    await screen.findByRole('table')

    await user.tab()
    expect(screen.getByRole('link', { name: 'alice-admin' })).toHaveFocus()
    await user.tab()
    expect(screen.getByRole('link', { name: 'bob' })).toHaveFocus()
    await user.keyboard('{Enter}')

    expect(await screen.findByRole('heading', { name: 'bob' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/users/00000000-0000-4000-8000-000000000002')
    expect(screen.getByText('bob@example.test')).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Back to the user list' }))
    expect(await screen.findByRole('table')).toBeInTheDocument()
  })

  it('shows an unknown user as not found', async () => {
    renderApp('/admin/users/00000000-0000-4000-8000-00000000dead')

    expect(await screen.findByRole('alert')).toHaveTextContent('That user could not be found.')
  })

  it('the gate sends an administrator without the factor to the challenge before asking for the list', async () => {
    let listed = 0
    server.use(
      http.get(apiUrl('/api/profile'), () =>
        HttpResponse.json({ ...verifiedAdmin, factors: { ...verifiedAdmin.factors, held: false } }),
      ),
      http.get(apiUrl('/api/admin/users'), () => {
        listed += 1
        return HttpResponse.json(users)
      }),
    )

    const { router } = renderApp('/admin/users')

    expect(await screen.findByRole('heading', { name: 'TOTP Verification' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/verify')
    expect(listed).toBe(0)
  })

  it('the gate sends an unenrolled administrator to enrolment', async () => {
    server.use(
      http.get(apiUrl('/api/profile'), () =>
        HttpResponse.json({ ...verifiedAdmin, factors: { ...verifiedAdmin.factors, held: false, enrolled: false } }),
      ),
    )
    const unenrolled = renderApp('/admin/users')
    expect(await screen.findByRole('heading', { name: 'Two-factor authentication' })).toBeInTheDocument()
    expect(unenrolled.router.state.location.pathname).toBe('/settings/mfa')
  })

  it('a user never sees the admin surface: the gate sends them to the greeting without asking for the list', async () => {
    let listed = 0
    server.use(
      http.get(apiUrl('/api/profile'), () =>
        HttpResponse.json({
          ...verifiedAdmin,
          role: 'USER',
          factors: { held: false, required: false, enrolled: false, rebindRequired: false },
        }),
      ),
      http.get(apiUrl('/api/hello'), () => HttpResponse.json({ message: 'Hello, bob' })),
      http.get(apiUrl('/api/admin/users'), () => {
        listed += 1
        return problemResponse('ACCESS_DENIED', '/api/admin/users')
      }),
    )

    const { router } = renderApp('/admin/users')

    expect(await screen.findByRole('heading', { name: 'Hello, bob' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/hello')
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(listed).toBe(0)
  })

  it('T-FE-002: with the guard deleted, the server still keeps the admin data off the page', async () => {
    // The client believes the factor is held; the server says it is not. No gate wraps the page.
    server.use(http.get(apiUrl('/api/admin/users'), () => problemResponse('MISSING_FACTOR', '/api/admin/users')))
    const router = createMemoryRouter([...adminRoutes, { path: '/verify', element: <FactorChallengePage /> }], {
      initialEntries: ['/admin/users'],
    })
    render(
      <QueryClientProvider client={createQueryClient()}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    )

    expect(await screen.findByRole('heading', { name: 'TOTP Verification' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/verify')
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.queryByText('bob@example.test')).not.toBeInTheDocument()
  })
})
