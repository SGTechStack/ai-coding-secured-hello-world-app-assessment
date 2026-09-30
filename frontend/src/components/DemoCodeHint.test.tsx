import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { apiUrl, clearCsrfToken } from '@/lib/api/client'
import type { DemoAccount } from '@/lib/auth/demoAccounts'
import type { Profile } from '@/lib/auth/session'
import { problemResponse } from '@/test/msw/problems'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/renderApp'

const DEMO_ACCOUNTS = apiUrl('/api/dev/demo-accounts')
const VERIFICATION = apiUrl('/api/mfa/totp/verification')

// Fixture values only; the real ones live in the backend's application-dev.yml and never in the bundle.
const admin: DemoAccount = {
  username: 'fixture-admin',
  role: 'ADMIN',
  seeded: true,
  email: 'fixture-admin@fixture.test',
  password: 'fixture-admin-password',
  passwordChanged: false,
  totp: { code: '111111', secondsRemaining: 20 },
}

function signedInAs(username: string): Profile {
  return {
    id: '00000000-0000-4000-8000-000000000001',
    username,
    role: 'ADMIN',
    passwordChangeRequired: false,
    factors: { held: false, required: true, enrolled: true, rebindRequired: false },
  }
}

let verifications: unknown[]

/** Serves `bodies` in turn (the last one repeats) and counts the calls. */
function serve(...bodies: DemoAccount[][]) {
  const calls = { count: 0 }
  server.use(
    http.get(DEMO_ACCOUNTS, () => {
      const body = bodies[Math.min(calls.count, bodies.length - 1)]
      calls.count++
      return HttpResponse.json({ accounts: body })
    }),
  )
  return calls
}

function refuse(status: 401 | 404) {
  const calls = { count: 0 }
  server.use(
    http.get(DEMO_ACCOUNTS, () => {
      calls.count++
      return status === 401
        ? problemResponse('AUTHENTICATION_FAILED', '/api/dev/demo-accounts')
        : new HttpResponse(null, { status: 404 })
    }),
  )
  return calls
}

beforeEach(() => {
  clearCsrfToken()
  verifications = []
  server.use(
    http.get(apiUrl('/api/profile'), () => HttpResponse.json(signedInAs('fixture-admin'))),
    http.post(VERIFICATION, async ({ request }) => {
      verifications.push(await request.json())
      return problemResponse('INVALID_FACTOR', '/api/mfa/totp/verification')
    }),
  )
})

describe('DemoCodeHint in the code prompt', () => {
  it("shows the signed-in demo administrator's code, and Fill in fills the field without sending it", async () => {
    serve([admin])
    renderApp('/verify')

    const hint = await screen.findByRole('group', { name: 'Demo code' })
    expect(within(hint).getByText('111111')).toBeInTheDocument()
    expect(within(hint).getByText('111111').closest('[aria-live]')).toHaveAttribute('aria-live', 'polite')

    await userEvent.click(within(hint).getByRole('button', { name: 'Fill in demo code' }))

    expect(screen.getByLabelText('Code from the app')).toHaveValue('111111')
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(verifications).toEqual([])
  })

  it.each([401, 404] as const)('renders nothing, and asks once, when the endpoint answers %i', async (status) => {
    const calls = refuse(status)
    renderApp('/verify')

    await screen.findByLabelText('Code from the app')
    await waitFor(() => expect(calls.count).toBe(1))
    await new Promise((resolve) => setTimeout(resolve, 100))
    expect(calls.count).toBe(1)
    expect(screen.queryByRole('group', { name: 'Demo code' })).not.toBeInTheDocument()
  })

  it('renders nothing for an account that is not a demo account, even in dev', async () => {
    server.use(http.get(apiUrl('/api/profile'), () => HttpResponse.json(signedInAs('alice-admin'))))
    const calls = serve([admin])
    renderApp('/verify')

    await screen.findByLabelText('Code from the app')
    await waitFor(() => expect(calls.count).toBe(1))
    expect(screen.queryByRole('group', { name: 'Demo code' })).not.toBeInTheDocument()
    expect(screen.queryByText('111111')).not.toBeInTheDocument()
  })

  it('fetches the next code as the current one expires', async () => {
    const calls = serve(
      [{ ...admin, totp: { code: '222222', secondsRemaining: 1 } }],
      [{ ...admin, totp: { code: '333333', secondsRemaining: 30 } }],
    )
    renderApp('/verify')

    expect(await screen.findByText('222222')).toBeInTheDocument()
    expect(await screen.findByText('333333', undefined, { timeout: 4000 })).toBeInTheDocument()
    expect(calls.count).toBe(2)
  })

  it('holds back a code this tab just sent until the next one arrives, since it cannot verify again', async () => {
    serve(
      [{ ...admin, totp: { code: '444444', secondsRemaining: 2 } }],
      [{ ...admin, totp: { code: '555555', secondsRemaining: 30 } }],
    )
    renderApp('/verify')
    const user = userEvent.setup()

    const hint = await screen.findByRole('group', { name: 'Demo code' })
    await user.click(await within(hint).findByRole('button', { name: 'Fill in demo code' }))
    await user.click(screen.getByRole('button', { name: 'Verify' }))
    await waitFor(() => expect(verifications).toEqual([{ code: '444444' }]))

    expect(await within(hint).findByText('That code was just used and cannot be used again.')).toBeInTheDocument()
    expect(within(hint).getByText(/^Next code in \d+ s\.$/)).toBeInTheDocument()
    expect(within(hint).queryByRole('button', { name: 'Fill in demo code' })).not.toBeInTheDocument()

    expect(await within(hint).findByText('555555', undefined, { timeout: 4000 })).toBeInTheDocument()
    expect(within(hint).getByRole('button', { name: 'Fill in demo code' })).toBeInTheDocument()
  })
})
