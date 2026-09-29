import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { apiUrl, clearCsrfToken } from '@/lib/api/client'
import type { Profile } from '@/lib/auth/session'
import { problemResponse } from '@/test/msw/problems'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/renderApp'

const VERIFICATION = apiUrl('/api/mfa/totp/verification')
const TERMINAL = { name: 'Authenticator disabled' }

const disabledAdmin: Profile = {
  id: '00000000-0000-4000-8000-000000000001',
  username: 'alice-admin',
  role: 'ADMIN',
  passwordChangeRequired: false,
  factors: { held: false, required: true, enrolled: true, rebindRequired: true },
}

/** Records whether the code prompt was ever put in the document, however briefly. */
let promptOffered: boolean
let observer: MutationObserver

beforeEach(() => {
  clearCsrfToken()
  promptOffered = false
  observer = new MutationObserver((records) => {
    for (const record of records) {
      for (const node of record.addedNodes) {
        if (node instanceof Element && (node.matches('#totp-code') || node.querySelector('#totp-code'))) {
          promptOffered = true
        }
      }
    }
  })
  observer.observe(document.body, { childList: true, subtree: true })
  server.use(http.get(apiUrl('/api/profile'), () => HttpResponse.json(disabledAdmin)))
})

afterEach(() => {
  observer.disconnect()
})

describe('the terminal factor state', () => {
  it('T-FE-001: rebindRequired lands the admin on the terminal screen and the code prompt is never offered', async () => {
    const { router } = renderApp('/')

    expect(await screen.findByRole('heading', TERMINAL)).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/factor-disabled')
    expect(screen.getByText(/Another administrator must reset it/)).toBeInTheDocument()
    expect(promptOffered).toBe(false)
  })

  it('T-FE-001: opening the challenge directly never offers the prompt to a disabled factor', async () => {
    const { router } = renderApp('/verify')

    expect(await screen.findByRole('heading', TERMINAL)).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/factor-disabled')
    expect(promptOffered).toBe(false)
  })

  it('T-FE-001: a 423 FACTOR_DISABLED from the admin surface overrides a stale belief', async () => {
    server.use(
      http.get(apiUrl('/api/profile'), () =>
        HttpResponse.json({
          ...disabledAdmin,
          factors: { ...disabledAdmin.factors, held: true, rebindRequired: false },
        }),
      ),
      http.get(apiUrl('/api/admin/users'), () => problemResponse('FACTOR_DISABLED', '/api/admin/users')),
    )
    const { router } = renderApp('/admin/users')

    expect(await screen.findByRole('heading', TERMINAL)).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/factor-disabled')
  })

  it('a 423 at verification leaves the challenge for the terminal screen', async () => {
    server.use(
      http.get(apiUrl('/api/profile'), () =>
        HttpResponse.json({ ...disabledAdmin, factors: { ...disabledAdmin.factors, rebindRequired: false } }),
      ),
      http.post(VERIFICATION, () => problemResponse('FACTOR_DISABLED', '/api/mfa/totp/verification')),
    )
    const { router } = renderApp('/verify')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Code from the app'), '123456')
    await user.click(screen.getByRole('button', { name: 'Verify' }))

    expect(await screen.findByRole('heading', TERMINAL)).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/factor-disabled')
  })

  it('signs out to the sign-in page', async () => {
    server.use(http.post(apiUrl('/api/logout'), () => new HttpResponse(null, { status: 204 })))
    const { router } = renderApp('/factor-disabled')
    const user = userEvent.setup()

    await screen.findByRole('heading', TERMINAL)
    await user.click(screen.getByRole('button', { name: 'Sign out' }))

    await screen.findByRole('heading', { name: /sign in/i })
    expect(router.state.location.pathname).toBe('/sign-in')
  })
})
