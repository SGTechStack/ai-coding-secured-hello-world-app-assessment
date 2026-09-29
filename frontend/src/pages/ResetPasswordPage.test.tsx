import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { csrfRoute, fakeApi, json, problem, type Route } from '../test/fakeApi'

let App: typeof import('../App').default
beforeEach(async () => {
  vi.resetModules()
  App = (await import('../App')).default
})

afterEach(() => {
  window.history.replaceState(null, '', '/')
})

function renderResetPassword(confirmRoute: Route, hash = '#token=synthetic-reset-token') {
  window.location.hash = hash
  const fetch = fakeApi({
    'GET /csrf': csrfRoute(),
    'GET /me': () => problem(401, 'authentication_required'),
    'POST /password-reset/confirm': confirmRoute,
  })
  render(
    <MemoryRouter initialEntries={['/reset-password']}>
      <App />
    </MemoryRouter>,
  )
  return fetch
}

function fillAndSubmit(newPassword = 'Synthetic-Pass-42') {
  fireEvent.change(screen.getByLabelText('New password'), { target: { value: newPassword } })
  fireEvent.click(screen.getByRole('button', { name: 'Reset password' }))
}

describe('Reset-password screen', () => {
  it('reads the Reset Token from the URL fragment and then clears it from the address bar', async () => {
    renderResetPassword(() => new Response(null, { status: 200 }))

    expect(await screen.findByRole('heading', { name: 'Reset password' })).toBeInTheDocument()
    expect(window.location.hash).toBe('')
  })

  it('shows a Confidential label next to the new-password field', async () => {
    renderResetPassword(() => new Response(null, { status: 200 }))

    const input = await screen.findByLabelText('New password')
    const field = input.closest('.field') as HTMLElement
    expect(within(field).getByText('Confidential')).toBeInTheDocument()
    expect(input).toHaveAccessibleDescription('Confidential')
    expect(input).toHaveAttribute('type', 'password')
  })

  it('posts the token from the fragment and the new password, then goes to the login page with a message', async () => {
    const fetch = renderResetPassword(() => new Response(null, { status: 200 }))
    await screen.findByRole('heading', { name: 'Reset password' })

    fillAndSubmit()

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Your password has been reset. Please log in.')
    const [, init] = fetch.mock.calls.find(([url]) => String(url).endsWith('/password-reset/confirm'))!
    expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json')
    expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('token-1')
    expect(JSON.parse(String(init?.body))).toEqual({
      token: 'synthetic-reset-token',
      newPassword: 'Synthetic-Pass-42',
    })
  })

  it('shows a token_invalid failure', async () => {
    renderResetPassword(() => problem(400, 'token_invalid'))
    await screen.findByRole('heading', { name: 'Reset password' })

    fillAndSubmit()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This reset link is invalid or has expired. Request a new one.',
    )
    expect(screen.getByRole('heading', { name: 'Reset password' })).toBeInTheDocument()
  })

  it('shows every broken password rule under the new password', async () => {
    renderResetPassword(() =>
      json(
        400,
        { status: 400, code: 'password_policy', violations: ['min_length', 'uppercase'] },
        'application/problem+json',
      ),
    )
    await screen.findByRole('heading', { name: 'Reset password' })

    fillAndSubmit('weak')

    expect(await screen.findByRole('alert')).toHaveTextContent('The new password does not meet the password policy.')
    const field = screen.getByLabelText('New password').closest('.field') as HTMLElement
    expect(within(field).getByText('Use at least 12 characters.')).toBeInTheDocument()
    expect(within(field).getByText('Include an uppercase letter.')).toBeInTheDocument()
  })

  it('shows a recently used password', async () => {
    renderResetPassword(() => problem(400, 'password_history'))
    await screen.findByRole('heading', { name: 'Reset password' })

    fillAndSubmit()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The new password was used recently. Choose a password other than your last 3.',
    )
  })

  it('shows a rate-limited failure', async () => {
    renderResetPassword(() => problem(429, 'too_many_requests'))
    await screen.findByRole('heading', { name: 'Reset password' })

    fillAndSubmit()

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts. Please try again later.')
  })

  it('shows a generic error for anything else', async () => {
    renderResetPassword(() => problem(500, 'internal_error'))
    await screen.findByRole('heading', { name: 'Reset password' })

    fillAndSubmit()

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong. Please try again later.')
  })

  it('shows a generic error when the API cannot be reached', async () => {
    renderResetPassword(() => {
      throw new TypeError('Failed to fetch')
    })
    await screen.findByRole('heading', { name: 'Reset password' })

    fillAndSubmit()

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong. Please try again later.')
  })

  it('links back to the login page', async () => {
    renderResetPassword(() => new Response(null, { status: 200 }))
    await screen.findByRole('heading', { name: 'Reset password' })

    fireEvent.click(screen.getByRole('link', { name: 'Back to login' }))

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
