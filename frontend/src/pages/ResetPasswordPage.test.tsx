import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { csrfResponse, mockApi, problem } from '../test/mockApi'
import { renderApp } from '../test/renderApp'

const notSignedIn = () => problem(401, 'UNAUTHENTICATED', 'Authentication is required.')

describe('reset password page', () => {
  it('takes the token from the link fragment, removes it from the URL and submits it', async () => {
    const requests = mockApi({
      'GET /api/me': notSignedIn,
      'GET /api/auth/csrf': csrfResponse,
      'POST /api/auth/password-reset/confirm': () => new Response(null, { status: 204 }),
    })
    const user = userEvent.setup()
    renderApp('/reset-password#token=secret-token')

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/reset-password$/))
    await user.type(screen.getByLabelText('New password'), 'A-brand-new-passphrase')
    await user.type(screen.getByLabelText('Confirm new password'), 'A-brand-new-passphrase')
    await user.click(screen.getByRole('button', { name: 'Set new password' }))

    expect(await screen.findByText(/Your password has been changed/)).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/login')
    expect(requests.find((r) => r.path === '/api/auth/password-reset/confirm')?.body).toEqual({
      token: 'secret-token',
      newPassword: 'A-brand-new-passphrase',
    })
  })

  it('checks the new password before calling the API', async () => {
    const requests = mockApi({ 'GET /api/me': notSignedIn })
    const user = userEvent.setup()
    renderApp('/reset-password#token=secret-token')

    await user.type(await screen.findByLabelText('New password'), 'A-brand-new-passphrase')
    await user.type(screen.getByLabelText('Confirm new password'), 'A-different-passphrase')
    await user.click(screen.getByRole('button', { name: 'Set new password' }))

    expect(await screen.findByText('Passwords do not match.')).toBeInTheDocument()
    expect(requests.some((r) => r.path.includes('password-reset'))).toBe(false)
  })

  it('shows the server rejection for a used or expired link', async () => {
    mockApi({
      'GET /api/me': notSignedIn,
      'GET /api/auth/csrf': csrfResponse,
      'POST /api/auth/password-reset/confirm': () =>
        problem(400, 'INVALID_RESET_TOKEN', 'This password reset link is invalid or has expired.'),
    })
    const user = userEvent.setup()
    renderApp('/reset-password#token=used-token')

    await user.type(await screen.findByLabelText('New password'), 'A-brand-new-passphrase')
    await user.type(screen.getByLabelText('Confirm new password'), 'A-brand-new-passphrase')
    await user.click(screen.getByRole('button', { name: 'Set new password' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('invalid or has expired')
  })

  it('explains a link without a token', async () => {
    mockApi({ 'GET /api/me': notSignedIn })

    renderApp('/reset-password')

    expect(await screen.findByRole('alert')).toHaveTextContent('invalid or incomplete')
  })
})
