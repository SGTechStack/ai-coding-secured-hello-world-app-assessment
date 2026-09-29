import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { apiUrl, clearCsrfToken, CSRF_HEADER } from '@/lib/api/client'
import { problemResponse } from '@/test/msw/problems'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/renderApp'
import { EMAIL_COPY, TOO_MANY_COPY } from './ForgotPasswordPage'

interface Sent {
  body: unknown
  token: string | null
}

function serve(respond: () => Response) {
  const sent: Sent[] = []
  server.use(
    http.post(apiUrl('/api/password-reset/request'), async ({ request }) => {
      sent.push({ body: await request.json(), token: request.headers.get(CSRF_HEADER) })
      return respond()
    }),
  )
  return sent
}

async function submit(email: string) {
  const user = userEvent.setup()
  renderApp('/forgot-password')
  await user.type(await screen.findByLabelText('Email address'), email)
  await user.click(screen.getByRole('button', { name: 'Send reset link' }))
  return user
}

beforeEach(() => clearCsrfToken())

describe('forgot password', () => {
  it('sends the address with the CSRF token, and confirms without saying whether it has an account', async () => {
    const sent = serve(() => new HttpResponse(null, { status: 202 }))

    await submit('alice@example.com')

    expect(await screen.findByRole('status')).toHaveTextContent(
      'If alice@example.com belongs to an account, a reset link is on its way to it.',
    )
    expect(sent).toEqual([{ body: { email: 'alice@example.com' }, token: 'test-csrf-token' }])
  })

  it('a malformed address is refused client-side', async () => {
    const sent = serve(() => new HttpResponse(null, { status: 202 }))

    await submit('not an address')

    expect(await screen.findByText(EMAIL_COPY)).toBeInTheDocument()
    expect(sent).toHaveLength(0)
  })

  it('a server validation failure is reported on the address', async () => {
    serve(() => problemResponse('VALIDATION_FAILED', '/api/password-reset/request'))

    await submit('alice@example.com')

    expect(await screen.findByText(EMAIL_COPY)).toBeInTheDocument()
    expect(screen.getByLabelText('Email address')).toHaveAttribute('aria-invalid', 'true')
  })

  it('a spent budget says to wait', async () => {
    serve(() => problemResponse('TOO_MANY_REQUESTS', '/api/password-reset/request'))

    await submit('alice@example.com')

    expect(await screen.findByRole('alert')).toHaveTextContent(TOO_MANY_COPY)
  })

  it('is reachable from the sign-in page', async () => {
    const user = userEvent.setup()
    renderApp('/sign-in')

    await user.click(await screen.findByRole('link', { name: 'Forgot password?' }))

    expect(await screen.findByRole('heading', { name: 'Forgot your password?' })).toBeInTheDocument()
  })
})
