import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fakeApi, json, problem, type Route } from './test/fakeApi'

let App: typeof import('./App').default
beforeEach(async () => {
  vi.resetModules()
  App = (await import('./App')).default
})

const account = {
  id: '00000000-0000-0000-0000-000000000123',
  username: 'testuser123',
  email: 'testuser123@test.example.com',
  role: 'USER',
  passwordChangeRequired: false,
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}

const requestedPaths = (fetch: ReturnType<typeof fakeApi>) =>
  fetch.mock.calls.map(([url]) => new URL(String(url)).pathname)

function visit(meRoute: Route, helloRoute: Route = () => new Response('Hello, testuser123')) {
  const fetch = fakeApi({ 'GET /me': meRoute, 'GET /hello': helloRoute })
  renderAt('/')
  return fetch
}

describe('SPA start-up', () => {
  it('asks /me who is logged in and quietly shows the login page to a Visitor', async () => {
    const fetch = visit(() => problem(401, 'authentication_required'))

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    const paths = fetch.mock.calls.map(([url]) => new URL(String(url)).pathname)
    expect(paths).toEqual(['/api/me'])
  })

  it('shows the hello screen to a logged-in Account holder', async () => {
    visit(() => json(200, account))

    expect(await screen.findByRole('heading', { name: 'Hello, testuser123' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument()
  })

  it('renders the greeting as text, never as HTML', async () => {
    visit(
      () => json(200, account),
      () => new Response('<img src=x onerror=alert(1)>Hello', { headers: { 'Content-Type': 'text/plain' } }),
    )

    const heading = await screen.findByRole('heading', { name: /Hello/ })
    expect(heading).toHaveTextContent('<img src=x onerror=alert(1)>Hello')
    expect(heading.querySelector('img')).toBeNull()
  })

  it('shows a generic error when /me fails for another reason', async () => {
    visit(() => problem(500, 'internal_error'))

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
  })

  it('shows a generic error when /hello fails for another reason', async () => {
    visit(
      () => json(200, account),
      () => problem(500, 'internal_error'),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
  })

  it('goes to the login page when the Session ends while on the hello screen', async () => {
    visit(
      () => json(200, account),
      () => problem(401, 'authentication_required'),
    )

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows a generic error when the API cannot be reached', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'))
    renderAt('/')

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
  })

  it('shows a loading state until the API answers', () => {
    vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise(() => {}))
    renderAt('/')

    expect(screen.getByText('Loading…')).toBeInTheDocument()
  })

  it('lets a Visitor open the register screen directly', async () => {
    const fetch = fakeApi({ 'GET /me': () => problem(401, 'authentication_required') })
    renderAt('/register')

    await vi.waitFor(() => expect(fetch).toHaveBeenCalled())
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(screen.getByRole('heading', { name: 'Register' })).toBeInTheDocument()
  })

  it('takes an Account holder whose password must be changed straight to that screen', async () => {
    const fetch = fakeApi({ 'GET /me': () => json(200, { ...account, passwordChangeRequired: true }) })
    renderAt('/')

    expect(await screen.findByRole('heading', { name: 'Change password' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Back' })).not.toBeInTheDocument()
    expect(requestedPaths(fetch)).not.toContain('/api/hello')
  })

  it('keeps every other screen out of reach while a password change is required', async () => {
    fakeApi({ 'GET /me': () => json(200, { ...account, role: 'ADMIN', passwordChangeRequired: true }) })
    renderAt('/admin/users')

    expect(await screen.findByRole('heading', { name: 'Change password' })).toBeInTheDocument()
  })

  it.each(['/register', '/forgot-password', '/reset-password'])(
    'sends an Account holder whose password must be changed from %s to that screen',
    async (path) => {
      fakeApi({ 'GET /me': () => json(200, { ...account, passwordChangeRequired: true }) })
      renderAt(path)

      expect(await screen.findByRole('heading', { name: 'Change password' })).toBeInTheDocument()
    },
  )

  it('still lets a logged-out holder open the reset screen, which is how a reset clears the flag', async () => {
    fakeApi({ 'GET /me': () => problem(401, 'authentication_required') })
    renderAt('/reset-password?token=reset-token-1')

    expect(await screen.findByRole('heading', { name: 'Reset password' })).toBeInTheDocument()
  })

  it('switches to the Password Change screen when a request is refused until the password changes', async () => {
    visit(
      () => json(200, account),
      () => problem(403, 'password_change_required'),
    )

    expect(await screen.findByRole('heading', { name: 'Change password' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('sends unknown paths to the start page', async () => {
    fakeApi({ 'GET /me': () => problem(401, 'authentication_required') })
    renderAt('/no-such-page')

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
  })
})
