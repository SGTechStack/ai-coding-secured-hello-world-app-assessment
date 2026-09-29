import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { csrfRoute, fakeApi, json, problem, type Route } from '../test/fakeApi'

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

const visitor = () => problem(401, 'authentication_required')

function renderApp(
  routes: Record<string, Route>,
  entry: string | { pathname: string; search?: string; state?: unknown } = '/login',
) {
  const fetch = fakeApi({
    'GET /csrf': csrfRoute(),
    'GET /me': visitor,
    'GET /hello': () => new Response('Hello, testuser123', { headers: { 'Content-Type': 'text/plain' } }),
    ...routes,
  })
  render(
    <MemoryRouter initialEntries={[entry]}>
      <App />
    </MemoryRouter>,
  )
  return fetch
}

async function fillAndSubmit(username = 'testuser123', password = 'Synthetic-Pass-42') {
  fireEvent.change(await screen.findByLabelText('Username'), { target: { value: username } })
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } })
  fireEvent.click(screen.getByRole('button', { name: 'Log in' }))
}

describe('Login screen', () => {
  it('shows a Confidential label next to every input field', async () => {
    renderApp({})

    for (const label of ['Username', 'Password']) {
      const input = await screen.findByLabelText(label)
      const field = input.closest('.field') as HTMLElement
      expect(within(field).getByText('Confidential')).toBeInTheDocument()
      expect(input).toHaveAccessibleDescription('Confidential')
    }
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password')
  })

  it('logs in with the CSRF token and lands on the hello screen', async () => {
    const fetch = renderApp({ 'POST /login': () => json(200, account) })

    await fillAndSubmit()

    expect(await screen.findByRole('heading', { name: 'Hello, testuser123' })).toBeInTheDocument()
    const [, init] = fetch.mock.calls.find(([url]) => String(url).endsWith('/login'))!
    expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('token-1')
    expect(JSON.parse(String(init?.body))).toEqual({ username: 'testuser123', password: 'Synthetic-Pass-42' })
    const csrfCalls = fetch.mock.calls.filter(([url]) => String(url).endsWith('/csrf'))
    expect(csrfCalls).toHaveLength(2)
  })

  it('always goes to the hello screen, never to a return URL', async () => {
    renderApp(
      { 'POST /login': () => json(200, account) },
      {
        pathname: '/login',
        search: '?returnUrl=https://evil.example/',
        state: { from: '/admin' },
      },
    )

    await fillAndSubmit()

    expect(await screen.findByRole('heading', { name: 'Hello, testuser123' })).toBeInTheDocument()
  })

  it.each([
    [problem(401, 'authentication_failed'), 'The username or password is incorrect.'],
    [problem(429, 'too_many_requests'), 'Too many attempts. Please try again later.'],
    [problem(500, 'internal_error'), 'Something went wrong. Please try again later.'],
  ])('shows a message when login fails (%#)', async (response, message) => {
    renderApp({ 'POST /login': () => response })

    await fillAndSubmit()

    const button = screen.getByRole('button', { name: 'Log in' })
    await vi.waitFor(() => expect(button).toBeEnabled())
    expect(screen.getByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument()
  })

  it('shows a generic error when the API cannot be reached', async () => {
    renderApp({
      'POST /login': () => {
        throw new TypeError('Failed to fetch')
      },
    })

    await fillAndSubmit()

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
  })

  it('sends a logged-in Account holder straight to the hello screen', async () => {
    renderApp({ 'GET /me': () => json(200, account) })

    expect(await screen.findByRole('heading', { name: 'Hello, testuser123' })).toBeInTheDocument()
  })
})

describe('Logout', () => {
  it.each([
    ['succeeds', () => new Response(null, { status: 200 })],
    ['finds the Session already ended (401)', () => problem(401, 'authentication_required')],
    ['finds the Session already ended (403)', () => problem(403, 'csrf_invalid')],
  ])('lands on the login page with a logged-out message when it %s', async (_, logoutRoute) => {
    const fetch = renderApp({ 'GET /me': () => json(200, account), 'POST /logout': logoutRoute }, '/')

    fireEvent.click(await screen.findByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(await screen.findByRole('status')).toHaveTextContent('You have logged out.')
    // A fresh token is fetched once logout is done.
    expect(String(fetch.mock.calls.at(-1)![0])).toMatch(/\/csrf$/)
  })

  it('stays on the hello screen with an error when logout fails', async () => {
    renderApp({ 'GET /me': () => json(200, account), 'POST /logout': () => problem(500, 'internal_error') }, '/')

    fireEvent.click(await screen.findByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
    expect(screen.getByRole('heading', { name: 'Hello, testuser123' })).toBeInTheDocument()
  })

  it('shows an error when the API cannot be reached during logout', async () => {
    renderApp(
      {
        'GET /me': () => json(200, account),
        'POST /logout': () => {
          throw new TypeError('Failed to fetch')
        },
      },
      '/',
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
  })
})
