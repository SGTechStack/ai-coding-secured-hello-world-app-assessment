import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearCsrfToken, setUnauthorizedHandler } from '../shared/api/http.ts'
import {
  type ApiRoutes,
  emptyResponse,
  jsonResponse,
  stubApi,
  textResponse,
} from '../test/fakeApi.ts'
import { wireUnauthorizedRedirect } from './router.ts'
import { routes } from './routes.tsx'

const CURRENT_USER = { username: 'johndoe', role: 'USER' }
const GREETING = 'Hello, johndoe'
const UNAVAILABLE_MESSAGE = 'Unable to connect to the server. Please try again later.'

/** A signed-in johndoe, as seen by the protected and landing loaders. */
const SIGNED_IN: ApiRoutes = {
  'GET /auth/me': () => jsonResponse(CURRENT_USER),
  'GET /hello': () => textResponse(GREETING),
}

const ANONYMOUS: ApiRoutes = {
  'GET /auth/me': () => jsonResponse({}, 401),
  'GET /hello': () => jsonResponse({}, 401),
}

function loginPageApi(loginResponse: () => Response | Promise<Response>) {
  return stubApi({ ...ANONYMOUS, 'POST /auth/login': loginResponse })
}

/** An API where a login succeeds and later loader calls see the new session. */
function signInApi() {
  let authenticated = false
  return stubApi({
    'GET /auth/me': () => (authenticated ? jsonResponse(CURRENT_USER) : jsonResponse({}, 401)),
    'GET /hello': () => (authenticated ? textResponse(GREETING) : jsonResponse({}, 401)),
    'POST /auth/login': () => {
      authenticated = true
      return jsonResponse(CURRENT_USER)
    },
  })
}

function renderAppAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  wireUnauthorizedRedirect(router)
  const { unmount } = render(<RouterProvider router={router} />)
  return { router, unmount }
}

function renderAt(path: string) {
  return renderAppAt(path).router
}

async function fillLoginForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(await screen.findByLabelText('Username'), 'johndoe')
  await user.type(screen.getByLabelText('Password'), 'Password123!')
}

beforeEach(() => {
  clearCsrfToken()
})

afterEach(() => {
  setUnauthorizedHandler(undefined)
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('routes', () => {
  it('[assessment/story2-ac7] signs in from the login form and redirects to the greeting', async () => {
    const api = signInApi()
    const user = userEvent.setup()
    const router = renderAt('/login')

    await fillLoginForm(user)
    await user.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('heading', { name: GREETING })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
    expect(api.calls('POST /auth/login')[0].body).toEqual({
      username: 'johndoe',
      password: 'Password123!',
    })
  })

  it('[assessment/story2-ac8] blocks blank fields and links both inline errors without a login request', async () => {
    const api = loginPageApi(() => jsonResponse(CURRENT_USER))
    const user = userEvent.setup()
    renderAt('/login')

    const username = await screen.findByLabelText('Username')
    await user.type(username, '   ')
    await user.click(screen.getByRole('button', { name: 'Log in' }))

    const usernameError = screen.getByText('Username is required')
    const passwordError = screen.getByText('Password is required')
    expect(username).toHaveAttribute('aria-invalid', 'true')
    expect(username).toHaveAttribute('aria-describedby', usernameError.id)
    expect(screen.getByLabelText('Password')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Password')).toHaveAttribute('aria-describedby', passwordError.id)
    expect(api.calls('POST /auth/login')).toHaveLength(0)
  })

  it('[assessment/story2-ac9] does not validate while the user types before submitting', async () => {
    loginPageApi(() => jsonResponse(CURRENT_USER))
    const user = userEvent.setup()
    renderAt('/login')

    await user.type(await screen.findByLabelText('Username'), 'invalid!characters')

    expect(screen.queryByText('Username is required')).not.toBeInTheDocument()
    expect(screen.queryByText('Password is required')).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('[assessment/story2-ac10] clears only the edited field error immediately', async () => {
    loginPageApi(() => jsonResponse(CURRENT_USER))
    const user = userEvent.setup()
    renderAt('/login')

    await screen.findByLabelText('Username')
    await user.click(screen.getByRole('button', { name: 'Log in' }))
    await user.type(screen.getByLabelText('Username'), 'j')

    expect(screen.queryByText('Username is required')).not.toBeInTheDocument()
    expect(screen.getByText('Password is required')).toBeInTheDocument()
    expect(screen.getByLabelText('Username')).toHaveAttribute('aria-invalid', 'false')
    expect(screen.getByLabelText('Password')).toHaveAttribute('aria-invalid', 'true')
  })

  it('[assessment/story2-ac11] delays a fast successful redirect until 400ms', async () => {
    signInApi()
    const user = userEvent.setup()
    const router = renderAt('/login')
    await fillLoginForm(user)

    vi.useFakeTimers()
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(screen.getByLabelText('Username')).toBeDisabled()
    expect(screen.getByLabelText('Password')).toBeDisabled()
    const loadingButton = screen.getByRole('button', { name: 'Logging in...' })
    expect(loadingButton).toBeDisabled()
    // The "..." is its own element carrying the pulse animation (LoginPage.css `.login-ellipsis`).
    const ellipsis = loadingButton.querySelector('.login-ellipsis')
    expect(ellipsis).toHaveTextContent('...')

    await act(async () => {
      vi.advanceTimersByTime(399)
      await Promise.resolve()
    })
    expect(router.state.location.pathname).toBe('/login')
    expect(screen.getByRole('button', { name: 'Logging in...' })).toBeDisabled()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1)
    })
    vi.useRealTimers()
    expect(await screen.findByRole('heading', { name: GREETING })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })

  it('keeps the form disabled after 400ms while the login request is still in flight', async () => {
    let resolveRequest: ((response: Response) => void) | undefined
    loginPageApi(
      () =>
        new Promise<Response>((resolve) => {
          resolveRequest = resolve
        }),
    )
    const user = userEvent.setup()
    renderAt('/login')
    await fillLoginForm(user)

    vi.useFakeTimers()
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }))
    await act(async () => {
      vi.advanceTimersByTime(400)
      await Promise.resolve()
    })
    expect(screen.getByRole('button', { name: 'Logging in...' })).toBeDisabled()

    await act(async () => {
      resolveRequest?.(jsonResponse({}, 401))
      await Promise.resolve()
    })
    expect(screen.getByRole('button', { name: 'Log in' })).toBeEnabled()
  })

  it('[assessment/story2-ac12] shows one fixed credential banner for 401 and re-enables the form', async () => {
    loginPageApi(() => jsonResponse({ message: 'Username does not exist' }, 401))
    const user = userEvent.setup()
    renderAt('/login')

    await fillLoginForm(user)
    await user.click(screen.getByRole('button', { name: 'Log in' }))

    const banner = await screen.findByRole('alert')
    expect(banner).toHaveTextContent('Invalid username or password')
    expect(banner).not.toHaveTextContent('Username does not exist')
    expect(banner.nextElementSibling?.tagName).toBe('FORM')
    expect(screen.getByLabelText('Username')).toBeEnabled()
    expect(screen.getByLabelText('Password')).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Log in' })).toBeEnabled()
  })

  it('[assessment/story2-ac13] clears the credential banner when either field is edited', async () => {
    loginPageApi(() => jsonResponse({}, 401))
    const user = userEvent.setup()
    renderAt('/login')

    await fillLoginForm(user)
    await user.click(screen.getByRole('button', { name: 'Log in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password')

    await user.type(screen.getByLabelText('Username'), 'x')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('[assessment/story2-ac14] shows the unavailable banner for server and connection failures', async () => {
    let loginAttempts = 0
    loginPageApi(() => {
      loginAttempts += 1
      if (loginAttempts === 1) return jsonResponse({ message: 'Internal details' }, 500)
      throw new TypeError('connection refused')
    })
    const user = userEvent.setup()
    renderAt('/login')

    await fillLoginForm(user)
    await user.click(screen.getByRole('button', { name: 'Log in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(UNAVAILABLE_MESSAGE)
    expect(screen.getByRole('alert')).not.toHaveTextContent('Internal details')
    expect(screen.getByRole('button', { name: 'Log in' })).toBeEnabled()

    await user.type(screen.getByLabelText('Password'), 'x')
    await user.click(screen.getByRole('button', { name: 'Log in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(UNAVAILABLE_MESSAGE)
    expect(screen.getByRole('button', { name: 'Log in' })).toBeEnabled()
  })

  it('maps a server-side 400 validation response to the fixed credential banner', async () => {
    loginPageApi(() => jsonResponse({ message: 'server validation detail' }, 400))
    const user = userEvent.setup()
    renderAt('/login')

    await fillLoginForm(user)
    await user.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password')
    expect(screen.getByRole('alert')).not.toHaveTextContent('server validation detail')
  })

  it('maps 429 to the unavailable banner after the 400ms floor and re-enables the form', async () => {
    loginPageApi(() => jsonResponse({ message: 'Too many requests' }, 429))
    const user = userEvent.setup()
    renderAt('/login')
    await fillLoginForm(user)

    vi.useFakeTimers()
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }))
    await act(async () => {
      vi.advanceTimersByTime(399)
      await Promise.resolve()
    })
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Logging in...' })).toBeDisabled()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1)
    })
    expect(screen.getByRole('alert')).toHaveTextContent(UNAVAILABLE_MESSAGE)
    expect(screen.getByLabelText('Username')).toBeEnabled()
    expect(screen.getByLabelText('Password')).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Log in' })).toBeEnabled()
  })

  it('submits the login form when Enter is pressed in a field', async () => {
    signInApi()
    const user = userEvent.setup()
    renderAt('/login')

    await user.type(await screen.findByLabelText('Username'), 'johndoe')
    await user.type(screen.getByLabelText('Password'), 'Password123!{Enter}')

    expect(await screen.findByRole('heading', { name: GREETING })).toBeInTheDocument()
  })

  it('keeps the form usable after a 401 and fetches fresh CSRF for the next attempt', async () => {
    let csrfRequests = 0
    stubApi({
      ...ANONYMOUS,
      'GET /csrf': () => {
        csrfRequests += 1
        return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: `csrf-${csrfRequests}` })
      },
      'POST /auth/login': () => jsonResponse({}, 401),
    })
    const user = userEvent.setup()
    renderAt('/login')

    await fillLoginForm(user)
    await user.click(screen.getByRole('button', { name: 'Log in' }))
    await screen.findByRole('alert')
    await user.click(screen.getByRole('button', { name: 'Log in' }))

    await waitFor(() => expect(csrfRequests).toBe(2))
    expect(await screen.findByRole('button', { name: 'Log in' })).toBeEnabled()
    expect(screen.getByLabelText('Username')).toBeEnabled()
    expect(screen.getByLabelText('Password')).toBeEnabled()
  })

  it('[assessment/story5-ac3] shows the greeting returned by the API', async () => {
    const api = stubApi(SIGNED_IN)

    renderAt('/')

    expect(await screen.findByRole('heading', { name: 'Hello, johndoe' })).toBeInTheDocument()
    expect(api.calls('GET /hello')).toHaveLength(1)
  })

  it('shows whatever greeting text the API returns', async () => {
    stubApi({ ...SIGNED_IN, 'GET /hello': () => textResponse('Hello, someone-else') })

    renderAt('/')

    expect(await screen.findByRole('heading', { name: 'Hello, someone-else' })).toBeInTheDocument()
  })

  it('[assessment/story5-ac5] restores the authenticated view from the session cookie after a reload', async () => {
    const api = stubApi(SIGNED_IN)
    const { router: before, unmount } = renderAppAt('/')
    expect(await screen.findByRole('heading', { name: GREETING })).toBeInTheDocument()
    expect(before.state.location.pathname).toBe('/')

    // A reload throws away every in-memory state (app tree, router, cached CSRF token); only the
    // browser's session cookie survives, and the API sees it because every call sends credentials.
    unmount()
    clearCsrfToken()
    const callsBeforeReload = api.calls('GET /auth/me').length
    const visited: string[] = []
    const { router: after } = renderAppAt('/')
    after.subscribe((state) => visited.push(state.location.pathname))

    expect(await screen.findByRole('heading', { name: GREETING })).toBeInTheDocument()
    expect(after.state.location.pathname).toBe('/')
    expect(visited).not.toContain('/login')
    const reloadChecks = api.calls('GET /auth/me').slice(callsBeforeReload)
    expect(reloadChecks.length).toBeGreaterThan(0)
    expect(reloadChecks.every((request) => request.init?.credentials === 'include')).toBe(true)
  })

  it('[assessment/story5-ac4] redirects an anonymous visitor from the landing page to login', async () => {
    stubApi(ANONYMOUS)

    const router = renderAt('/')

    expect(await screen.findByRole('button', { name: 'Log in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(screen.queryByText(/Hello/)).not.toBeInTheDocument()
  })

  it('[assessment/story5-ac6] redirects an authenticated visitor from login to the landing page', async () => {
    const api = stubApi(SIGNED_IN)

    const router = renderAt('/login')

    expect(await screen.findByRole('heading', { name: GREETING })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
    // The guard asked the server about the session and never showed the login form.
    expect(api.calls('GET /auth/me').length).toBeGreaterThan(0)
    expect(screen.queryByRole('button', { name: 'Log in' })).not.toBeInTheDocument()
    expect(api.calls('POST /auth/login')).toHaveLength(0)
  })

  it('shows the error page when the greeting cannot be loaded', async () => {
    stubApi({ ...SIGNED_IN, 'GET /hello': () => emptyResponse(500) })

    renderAt('/')

    expect(await screen.findByRole('heading', { name: 'Something went wrong' })).toBeInTheDocument()
  })

  it('[assessment/story4-ac4] logs out from the landing page and cannot go back to it', async () => {
    let authenticated = true
    stubApi({
      'GET /auth/me': () => (authenticated ? jsonResponse(CURRENT_USER) : jsonResponse({}, 401)),
      'GET /hello': () => (authenticated ? textResponse(GREETING) : jsonResponse({}, 401)),
      'POST /auth/logout': () => {
        authenticated = false
        return emptyResponse(204)
      },
    })
    const user = userEvent.setup()
    const router = renderAt('/')

    await user.click(await screen.findByRole('button', { name: 'Log out' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))

    await act(() => router.navigate('/'))

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(await screen.findByRole('button', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: GREETING })).not.toBeInTheDocument()
  })

  it('logs out through the API and replace-navigates to login', async () => {
    let authenticated = true
    const api = stubApi({
      'GET /auth/me': () => (authenticated ? jsonResponse(CURRENT_USER) : jsonResponse({}, 401)),
      'GET /hello': () => (authenticated ? textResponse(GREETING) : jsonResponse({}, 401)),
      'GET /csrf': () => jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'logout-token' }),
      'POST /auth/logout': (request) => {
        expect(request.headers.get('X-CSRF-TOKEN')).toBe('logout-token')
        authenticated = false
        return emptyResponse(204)
      },
    })
    const user = userEvent.setup()
    const router = renderAt('/')

    await user.click(await screen.findByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('button', { name: 'Log in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.historyAction).toBe('REPLACE')
    expect(api.calls('POST /auth/logout')).toHaveLength(1)
  })

  it('centrally redirects a direct logout 401 without showing an error', async () => {
    let logoutAttempted = false
    let csrfRequests = 0
    stubApi({
      'GET /auth/me': () => (logoutAttempted ? jsonResponse({}, 401) : jsonResponse(CURRENT_USER)),
      'GET /hello': () => textResponse(GREETING),
      'GET /csrf': () => {
        csrfRequests += 1
        return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: `token-${csrfRequests}` })
      },
      'POST /auth/logout': () => {
        logoutAttempted = true
        return jsonResponse({}, 401)
      },
    })
    const user = userEvent.setup()
    const router = renderAt('/')

    await user.click(await screen.findByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('button', { name: 'Log in' })).toBeInTheDocument()
    expect(csrfRequests).toBe(1)
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.historyAction).toBe('REPLACE')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('quietly treats a final logout 403 as logged out', async () => {
    let logoutAttempts = 0
    stubApi({
      'GET /auth/me': () =>
        logoutAttempts === 0 ? jsonResponse(CURRENT_USER) : jsonResponse({}, 401),
      'GET /hello': () => textResponse(GREETING),
      'POST /auth/logout': () => {
        logoutAttempts += 1
        return jsonResponse({}, 403)
      },
    })
    const user = userEvent.setup()
    renderAt('/')

    await user.click(await screen.findByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('button', { name: 'Log in' })).toBeInTheDocument()
    expect(logoutAttempts).toBe(2)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('quietly redirects when an expired logout retries from 403 to 401', async () => {
    let logoutAttempts = 0
    let csrfRequests = 0
    stubApi({
      'GET /auth/me': () =>
        logoutAttempts === 0 ? jsonResponse(CURRENT_USER) : jsonResponse({}, 401),
      'GET /hello': () => textResponse(GREETING),
      'GET /csrf': () => {
        csrfRequests += 1
        return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: `token-${csrfRequests}` })
      },
      'POST /auth/logout': () => {
        logoutAttempts += 1
        return jsonResponse({}, logoutAttempts === 1 ? 403 : 401)
      },
    })
    const user = userEvent.setup()
    const router = renderAt('/')

    await user.click(await screen.findByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('button', { name: 'Log in' })).toBeInTheDocument()
    expect(csrfRequests).toBe(2)
    expect(logoutAttempts).toBe(2)
    expect(router.state.historyAction).toBe('REPLACE')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it.each([
    ['a server failure', async () => emptyResponse(500)],
    ['a network failure', async () => Promise.reject(new Error('network down'))],
  ])(
    'shows the fixed unavailable message after %s during logout',
    async (_label, logoutResponse) => {
      stubApi({ ...SIGNED_IN, 'POST /auth/logout': logoutResponse })
      const user = userEvent.setup()
      const router = renderAt('/')

      await user.click(await screen.findByRole('button', { name: 'Log out' }))

      expect(await screen.findByRole('alert')).toHaveTextContent(UNAVAILABLE_MESSAGE)
      expect(router.state.location.pathname).toBe('/')
    },
  )

  it('renders the generic not-found page for unknown paths', async () => {
    renderAt('/nope')

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })
})
