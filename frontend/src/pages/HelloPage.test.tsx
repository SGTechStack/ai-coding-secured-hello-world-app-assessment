import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { apiUrl, clearCsrfToken, CSRF_HEADER } from '@/lib/api/client'
import type { ErrorCode } from '@/lib/api/errors'
import { PROFILE_KEY } from '@/lib/auth/session'
import { problemResponse } from '@/test/msw/problems'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/renderApp'

beforeEach(() => clearCsrfToken())

interface Outcome {
  name: string
  respond: () => Response
}

const refusals: ErrorCode[] = ['AUTHENTICATION_FAILED', 'CSRF_TOKEN_INVALID', 'ACCESS_DENIED']
const outcomes: Outcome[] = [
  { name: '204', respond: () => new HttpResponse(null, { status: 204 }) },
  ...refusals.map((code) => ({ name: code, respond: () => problemResponse(code, '/api/logout') })),
  { name: 'a network failure', respond: () => HttpResponse.error() },
]

describe('T-FE-017: sign-out is terminal', () => {
  it.each(outcomes)(
    'T-FE-017 on $name: local state cleared, routed to sign-in, never retried or re-bootstrapped',
    async ({ respond }) => {
      let logouts = 0
      let bootstraps = 0
      server.use(
        http.get(apiUrl('/api/hello'), () => HttpResponse.json({ message: 'Hello, alice' })),
        http.get(apiUrl('/api/csrf'), () => {
          bootstraps += 1
          return HttpResponse.json({ headerName: CSRF_HEADER, token: 'token' })
        }),
        http.post(apiUrl('/api/logout'), () => {
          logouts += 1
          return respond()
        }),
      )
      const { queryClient, router } = renderApp('/hello')
      queryClient.setQueryData(PROFILE_KEY, { username: 'alice' })
      await screen.findByRole('heading', { name: 'Hello, alice' })

      await userEvent.setup().click(screen.getByRole('button', { name: 'Sign out' }))

      expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
      expect(router.state.location.pathname).toBe('/sign-in')
      expect(logouts).toBe(1)
      expect(bootstraps).toBe(1)
      await waitFor(() => expect(queryClient.getQueryData(PROFILE_KEY)).toBeUndefined())
      expect(queryClient.getQueryData(['hello'])).toBeUndefined()
    },
  )

  it('shows an alert when the greeting cannot be loaded', async () => {
    server.use(http.get(apiUrl('/api/hello'), () => problemResponse('INTERNAL_ERROR', '/api/hello')))
    renderApp('/hello')

    expect(await screen.findByRole('alert')).toHaveTextContent('The greeting could not be loaded.')
  })
})
