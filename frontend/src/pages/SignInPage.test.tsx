import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { apiUrl, clearCsrfToken, CSRF_HEADER } from '@/lib/api/client'
import type { Profile } from '@/lib/auth/session'
import { problemResponse } from '@/test/msw/problems'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/renderApp'

const alice: Profile = {
  id: '6f1c2c1e-4f5a-4c1e-9f7e-2d1b3c4d5e6f',
  username: 'alice',
  role: 'USER',
  passwordChangeRequired: false,
  factors: { held: false, required: false, enrolled: false, rebindRequired: false },
}

beforeEach(() => clearCsrfToken())

async function fillAndSubmit(username: string, password: string) {
  const user = userEvent.setup()
  if (username) await user.type(screen.getByLabelText('Username'), username)
  if (password) await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
}

describe('SignInPage', () => {
  it('T-FE-005: every field has a label and its error is linked by aria-describedby', async () => {
    renderApp('/sign-in')

    await fillAndSubmit('', '')

    for (const [label, message] of [
      ['Username', 'Enter your username.'],
      ['Password', 'Enter your password.'],
    ]) {
      const field = screen.getByLabelText(label)
      expect(field).toHaveAttribute('aria-invalid', 'true')
      expect(field).toHaveAccessibleDescription(message)
    }
  })

  it('T-FE-006: a failed submit moves focus to the first invalid field', async () => {
    renderApp('/sign-in')

    await fillAndSubmit('', 'a-password')

    expect(screen.getByLabelText('Username')).toHaveFocus()
  })

  it('T-FE-008: a refused sign-in is announced through a live region', async () => {
    server.use(http.post(apiUrl('/api/login'), () => problemResponse('AUTHENTICATION_FAILED', '/api/login')))
    renderApp('/sign-in')

    await fillAndSubmit('alice', 'wrong-password')

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('The username or password is not correct.'))
    expect(screen.getByLabelText('Password')).toHaveValue('')
  })

  it('signs in, re-fetches the rotated CSRF token and shows the greeting', async () => {
    const tokens: string[] = []
    let bootstraps = 0
    server.use(
      http.get(apiUrl('/api/csrf'), () => {
        bootstraps += 1
        return HttpResponse.json({ headerName: CSRF_HEADER, token: 'token-' + String(bootstraps) })
      }),
      http.post(apiUrl('/api/login'), async ({ request }) => {
        tokens.push(request.headers.get(CSRF_HEADER) ?? '')
        expect(await request.json()).toEqual({ username: 'alice', password: 'correct-password' })
        return HttpResponse.json(alice)
      }),
      http.get(apiUrl('/api/hello'), () => HttpResponse.json({ message: 'Hello, alice' })),
    )
    const { router } = renderApp('/sign-in')

    await fillAndSubmit('alice', 'correct-password')

    expect(await screen.findByRole('heading', { name: 'Hello, alice' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/hello')
    expect(tokens).toEqual(['token-1'])
    expect(bootstraps).toBe(2)
  })
})

describe('Routing on the self-read', () => {
  it('sends an anonymous visitor to sign-in', async () => {
    const { router } = renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/sign-in')
  })

  it('sends a signed-in user to hello', async () => {
    server.use(
      http.get(apiUrl('/api/profile'), () => HttpResponse.json(alice)),
      http.get(apiUrl('/api/hello'), () => HttpResponse.json({ message: 'Hello, alice' })),
    )
    const { router } = renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Hello, alice' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/hello')
  })

  it('lets the envelope code override a belief that the session is live', async () => {
    server.use(http.get(apiUrl('/api/hello'), () => problemResponse('AUTHENTICATION_FAILED', '/api/hello')))
    const { router } = renderApp('/hello')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/sign-in')
  })

  it('reports an unavailable service instead of guessing', async () => {
    server.use(http.get(apiUrl('/api/profile'), () => problemResponse('INTERNAL_ERROR', '/api/profile')))
    renderApp('/')

    expect(await screen.findByRole('alert')).toHaveTextContent('The service is unavailable.')
  })
})

describe('The forced-change gate (ADR-046)', () => {
  const forced: Profile = { ...alice, passwordChangeRequired: true }

  it('sends a forced-change sign-in straight to the change-password page', async () => {
    server.use(
      http.post(apiUrl('/api/login'), () => HttpResponse.json(forced)),
      http.get(apiUrl('/api/profile'), () => HttpResponse.json(forced)),
    )
    const { router } = renderApp('/sign-in')

    await fillAndSubmit('alice', 'issued-password')

    expect(await screen.findByRole('heading', { name: 'Change password' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/change-password')
  })

  it('routes a forced-change session from the entry route to the change-password page', async () => {
    server.use(http.get(apiUrl('/api/profile'), () => HttpResponse.json(forced)))
    const { router } = renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Change password' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/change-password')
  })

  it('lets PASSWORD_CHANGE_REQUIRED override a belief that the app is open', async () => {
    server.use(
      http.get(apiUrl('/api/hello'), () => problemResponse('PASSWORD_CHANGE_REQUIRED', '/api/hello')),
      http.get(apiUrl('/api/profile'), () => HttpResponse.json(forced)),
    )
    const { router } = renderApp('/hello')

    expect(await screen.findByRole('heading', { name: 'Change password' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/change-password')
  })
})
