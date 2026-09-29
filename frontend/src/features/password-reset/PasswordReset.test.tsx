import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearCsrfToken } from '../../shared/api/http.ts'
import { emptyResponse, jsonResponse, stubApi } from '../../test/fakeApi.ts'
import { ANONYMOUS, renderApp, signedIn } from '../../test/renderApp.tsx'

const ACCEPTED = 'If that email is registered, a password reset link has been sent.'
const UNAVAILABLE = 'Unable to connect to the server. Please try again later.'

beforeEach(() => {
  clearCsrfToken()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('forgot password', () => {
  it('[assessment/story6-ac4] requests a reset link from the forgot-password page', async () => {
    const api = stubApi({
      ...ANONYMOUS,
      'POST /auth/password-reset/request': () => jsonResponse({ message: ACCEPTED }, 202),
    })
    const user = userEvent.setup()
    const router = renderApp('/login')

    await user.click(await screen.findByRole('link', { name: 'Forgot password?' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/forgot-password'))
    await user.type(await screen.findByLabelText('Email'), 'nobody@example.com')
    await user.click(screen.getByRole('button', { name: 'Send reset link' }))

    expect(await screen.findByRole('status')).toHaveTextContent(ACCEPTED)
    expect(api.calls('POST /auth/password-reset/request')[0].body).toEqual({
      email: 'nobody@example.com',
    })
    expect(screen.getByRole('button', { name: 'Send reset link' })).toBeEnabled()
    // Any input reaches the server, which answers the same way for malformed emails.
    expect(screen.getByRole('button', { name: 'Send reset link' }).closest('form')).toHaveAttribute(
      'novalidate',
    )
  })

  it.each([
    ['a server failure', () => emptyResponse(500)],
    ['a network failure', () => Promise.reject(new TypeError('offline'))],
  ])('shows the unavailable message after %s', async (_label, response) => {
    stubApi({ ...ANONYMOUS, 'POST /auth/password-reset/request': response })
    const user = userEvent.setup()
    renderApp('/forgot-password')

    await user.type(await screen.findByLabelText('Email'), 'john@example.com')
    await user.click(screen.getByRole('button', { name: 'Send reset link' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(UNAVAILABLE)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('sends a signed-in user home', async () => {
    stubApi(signedIn('johndoe', 'USER'))
    const router = renderApp('/forgot-password')

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
  })
})

describe('reset password', () => {
  async function fill(
    user: ReturnType<typeof userEvent.setup>,
    newPassword: string,
    confirmation: string,
  ) {
    await user.type(await screen.findByLabelText('New password'), newPassword)
    await user.type(screen.getByLabelText('Confirm new password'), confirmation)
    await user.click(screen.getByRole('button', { name: 'Reset password' }))
  }

  it('[assessment/story7-ac6] sets a new password from the emailed link and returns to log in', async () => {
    const api = stubApi({
      ...ANONYMOUS,
      'POST /auth/password-reset/confirm': () => emptyResponse(204),
    })
    const user = userEvent.setup()
    const router = renderApp('/reset-password?token=valid-reset-token')

    await fill(user, 'a-brand-new-passphrase', 'a-brand-new-passphrase')

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Your password has been reset. Please log in.',
    )
    expect(router.state.location.pathname).toBe('/login')
    expect(api.calls('POST /auth/password-reset/confirm')[0].body).toEqual({
      token: 'valid-reset-token',
      newPassword: 'a-brand-new-passphrase',
    })
  })

  it('refuses mismatched passwords without sending anything', async () => {
    const api = stubApi(ANONYMOUS)
    const user = userEvent.setup()
    renderApp('/reset-password?token=valid-reset-token')

    await fill(user, 'a-brand-new-passphrase', 'a-different-passphrase')

    expect(await screen.findByRole('alert')).toHaveTextContent('Passwords do not match')
    expect(api.calls('POST /auth/password-reset/confirm')).toHaveLength(0)
  })

  it('shows the server message for a rejected token or password', async () => {
    stubApi({
      ...ANONYMOUS,
      'POST /auth/password-reset/confirm': () =>
        jsonResponse({ message: 'Invalid or expired reset token' }, 400),
    })
    const user = userEvent.setup()
    const router = renderApp('/reset-password?token=used')

    await fill(user, 'a-brand-new-passphrase', 'a-brand-new-passphrase')

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid or expired reset token')
    expect(router.state.location.pathname).toBe('/reset-password')
    expect(screen.getByRole('button', { name: 'Reset password' })).toBeEnabled()
  })

  it.each([
    ['a server failure', () => emptyResponse(500)],
    ['a 400 without a message', () => jsonResponse({}, 400)],
    ['a network failure', () => Promise.reject(new TypeError('offline'))],
  ])('shows the unavailable message after %s', async (_label, response) => {
    const api = stubApi({ ...ANONYMOUS, 'POST /auth/password-reset/confirm': response })
    const user = userEvent.setup()
    renderApp('/reset-password')

    await fill(user, 'a-brand-new-passphrase', 'a-brand-new-passphrase')

    expect(await screen.findByRole('alert')).toHaveTextContent(UNAVAILABLE)
    expect(api.calls('POST /auth/password-reset/confirm')[0].body).toEqual({
      token: '',
      newPassword: 'a-brand-new-passphrase',
    })
  })
})
