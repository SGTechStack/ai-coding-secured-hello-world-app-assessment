import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { apiUrl } from '@/lib/api/client'
import type { DemoAccount } from '@/lib/auth/demoAccounts'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/renderApp'

const DEMO_ACCOUNTS = apiUrl('/api/dev/demo-accounts')

// Fixture values only; the real ones live in the backend's application-dev.yml and never in the bundle.
const user: DemoAccount = {
  username: 'fixture-user',
  role: 'USER',
  password: 'fixture-user-password',
  passwordChanged: false,
  totp: null,
}
const admin: DemoAccount = {
  username: 'fixture-admin',
  role: 'ADMIN',
  password: 'fixture-admin-password',
  passwordChanged: false,
  totp: { code: '111111', secondsRemaining: 20 },
}

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

describe('DemoAccountsPanel on the sign-in page', () => {
  it('renders nothing when the endpoint is refused, as it is outside dev', async () => {
    let asked = false
    server.use(
      http.get(DEMO_ACCOUNTS, () => {
        asked = true
        return HttpResponse.json({ code: 'AUTHENTICATION_FAILED' }, { status: 401 })
      }),
    )
    renderApp('/sign-in')

    await waitFor(() => expect(asked).toBe(true))
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Demo accounts' })).not.toBeInTheDocument()
  })

  it('renders nothing for a 200 with no accounts', async () => {
    const calls = serve([])
    renderApp('/sign-in')

    await waitFor(() => expect(calls.count).toBeGreaterThan(0))
    expect(screen.queryByRole('heading', { name: 'Demo accounts' })).not.toBeInTheDocument()
  })

  it('lists what the endpoint returns, and hides a password that has changed', async () => {
    serve([{ ...user, password: null, passwordChanged: true }, admin])
    renderApp('/sign-in')

    const panel = (await screen.findByRole('heading', { name: 'Demo accounts' })).closest('section')!
    expect(within(panel).getByText('fixture-admin-password')).toBeInTheDocument()
    expect(within(panel).getByText('111111')).toHaveAttribute('aria-live', 'polite')
    expect(within(panel).queryByText('fixture-user-password')).not.toBeInTheDocument()
    expect(within(panel).getByText('Changed since it was seeded, so not shown.')).toBeInTheDocument()
    // The sign-in form's labels stay unique: the panel labels no control "Username" or "Password".
    expect(screen.getByLabelText('Username')).toHaveAttribute('id', 'username')
    expect(screen.getByLabelText('Password')).toHaveAttribute('id', 'password')
  })

  it('copies a value and announces it', async () => {
    serve([user])
    renderApp('/sign-in')
    const copy = await screen.findByRole('button', { name: "Copy fixture-user's password" })
    const clicks = userEvent.setup()
    const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue()

    await clicks.click(copy)

    expect(writeText).toHaveBeenCalledWith('fixture-user-password')
    expect(await screen.findByText("Copied fixture-user's password.")).toBeInTheDocument()
  })

  it('fills the sign-in form from an account', async () => {
    serve([user])
    renderApp('/sign-in')

    await userEvent.click(await screen.findByRole('button', { name: 'Fill in fixture-user' }))

    expect(screen.getByLabelText('Username')).toHaveValue('fixture-user')
    expect(screen.getByLabelText('Password')).toHaveValue('fixture-user-password')
  })

  it("fetches the administrator's next code as the current one expires", async () => {
    const calls = serve(
      [{ ...admin, totp: { code: '111111', secondsRemaining: 1 } }],
      [{ ...admin, totp: { code: '222222', secondsRemaining: 30 } }],
    )
    renderApp('/sign-in')

    expect(await screen.findByText('111111')).toBeInTheDocument()
    expect(await screen.findByText('222222', undefined, { timeout: 4000 })).toBeInTheDocument()
    expect(calls.count).toBe(2)
  })
})
