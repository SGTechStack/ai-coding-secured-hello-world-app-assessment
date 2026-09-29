import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { apiUrl, clearCsrfToken, CSRF_HEADER } from '@/lib/api/client'
import { PASSWORD_RULES, RULE_COPY } from '@/lib/password/policy'
import { problemResponse } from '@/test/msw/problems'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/renderApp'

const CURRENT = 'fixture-password-correct-horse'
const NEXT = 'velvet harbour quietly hums'
const PROFILE = {
  id: '00000000-0000-4000-8000-000000000001',
  username: 'alice',
  role: 'USER',
  passwordChangeRequired: false,
  factors: { held: false, required: false, enrolled: false, rebindRequired: false },
}

interface Sent {
  body: unknown
  token: string | null
}

/** Serves the self-read and answers the change with `respond`; returns what the page sent and how often it fetched a token. */
function serve(respond: () => Response, profile: object = PROFILE) {
  const sent: Sent[] = []
  const tokens = { fetched: 0 }
  server.use(
    http.get(apiUrl('/api/profile'), () => HttpResponse.json(profile)),
    http.get(apiUrl('/api/csrf'), () => {
      tokens.fetched += 1
      return HttpResponse.json({ headerName: CSRF_HEADER, token: `token-${tokens.fetched}` })
    }),
    http.patch(apiUrl('/api/profile/password'), async ({ request }) => {
      sent.push({ body: await request.json(), token: request.headers.get(CSRF_HEADER) })
      return respond()
    }),
  )
  return { sent, tokens }
}

async function submit(current: string, next: string) {
  const user = userEvent.setup()
  renderApp('/change-password')
  await user.click(await screen.findByLabelText('Current password'))
  await user.paste(current)
  await user.click(screen.getByLabelText('New password'))
  await user.paste(next)
  await user.click(screen.getByRole('button', { name: 'Change password' }))
  return user
}

beforeEach(() => clearCsrfToken())

describe('change password', () => {
  it('sends both passwords, fetches the rotated CSRF token and confirms the change', async () => {
    const { sent, tokens } = serve(() => new HttpResponse(null, { status: 204 }))

    await submit(CURRENT, NEXT)

    expect(await screen.findByRole('status')).toHaveTextContent('Your password has been changed.')
    expect(sent).toEqual([{ body: { currentPassword: CURRENT, newPassword: NEXT }, token: 'token-1' }])
    // The server rotated the session and its token, so the page fetched a new one at once (ADR-038).
    expect(tokens.fetched).toBe(2)
    expect(screen.getByLabelText('New password')).toHaveValue('')
  })

  it.each(PASSWORD_RULES)('T-FE-018: a %s rejection renders its own copy on the new password', async (rule) => {
    serve(() => problemResponse('PASSWORD_REJECTED', '/api/profile/password', { rule }))

    await submit(CURRENT, NEXT)

    expect(await screen.findByText(RULE_COPY[rule])).toBeInTheDocument()
    expect(screen.getByLabelText('New password')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('New password')).toHaveFocus()
  })

  it('T-FE-018: every rule has distinct copy', () => {
    expect(new Set(PASSWORD_RULES.map((rule) => RULE_COPY[rule])).size).toBe(PASSWORD_RULES.length)
  })

  it('T-FE-019: a 30-character multibyte password over 72 bytes is refused before any request', async () => {
    const { sent } = serve(() => new HttpResponse(null, { status: 204 }))
    const multibyte = '日'.repeat(30)

    await submit(CURRENT, multibyte)

    expect(await screen.findByText(RULE_COPY.MAX_BYTES)).toBeInTheDocument()
    expect(screen.getByText('90 of 72 bytes')).toBeInTheDocument()
    expect(sent).toHaveLength(0)
  })

  it('counts bytes after NFC, as the server does', async () => {
    serve(() => new HttpResponse(null, { status: 204 }))
    const user = userEvent.setup()
    renderApp('/change-password')
    await user.click(await screen.findByLabelText('New password'))
    // "e" plus a combining acute is three bytes; composed to "é" it is two.
    await user.paste('café')

    expect(screen.getByText('5 of 72 bytes')).toBeInTheDocument()
  })

  it('a short new password is refused client-side with the MIN_LENGTH copy', async () => {
    const { sent } = serve(() => new HttpResponse(null, { status: 204 }))

    await submit(CURRENT, 'too short')

    expect(await screen.findByText(RULE_COPY.MIN_LENGTH)).toBeInTheDocument()
    expect(sent).toHaveLength(0)
  })

  it('a wrong current password is reported on the current-password field', async () => {
    serve(() => problemResponse('VALIDATION_FAILED', '/api/profile/password'))

    await submit('not the current password', NEXT)

    expect(await screen.findByText('The current password is not correct.')).toBeInTheDocument()
    expect(screen.getByLabelText('Current password')).toHaveFocus()
  })

  it('a spent budget is announced in the live region', async () => {
    serve(() => problemResponse('TOO_MANY_REQUESTS', '/api/profile/password'))

    await submit(CURRENT, NEXT)

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts.')
  })

  it('R-FE-001: both fields take pasted input and carry the password-manager autocomplete values', async () => {
    serve(() => new HttpResponse(null, { status: 204 }))
    renderApp('/change-password')

    expect(await screen.findByLabelText('Current password')).toHaveAttribute('autocomplete', 'current-password')
    expect(screen.getByLabelText('New password')).toHaveAttribute('autocomplete', 'new-password')
  })

  it('shows an indicative strength while typing', async () => {
    serve(() => new HttpResponse(null, { status: 204 }))
    const user = userEvent.setup()
    renderApp('/change-password')
    await user.click(await screen.findByLabelText('New password'))
    await user.paste('aaaaaaaaaaaaaaaa')
    await waitFor(() => expect(screen.getByText('Very weak')).toBeInTheDocument(), { timeout: 5000 })

    await user.clear(screen.getByLabelText('New password'))
    await user.paste(NEXT)
    await waitFor(() => expect(screen.getByText(/^(Strong|Very strong)$/)).toBeInTheDocument(), { timeout: 5000 })
  })

  it('an expired session routes to sign-in', async () => {
    renderApp('/change-password')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })
})

describe('forced change: the first gate (ADR-046)', () => {
  const FORCED = { ...PROFILE, passwordChangeRequired: true }

  it('explains the obligation and offers sign-out instead of a way back', async () => {
    serve(() => new HttpResponse(null, { status: 204 }), FORCED)
    renderApp('/change-password')

    expect(await screen.findByText(/You must choose a new password before you can continue/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Back' })).not.toBeInTheDocument()
  })

  it('moves on to the app once the change succeeds', async () => {
    serve(() => new HttpResponse(null, { status: 204 }), FORCED)
    server.use(http.get(apiUrl('/api/hello'), () => HttpResponse.json({ message: 'Hello, alice' })))

    await submit(CURRENT, NEXT)

    expect(await screen.findByRole('heading', { name: 'Hello, alice' })).toBeInTheDocument()
  })

  it('signs out from the change page', async () => {
    serve(() => new HttpResponse(null, { status: 204 }), FORCED)
    server.use(http.post(apiUrl('/api/logout'), () => new HttpResponse(null, { status: 204 })))
    const user = userEvent.setup()
    const { router } = renderApp('/change-password')

    await user.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/sign-in')
  })
})
