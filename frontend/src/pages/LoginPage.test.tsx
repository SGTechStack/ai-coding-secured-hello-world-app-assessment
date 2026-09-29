import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { csrfResponse, json, mockApi, problem } from '../test/mockApi'
import { renderApp } from '../test/renderApp'

const notSignedIn = () => problem(401, 'UNAUTHENTICATED', 'Authentication is required.')

async function submitLogin(username: string, password: string) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Username'), username)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
}

describe('sign in', () => {
  it('sends anonymous visitors of protected pages to sign in', async () => {
    mockApi({ 'GET /api/me': notSignedIn })

    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('signs in and shows the personalised greeting', async () => {
    mockApi({
      'GET /api/me': notSignedIn,
      'GET /api/auth/csrf': csrfResponse,
      'POST /api/auth/login': () => json(200, { id: 'u1', username: 'alice', role: 'USER' }),
      'GET /api/hello': () => new Response('Hello, alice', { headers: { 'Content-Type': 'text/plain' } }),
    })
    renderApp('/')

    await submitLogin('alice', 'correct-password')

    expect(await screen.findByRole('heading', { name: 'Hello, alice' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Users' })).not.toBeInTheDocument()
  })

  it('shows the generic error and clears the password on failure', async () => {
    mockApi({
      'GET /api/me': notSignedIn,
      'GET /api/auth/csrf': csrfResponse,
      'POST /api/auth/login': () => problem(401, 'INVALID_CREDENTIALS', 'Invalid username or password.'),
    })
    renderApp('/login')

    await submitLogin('alice', 'wrong-password')

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password.')
    expect(screen.getByLabelText('Password')).toHaveValue('')
  })

  it('explains when the IP is throttled', async () => {
    mockApi({
      'GET /api/me': notSignedIn,
      'GET /api/auth/csrf': csrfResponse,
      'POST /api/auth/login': () =>
        json(429, { status: 429, code: 'TOO_MANY_REQUESTS', detail: 'Too many attempts.' }, { 'Retry-After': '600' }),
    })
    renderApp('/login')

    await submitLogin('alice', 'any-password')

    expect(await screen.findByRole('alert')).toHaveTextContent('Try again in 10 minutes.')
  })
})
