import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { csrfRoute, fakeApi, json, problem, type Route } from '../test/fakeApi'

let App: typeof import('../App').default
beforeEach(async () => {
  vi.resetModules()
  App = (await import('../App')).default
})

function renderRegister(registerRoute: Route) {
  const fetch = fakeApi({ 'GET /csrf': csrfRoute(), 'POST /register': registerRoute })
  render(
    <MemoryRouter initialEntries={['/register']}>
      <App />
    </MemoryRouter>,
  )
  return fetch
}

function fillAndSubmit(
  username = 'testuser123',
  email = 'testuser123@test.example.com',
  password = 'Synthetic-Pass-42',
) {
  fireEvent.change(screen.getByLabelText('Username'), { target: { value: username } })
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } })
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } })
  fireEvent.click(screen.getByRole('button', { name: 'Register' }))
}

describe('Register screen', () => {
  it('shows a Confidential label next to every input field', () => {
    renderRegister(() => new Response(null, { status: 201 }))

    for (const label of ['Username', 'Email', 'Password']) {
      const input = screen.getByLabelText(label)
      const field = input.closest('.field') as HTMLElement
      expect(within(field).getByText('Confidential')).toBeInTheDocument()
      expect(input).toHaveAccessibleDescription('Confidential')
    }
  })

  it('posts the form as JSON with the CSRF token and goes to the login page on success', async () => {
    const fetch = renderRegister(() => new Response(null, { status: 201 }))

    fillAndSubmit()

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Your Account has been created')
    const [, init] = fetch.mock.calls.find(([url]) => String(url).endsWith('/register'))!
    const headers = new Headers(init?.headers)
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(headers.get('X-CSRF-TOKEN')).toBe('token-1')
    expect(init?.credentials).toBe('include')
    expect(JSON.parse(String(init?.body))).toEqual({
      username: 'testuser123',
      email: 'testuser123@test.example.com',
      password: 'Synthetic-Pass-42',
    })
  })

  it('shows every broken password rule', async () => {
    renderRegister(() =>
      json(
        400,
        {
          status: 400,
          code: 'password_policy',
          violations: [
            'min_length',
            'uppercase',
            'digit',
            'special',
            'common_password',
            'max_length',
            'max_bytes',
            'lowercase',
            'future_rule',
            'another_future_rule',
          ],
        },
        'application/problem+json',
      ),
    )

    const consoleError = vi.spyOn(console, 'error')
    fillAndSubmit()

    const password = screen.getByLabelText('Password')
    expect(await screen.findByText('Use at least 12 characters.')).toBeInTheDocument()
    for (const message of [
      'Include an uppercase letter.',
      'Include a lowercase letter.',
      'Include a digit.',
      'Use at most 64 characters.',
      'Choose a less common password.',
    ]) {
      expect(screen.getByText(message)).toBeInTheDocument()
    }
    // Unknown rules share a message but still get distinct React keys.
    expect(screen.getAllByText('The password is not allowed.')).toHaveLength(2)
    expect(consoleError).not.toHaveBeenCalled()
    expect(screen.getByText(/at most 72 bytes/)).toBeInTheDocument()
    expect(screen.getByText(/Include a special character/)).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('The password does not meet the password policy.')
    expect(password).toHaveAttribute('aria-invalid', 'true')
    expect(password).toHaveAccessibleDescription(/Confidential.*Use at least 12 characters/)
  })

  it('marks each field that failed input validation', async () => {
    renderRegister(() =>
      json(
        400,
        { status: 400, code: 'validation', fields: ['email', 'password', 'username', 'unknown'] },
        'application/problem+json',
      ),
    )

    fillAndSubmit('x', 'not-an-email', '')

    expect(await screen.findByText('Use 3 to 32 letters and digits.')).toBeInTheDocument()
    expect(screen.getByText('Enter a valid email address of at most 254 characters.')).toBeInTheDocument()
    expect(screen.getByText('Enter a password.')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Please correct the highlighted fields.')
    expect(screen.getByLabelText('Username')).toHaveAttribute('aria-invalid', 'true')
  })

  it.each([
    [problem(400, 'user_exist'), 'An Account with that username or email already exists.'],
    [problem(429, 'too_many_requests'), 'Too many attempts. Please try again later.'],
    [problem(500, 'internal_error'), 'Something went wrong. Please try again later.'],
    [
      json(400, { status: 400, code: 'password_policy' }, 'application/problem+json'),
      'The password does not meet the password policy.',
    ],
  ])('shows a message for other errors (%#)', async (response, message) => {
    renderRegister(() => response)

    fillAndSubmit()

    const button = screen.getByRole('button', { name: 'Register' })
    await vi.waitFor(() => expect(button).toBeEnabled())
    expect(screen.getByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('heading', { name: 'Register' })).toBeInTheDocument()
  })

  it('shows a generic error when the API cannot be reached', async () => {
    renderRegister(() => {
      throw new TypeError('Failed to fetch')
    })

    fillAndSubmit()

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
  })

  it('is reachable from the login page', async () => {
    fakeApi({ 'GET /csrf': csrfRoute(), 'GET /me': () => problem(401, 'authentication_required') })
    render(
      <MemoryRouter initialEntries={['/login']}>
        <App />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('link', { name: 'Register' }))

    expect(await screen.findByRole('heading', { name: 'Register' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: 'Log in' }))
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
