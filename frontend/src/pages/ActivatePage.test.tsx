import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { apiUrl, clearCsrfToken, CSRF_HEADER } from '@/lib/api/client'
import { tokenFromFragment } from '@/lib/auth/registration'
import { PASSWORD_RULES, RULE_COPY } from '@/lib/password/policy'
import { problemResponse } from '@/test/msw/problems'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/renderApp'
import { INVALID_LINK } from './ActivatePage'

const TOKEN = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_0123'
const PASSWORD = 'velvet harbour quietly hums'

interface Sent {
  body: unknown
  token: string | null
}

function serve(respond: () => Response) {
  const sent: Sent[] = []
  server.use(
    http.post(apiUrl('/api/register/activate'), async ({ request }) => {
      sent.push({ body: await request.json(), token: request.headers.get(CSRF_HEADER) })
      return respond()
    }),
  )
  return sent
}

async function submit(password: string, path = `/activate#token=${TOKEN}`) {
  const user = userEvent.setup()
  renderApp(path)
  await user.click(await screen.findByLabelText('Choose a password'))
  await user.paste(password)
  await user.click(screen.getByRole('button', { name: 'Activate' }))
  return user
}

beforeEach(() => clearCsrfToken())

describe('activate', () => {
  it('sends the token from the link fragment with the chosen password, then points to sign-in', async () => {
    const sent = serve(() => new HttpResponse(null, { status: 204 }))

    await submit(PASSWORD)

    expect(await screen.findByRole('status')).toHaveTextContent('Your password is set. You can now sign in.')
    expect(sent).toEqual([{ body: { token: TOKEN, password: PASSWORD }, token: 'test-csrf-token' }])
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/sign-in')
  })

  it.each(PASSWORD_RULES)('T-FE-018: a %s rejection renders its own copy on the password', async (rule) => {
    serve(() => problemResponse('PASSWORD_REJECTED', '/api/register/activate', { rule }))

    await submit(PASSWORD)

    expect(await screen.findByText(RULE_COPY[rule])).toBeInTheDocument()
    expect(screen.getByLabelText('Choose a password')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Choose a password')).toHaveFocus()
  })

  it('an invalid, used or expired link says so and offers nothing about why', async () => {
    serve(() => problemResponse('RESET_TOKEN_INVALID', '/api/register/activate'))

    await submit(PASSWORD)

    expect(await screen.findByRole('alert')).toHaveTextContent(INVALID_LINK)
  })

  it('a short password is refused client-side with the MIN_LENGTH copy', async () => {
    const sent = serve(() => new HttpResponse(null, { status: 204 }))

    await submit('too short')

    expect(await screen.findByText(RULE_COPY.MIN_LENGTH)).toBeInTheDocument()
    expect(sent).toHaveLength(0)
  })

  it('a link without a token asks the user to register again, with no form', async () => {
    renderApp('/activate')

    expect(await screen.findByRole('alert')).toHaveTextContent(INVALID_LINK)
    expect(screen.queryByLabelText('Choose a password')).not.toBeInTheDocument()
  })

  it('shows the byte count and an indicative strength while typing', async () => {
    const user = userEvent.setup()
    renderApp(`/activate#token=${TOKEN}`)
    await user.click(await screen.findByLabelText('Choose a password'))
    await user.paste(PASSWORD)

    expect(screen.getByText(`${PASSWORD.length} of 72 bytes`)).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText(/^(Strong|Very strong)$/)).toBeInTheDocument(), { timeout: 5000 })
  })

  it('R-FE-001: the password field carries the new-password autocomplete value', async () => {
    renderApp(`/activate#token=${TOKEN}`)

    expect(await screen.findByLabelText('Choose a password')).toHaveAttribute('autocomplete', 'new-password')
  })
})

describe('tokenFromFragment', () => {
  it('reads the token from a link fragment and nothing else', () => {
    expect(tokenFromFragment(`#token=${TOKEN}`)).toBe(TOKEN)
    expect(tokenFromFragment('')).toBe('')
    expect(tokenFromFragment('#other=1')).toBe('')
  })
})
