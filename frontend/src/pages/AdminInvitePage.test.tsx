import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { apiUrl, clearCsrfToken } from '@/lib/api/client'
import type { Profile } from '@/lib/auth/session'
import { problemResponse } from '@/test/msw/problems'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/renderApp'

const TOKEN = 'iNv1te_abcdefghijklmnopqrstuvwxyz0123456789'

const verifiedAdmin: Profile = {
  id: '00000000-0000-4000-8000-000000000001',
  username: 'alice-admin',
  role: 'ADMIN',
  passwordChangeRequired: false,
  factors: { held: true, required: true, enrolled: true, rebindRequired: false },
}

let invites: unknown[]

beforeEach(() => {
  clearCsrfToken()
  invites = []
  server.use(
    http.get(apiUrl('/api/profile'), () => HttpResponse.json(verifiedAdmin)),
    http.get(apiUrl('/api/admin/users'), () => HttpResponse.json([])),
    http.post(apiUrl('/api/admin/users'), async ({ request }) => {
      invites.push(await request.json())
      return HttpResponse.json(
        { userId: '00000000-0000-4000-8000-000000000009', token: TOKEN },
        { status: 201, headers: { 'Cache-Control': 'no-store' } },
      )
    }),
  )
})

async function fillAndSubmit(role?: string) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Username'), 'dave')
  await user.type(screen.getByLabelText('Email address'), 'dave@example.test')
  if (role) {
    await user.selectOptions(screen.getByLabelText('Role'), role)
  }
  await user.click(screen.getByRole('button', { name: 'Create invitation' }))
}

describe('/admin/users/invite', () => {
  it('is reached from the user list', async () => {
    const { router } = renderApp('/admin/users')

    await userEvent.setup().click(await screen.findByRole('link', { name: 'Invite a user' }))

    expect(router.state.location.pathname).toBe('/admin/users/invite')
    expect(await screen.findByRole('heading', { name: 'Invite a user' })).toBeInTheDocument()
  })

  it('invites with the chosen role and shows the activation link once, with no password asked', async () => {
    renderApp('/admin/users/invite')

    expect(await screen.findByLabelText('Username')).toBeInTheDocument()
    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument()
    await fillAndSubmit('ADMIN')

    expect(await screen.findByText(`${window.location.origin}/activate#token=${TOKEN}`)).toBeInTheDocument()
    expect(screen.getByText(TOKEN)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Invitation for dave' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Create invitation' })).not.toBeInTheDocument()
    expect(invites).toEqual([{ username: 'dave', email: 'dave@example.test', role: 'ADMIN' }])
  })

  it('a taken or tombstoned identifier is shown on the form and nothing is shown once', async () => {
    server.use(http.post(apiUrl('/api/admin/users'), () => problemResponse('USER_EXISTS', '/api/admin/users')))
    renderApp('/admin/users/invite')

    await fillAndSubmit()

    expect(await screen.findByText(/already has that username or email address/)).toBeInTheDocument()
    expect(screen.getByLabelText('Username')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.queryByText(TOKEN)).not.toBeInTheDocument()
  })

  it('checks the username rule before sending anything', async () => {
    renderApp('/admin/users/invite')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Username'), 'Dave')
    await user.type(screen.getByLabelText('Email address'), 'dave@example.test')
    await user.click(screen.getByRole('button', { name: 'Create invitation' }))

    expect(await screen.findByText(/Use 3 to 32 characters/)).toBeInTheDocument()
    expect(invites).toEqual([])
  })
})
