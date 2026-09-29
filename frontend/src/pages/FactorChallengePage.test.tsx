import { cleanup, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { apiUrl, clearCsrfToken } from '@/lib/api/client'
import type { AdminUser } from '@/lib/admin/users'
import type { Profile } from '@/lib/auth/session'
import { problemResponse } from '@/test/msw/problems'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/renderApp'

const VERIFICATION = apiUrl('/api/mfa/totp/verification')

const enrolledAdmin: Profile = {
  id: '00000000-0000-4000-8000-000000000001',
  username: 'alice-admin',
  role: 'ADMIN',
  passwordChangeRequired: false,
  factors: { held: false, required: true, enrolled: true, rebindRequired: false },
}

const users: AdminUser[] = [
  {
    id: '00000000-0000-4000-8000-000000000001',
    username: 'alice-admin',
    email: 'alice@example.test',
    role: 'ADMIN',
    enabled: true,
    createdAt: '2026-09-01T08:00:00Z',
  },
]

let verifications: unknown[]
let held: boolean

beforeEach(() => {
  clearCsrfToken()
  verifications = []
  held = false
  server.use(
    http.get(apiUrl('/api/profile'), () =>
      HttpResponse.json({ ...enrolledAdmin, factors: { ...enrolledAdmin.factors, held } }),
    ),
    http.get(apiUrl('/api/admin/users'), () =>
      held ? HttpResponse.json(users) : problemResponse('MISSING_FACTOR', '/api/admin/users'),
    ),
    http.post(VERIFICATION, async ({ request }) => {
      verifications.push(await request.json())
      held = true
      return new HttpResponse(null, { status: 204 })
    }),
  )
})

describe('/verify', () => {
  it('lands an enrolled administrator without the factor on the challenge, focused on the code', async () => {
    const { router } = renderApp('/')

    expect(await screen.findByRole('heading', { name: 'TOTP Verification' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/verify')
    expect(screen.getByLabelText('Code from the app')).toHaveFocus()
  })

  it('T-FE-012: a complete code sends nothing until Verify is pressed', async () => {
    renderApp('/verify')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Code from the app'), '123456')
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(verifications).toEqual([])

    await user.click(screen.getByRole('button', { name: 'Verify' }))
    await waitFor(() => expect(verifications).toEqual([{ code: '123456' }]))
  })

  it('a correct code opens the user list, keyboard only', async () => {
    const { router } = renderApp('/verify')
    const user = userEvent.setup()

    await screen.findByLabelText('Code from the app')
    await user.keyboard('123456{Enter}')

    expect(await screen.findByRole('link', { name: 'alice-admin' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/users')
  })

  it('T-FE-013: a refused code is announced on the field, which is cleared on every Verify', async () => {
    server.use(http.post(VERIFICATION, () => problemResponse('INVALID_FACTOR', '/api/mfa/totp/verification')))
    renderApp('/verify')
    const user = userEvent.setup()
    const code = await screen.findByLabelText('Code from the app')

    await user.type(code, '654321')
    await user.click(screen.getByRole('button', { name: 'Verify' }))

    expect(await screen.findByText(/That code was not accepted/)).toHaveAttribute('role', 'alert')
    expect(code).toHaveValue('')
    expect(code).toHaveAttribute('aria-invalid', 'true')
    expect(code).toHaveFocus()
  })

  it('follows FACTOR_ENROLMENT_REQUIRED to enrolment', async () => {
    server.use(
      http.post(VERIFICATION, () => problemResponse('FACTOR_ENROLMENT_REQUIRED', '/api/mfa/totp/verification')),
    )
    const { router } = renderApp('/verify')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Code from the app'), '123456')
    await user.click(screen.getByRole('button', { name: 'Verify' }))

    expect(await screen.findByRole('heading', { name: 'Two-factor authentication' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/settings/mfa')
  })

  it('shows a throttle in the live region and stays on the challenge', async () => {
    server.use(http.post(VERIFICATION, () => problemResponse('TOO_MANY_REQUESTS', '/api/mfa/totp/verification')))
    renderApp('/verify')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Code from the app'), '123456')
    await user.click(screen.getByRole('button', { name: 'Verify' }))

    await waitFor(() =>
      expect(screen.getAllByRole('alert').some((alert) => /Too many attempts/.test(alert.textContent ?? ''))).toBe(
        true,
      ),
    )
  })

  it('a 429 with Retry-After says how long and holds Verify disabled until it elapses', async () => {
    let retryAfter = '1200'
    server.use(
      http.post(VERIFICATION, () => {
        const locked = problemResponse('TOO_MANY_REQUESTS', '/api/mfa/totp/verification', {
          factor: 'TOTP',
          reason: 'LOCKED',
        })
        locked.headers.set('Retry-After', retryAfter)
        return locked
      }),
    )
    renderApp('/verify')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Code from the app'), '123456')
    await user.click(screen.getByRole('button', { name: 'Verify' }))

    expect(await screen.findByText('Too many attempts. Try again in 20 minutes.')).toHaveAttribute('role', 'alert')
    expect(screen.getByRole('button', { name: 'Verify' })).toBeDisabled()

    // A short lock lifts on its own: Verify comes back and the copy clears.
    cleanup()
    retryAfter = '1'
    renderApp('/verify')
    await user.type(await screen.findByLabelText('Code from the app'), '123456')
    await user.click(screen.getByRole('button', { name: 'Verify' }))
    expect(await screen.findByText('Too many attempts. Try again in 1 second.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Verify' })).toBeDisabled()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Verify' })).toBeEnabled(), { timeout: 3000 })
    expect(screen.queryByText(/Try again in/)).not.toBeInTheDocument()
  })

  it('a self-read that fails without a redirect shows an error with a retry, not an endless load', async () => {
    let fail = true
    server.use(
      http.get(apiUrl('/api/profile'), () =>
        fail ? problemResponse('INTERNAL_ERROR', '/api/profile') : HttpResponse.json(enrolledAdmin),
      ),
    )
    renderApp('/verify')
    const user = userEvent.setup()

    expect(await screen.findByText('The service is unavailable. Try again later.')).toHaveAttribute('role', 'alert')
    expect(screen.queryByText('Loading…')).not.toBeInTheDocument()

    fail = false
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'TOTP Verification' })).toBeInTheDocument()
  })
})
