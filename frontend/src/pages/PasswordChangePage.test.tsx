import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fakeApi, json, problem, type Route } from '../test/fakeApi'

let App: typeof import('../App').default
beforeEach(async () => {
  vi.resetModules()
  App = (await import('../App')).default
})

const account = {
  id: '00000000-0000-0000-0000-000000000123',
  username: 'testuser123',
  email: 'testuser123@test.example.com',
  role: 'USER',
  passwordChangeRequired: false,
}

/** A CSRF route that issues token-1, token-2, … so tests can see when a new one was fetched. */
function rotatingCsrf(): Route {
  let issued = 0
  return () => json(200, { headerName: 'X-CSRF-TOKEN', token: `token-${++issued}` })
}

function renderApp(changeRoute: Route, entry = '/password-change') {
  const fetch = fakeApi({
    'GET /csrf': rotatingCsrf(),
    'GET /me': () => json(200, account),
    'GET /hello': () => new Response('Hello, testuser123', { headers: { 'Content-Type': 'text/plain' } }),
    'PATCH /me/password': changeRoute,
  })
  render(
    <MemoryRouter initialEntries={[entry]}>
      <App />
    </MemoryRouter>,
  )
  return fetch
}

async function fillAndSubmit(currentPassword = 'Synthetic-Pass-42', newPassword = 'Synthetic-Pass-43') {
  fireEvent.change(await screen.findByLabelText('Current password'), { target: { value: currentPassword } })
  fireEvent.change(screen.getByLabelText('New password'), { target: { value: newPassword } })
  fireEvent.click(screen.getByRole('button', { name: 'Change password' }))
}

describe('Password Change screen', () => {
  it('is linked from the hello screen', async () => {
    renderApp(() => new Response(null, { status: 200 }), '/')

    fireEvent.click(await screen.findByRole('link', { name: 'Change password' }))

    expect(await screen.findByRole('heading', { name: 'Change password' })).toBeInTheDocument()
  })

  it('shows a Confidential label next to every input field', async () => {
    renderApp(() => new Response(null, { status: 200 }))

    for (const label of ['Current password', 'New password']) {
      const input = await screen.findByLabelText(label)
      const field = input.closest('.field') as HTMLElement
      expect(within(field).getByText('Confidential')).toBeInTheDocument()
      expect(input).toHaveAccessibleDescription('Confidential')
      expect(input).toHaveAttribute('type', 'password')
    }
  })

  it('sends both passwords as JSON with the CSRF token and goes to the login page with a message', async () => {
    const fetch = renderApp(() => new Response(null, { status: 200 }))

    await fillAndSubmit()

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Your password has been changed. Please log in again.')
    const [, init] = fetch.mock.calls.find(([url]) => String(url).endsWith('/me/password'))!
    expect(init?.method).toBe('PATCH')
    expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json')
    expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('token-1')
    expect(init?.credentials).toBe('include')
    expect(JSON.parse(String(init?.body))).toEqual({
      currentPassword: 'Synthetic-Pass-42',
      newPassword: 'Synthetic-Pass-43',
    })
    // The server ended the Session and its CSRF token, so a new token was fetched.
    expect(fetch.mock.calls.filter(([url]) => String(url).endsWith('/csrf'))).toHaveLength(2)
  })

  it('still goes to the login page when fetching the new CSRF token fails after a successful change', async () => {
    let csrfCalls = 0
    fakeApi({
      'GET /csrf': () =>
        ++csrfCalls === 1
          ? json(200, { headerName: 'X-CSRF-TOKEN', token: 'token-1' })
          : problem(500, 'internal_error'),
      'GET /me': () => json(200, account),
      'PATCH /me/password': () => new Response(null, { status: 200 }),
    })
    render(
      <MemoryRouter initialEntries={['/password-change']}>
        <App />
      </MemoryRouter>,
    )

    await fillAndSubmit()

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Your password has been changed. Please log in again.')
    expect(csrfCalls).toBe(2)
  })

  it('keeps the new password but clears the current one after a refusal', async () => {
    renderApp(() => problem(400, 'password_history'))

    await fillAndSubmit('Synthetic-Pass-42', 'Synthetic-Pass-42')

    await screen.findByRole('alert')
    expect(screen.getByLabelText('Current password')).toHaveValue('')
    expect(screen.getByLabelText('New password')).toHaveValue('Synthetic-Pass-42')
  })

  it('shows a wrong current password', async () => {
    renderApp(() => problem(400, 'current_password_invalid'))

    await fillAndSubmit('Wrong-Pass-4242')

    expect(await screen.findByRole('alert')).toHaveTextContent('The current password is incorrect.')
    expect(screen.getByLabelText('Current password')).toHaveValue('')
    expect(screen.getByRole('heading', { name: 'Change password' })).toBeInTheDocument()
  })

  it('shows every broken password rule under the new password', async () => {
    renderApp(() =>
      json(
        400,
        { status: 400, code: 'password_policy', violations: ['min_length', 'uppercase', 'common_password'] },
        'application/problem+json',
      ),
    )

    await fillAndSubmit('Synthetic-Pass-42', 'weak')

    expect(await screen.findByRole('alert')).toHaveTextContent('The new password does not meet the password policy.')
    const field = screen.getByLabelText('New password').closest('.field') as HTMLElement
    expect(within(field).getByText('Use at least 12 characters.')).toBeInTheDocument()
    expect(within(field).getByText('Include an uppercase letter.')).toBeInTheDocument()
    expect(within(field).getByText('Choose a less common password.')).toBeInTheDocument()
    expect(screen.getByLabelText('New password')).toHaveAccessibleDescription(/Use at least 12 characters/)
  })

  it('shows a recently used password', async () => {
    renderApp(() => problem(400, 'password_history'))

    await fillAndSubmit('Synthetic-Pass-42', 'Synthetic-Pass-42')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The new password was used recently. Choose a password other than your last 3.',
    )
  })

  it('asks for both passwords when one is missing', async () => {
    renderApp(() => json(400, { status: 400, code: 'validation', fields: ['newPassword'] }, 'application/problem+json'))

    await fillAndSubmit('Synthetic-Pass-42', '')

    expect(await screen.findByRole('alert')).toHaveTextContent('Enter your current password and a new password.')
  })

  it('shows a generic error for anything else', async () => {
    renderApp(() => problem(500, 'internal_error'))

    await fillAndSubmit()

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong. Please try again later.')
  })

  it('shows a generic error when the API cannot be reached', async () => {
    fakeApi({
      'GET /csrf': rotatingCsrf(),
      'GET /me': () => json(200, account),
      'PATCH /me/password': () => Promise.reject(new TypeError('Failed to fetch')),
    })
    render(
      <MemoryRouter initialEntries={['/password-change']}>
        <App />
      </MemoryRouter>,
    )

    await fillAndSubmit()

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong. Please try again later.')
  })

  it('sends a Visitor to the login page', async () => {
    fakeApi({ 'GET /csrf': rotatingCsrf(), 'GET /me': () => problem(401, 'authentication_required') })
    render(
      <MemoryRouter initialEntries={['/password-change']}>
        <App />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
  })
})
