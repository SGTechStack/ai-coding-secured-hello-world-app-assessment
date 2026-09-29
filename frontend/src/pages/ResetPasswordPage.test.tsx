import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { apiUrl, clearCsrfToken, CSRF_HEADER } from '@/lib/api/client'
import { RULE_COPY } from '@/lib/password/policy'
import { problemResponse } from '@/test/msw/problems'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/renderApp'
import { INVALID_RESET_LINK } from './ResetPasswordPage'

const TOKEN = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_0123'
const PASSWORD = 'copper lantern drifts westward'

interface Sent {
  body: unknown
  token: string | null
}

function serve(respond: () => Response) {
  const sent: Sent[] = []
  server.use(
    http.post(apiUrl('/api/password-reset/confirm'), async ({ request }) => {
      sent.push({ body: await request.json(), token: request.headers.get(CSRF_HEADER) })
      return respond()
    }),
  )
  return sent
}

async function submit(password: string) {
  const user = userEvent.setup()
  renderApp(`/reset#token=${TOKEN}`)
  await user.click(await screen.findByLabelText('Choose a password'))
  await user.paste(password)
  await user.click(screen.getByRole('button', { name: 'Reset password' }))
  return user
}

beforeEach(() => clearCsrfToken())

describe('reset password', () => {
  it('sends the token from the link fragment with the new password, then points to sign-in', async () => {
    const sent = serve(() => new HttpResponse(null, { status: 204 }))

    await submit(PASSWORD)

    expect(await screen.findByRole('status')).toHaveTextContent('Your password is set. You can now sign in.')
    expect(sent).toEqual([{ body: { token: TOKEN, password: PASSWORD }, token: 'test-csrf-token' }])
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/sign-in')
  })

  it('an invalid, used or expired link says so and offers a new one', async () => {
    serve(() => problemResponse('RESET_TOKEN_INVALID', '/api/password-reset/confirm'))

    await submit(PASSWORD)

    expect(await screen.findByRole('alert')).toHaveTextContent(INVALID_RESET_LINK)
  })

  it('a rejected password renders its rule on the field', async () => {
    serve(() => problemResponse('PASSWORD_REJECTED', '/api/password-reset/confirm', { rule: 'HISTORY_REUSE' }))

    await submit(PASSWORD)

    expect(await screen.findByText(RULE_COPY.HISTORY_REUSE)).toBeInTheDocument()
    expect(screen.getByLabelText('Choose a password')).toHaveFocus()
  })

  it('a link without a token offers a new link, with no form', async () => {
    renderApp('/reset')

    expect(await screen.findByRole('alert')).toHaveTextContent(INVALID_RESET_LINK)
    expect(screen.getByRole('link', { name: 'Request a new link' })).toHaveAttribute('href', '/forgot-password')
    expect(screen.queryByLabelText('Choose a password')).not.toBeInTheDocument()
  })

  it('R-FE-001: the password field carries the new-password autocomplete value', async () => {
    renderApp(`/reset#token=${TOKEN}`)

    expect(await screen.findByLabelText('Choose a password')).toHaveAttribute('autocomplete', 'new-password')
  })
})
