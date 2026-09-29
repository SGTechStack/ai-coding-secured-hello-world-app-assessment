import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '../../app/routes.tsx'
import { clearCsrfToken } from '../../shared/api/http.ts'
import { type ApiRoutes, jsonResponse, stubApi, textResponse } from '../../test/fakeApi.ts'

const ANONYMOUS: ApiRoutes = {
  'GET /auth/me': () => jsonResponse({}, 401),
  'GET /hello': () => jsonResponse({}, 401),
}

const CREATED = {
  id: '5b0c7c6e-0000-4000-8000-000000000001',
  username: 'janedoe',
  email: 'jane@example.com',
  role: 'USER',
  enabled: true,
  createdAt: '2026-09-28T08:00:00Z',
}

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(<RouterProvider router={router} />)
  return router
}

async function fillForm(
  user: ReturnType<typeof userEvent.setup>,
  username: string,
  email: string,
  password: string,
) {
  await user.type(await screen.findByLabelText('Username'), username)
  await user.type(screen.getByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Password'), password)
}

beforeEach(() => {
  clearCsrfToken()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('registration', () => {
  it('[assessment/story1-ac8] registers from the login page and returns to log in', async () => {
    const api = stubApi({ ...ANONYMOUS, 'POST /auth/register': () => jsonResponse(CREATED, 201) })
    const user = userEvent.setup()
    const router = renderAt('/login')

    await user.click(await screen.findByRole('link', { name: 'Create an account' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/register'))
    await fillForm(user, 'janedoe', 'jane@example.com', 'correct-horse-battery')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Account created. Please log in.')
    expect(router.state.location.pathname).toBe('/login')
    const [request] = api.calls('POST /auth/register')
    expect(request.body).toEqual({
      username: 'janedoe',
      email: 'jane@example.com',
      password: 'correct-horse-battery',
    })
    expect(request.headers.get('X-CSRF-TOKEN')).toBe('csrf-token')
    expect(screen.getByLabelText('Username')).toHaveAttribute('autocomplete', 'username')
  })

  it('[assessment/story1-ac9] shows a server error and re-enables the form', async () => {
    let respond: ((response: Response) => void) | undefined
    stubApi({
      ...ANONYMOUS,
      'POST /auth/register': () =>
        new Promise<Response>((resolve) => {
          respond = resolve
        }),
    })
    const user = userEvent.setup()
    const router = renderAt('/register')
    await fillForm(user, 'johndoe', 'new@example.com', 'correct-horse-battery')

    await user.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Create account' })).toBeDisabled(),
    )
    expect(screen.getByLabelText('Username')).toBeDisabled()
    expect(screen.getByLabelText('Email')).toBeDisabled()
    expect(screen.getByLabelText('Password')).toBeDisabled()

    // The server's messages are the only validation: the browser must not block malformed emails.
    expect(screen.getByRole('button', { name: 'Create account' }).closest('form')).toHaveAttribute(
      'novalidate',
    )
    respond?.(jsonResponse({ message: 'Username is already taken' }, 409))

    expect(await screen.findByRole('alert')).toHaveTextContent('Username is already taken')
    expect(router.state.location.pathname).toBe('/register')
    expect(screen.getByRole('button', { name: 'Create account' })).toBeEnabled()
    expect(screen.getByLabelText('Username')).toBeEnabled()
    expect(screen.getByLabelText('Email')).toBeEnabled()
    expect(screen.getByLabelText('Password')).toBeEnabled()
  })

  it('shows a validation message from a 400 response', async () => {
    stubApi({
      ...ANONYMOUS,
      'POST /auth/register': () =>
        jsonResponse({ message: 'Password must be at least 12 characters' }, 400),
    })
    const user = userEvent.setup()
    renderAt('/register')
    await fillForm(user, 'janedoe', 'jane@example.com', 'short')

    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Password must be at least 12 characters',
    )
  })

  it.each([
    ['a server failure', () => jsonResponse({ message: 'Internal details' }, 500)],
    ['a 400 without a message', () => jsonResponse({}, 400)],
    ['a network failure', () => Promise.reject(new TypeError('connection refused'))],
  ])('shows the unavailable message after %s', async (_label, response) => {
    stubApi({ ...ANONYMOUS, 'POST /auth/register': response })
    const user = userEvent.setup()
    renderAt('/register')
    await fillForm(user, 'janedoe', 'jane@example.com', 'correct-horse-battery')

    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Unable to connect to the server. Please try again later.',
    )
    expect(screen.queryByText('Internal details')).not.toBeInTheDocument()
  })

  it('redirects an authenticated user away from the registration page', async () => {
    stubApi({
      'GET /auth/me': () => jsonResponse({ username: 'johndoe', role: 'USER' }),
      'GET /hello': () => textResponse('Hello, johndoe'),
    })

    const router = renderAt('/register')

    expect(await screen.findByRole('heading', { name: 'Hello, johndoe' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })

  it('links back to the login page', async () => {
    stubApi(ANONYMOUS)
    const user = userEvent.setup()
    const router = renderAt('/register')

    await user.click(await screen.findByRole('link', { name: 'Back to log in' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
