import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { apiUrl, clearCsrfToken, CSRF_HEADER } from '@/lib/api/client'
import { problemResponse } from '@/test/msw/problems'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/renderApp'
import { USERNAME_RULE_COPY, USERNAME_UNAVAILABLE_COPY } from './RegisterPage'

interface Sent {
  body: unknown
  token: string | null
}

function serve(respond: () => Response) {
  const sent: Sent[] = []
  server.use(
    http.post(apiUrl('/api/register'), async ({ request }) => {
      sent.push({ body: await request.json(), token: request.headers.get(CSRF_HEADER) })
      return respond()
    }),
  )
  return sent
}

async function submit(username: string, email: string) {
  const user = userEvent.setup()
  renderApp('/register')
  await user.type(await screen.findByLabelText('Username'), username)
  await user.type(screen.getByLabelText('Email address'), email)
  await user.click(screen.getByRole('button', { name: 'Register' }))
  return user
}

beforeEach(() => clearCsrfToken())

describe('register', () => {
  it('sends the username and email with the CSRF token, and confirms without saying whether the address is known', async () => {
    const sent = serve(() => new HttpResponse(null, { status: 202 }))

    await submit('alice', 'alice@example.com')

    expect(await screen.findByRole('status')).toHaveTextContent(
      'If alice@example.com can be registered, an activation link is on its way to it.',
    )
    expect(sent).toEqual([{ body: { username: 'alice', email: 'alice@example.com' }, token: 'test-csrf-token' }])
  })

  it('a taken username is reported on the username field', async () => {
    serve(() => problemResponse('VALIDATION_FAILED', '/api/register', { rule: 'USERNAME_UNAVAILABLE' }))

    await submit('alice', 'alice@example.com')

    expect(await screen.findByText(USERNAME_UNAVAILABLE_COPY)).toBeInTheDocument()
    expect(screen.getByLabelText('Username')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Username')).toHaveFocus()
  })

  it('any other validation failure is reported without naming a rule', async () => {
    serve(() => problemResponse('VALIDATION_FAILED', '/api/register'))

    await submit('alice', 'alice@example.com')

    expect(await screen.findByText(/was not accepted/)).toBeInTheDocument()
  })

  it.each(['Alice', 'al', 'bob@example.com', 'has space'])(
    'the username %s is refused before any request, never changed',
    async (username) => {
      const sent = serve(() => new HttpResponse(null, { status: 202 }))

      await submit(username, 'someone@example.com')

      expect(await screen.findByText(USERNAME_RULE_COPY)).toBeInTheDocument()
      expect(sent).toHaveLength(0)
    },
  )

  it('a malformed email address is refused before any request', async () => {
    const sent = serve(() => new HttpResponse(null, { status: 202 }))

    await submit('alice', 'not-an-address')

    expect(await screen.findByText(/Enter an email address/)).toBeInTheDocument()
    expect(sent).toHaveLength(0)
  })

  it('a spent budget is announced in the live region', async () => {
    serve(() => problemResponse('TOO_MANY_REQUESTS', '/api/register'))

    await submit('alice', 'alice@example.com')

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts.')
  })

  it('links to sign-in, and sign-in links here', async () => {
    const user = userEvent.setup()
    renderApp('/sign-in')
    await user.click(await screen.findByRole('link', { name: 'Register' }))

    expect(await screen.findByRole('heading', { name: 'Register' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Sign in instead' })).toHaveAttribute('href', '/sign-in')
  })
})
