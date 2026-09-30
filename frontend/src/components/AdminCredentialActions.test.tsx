import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiUrl, clearCsrfToken } from '@/lib/api/client'
import type { AdminUser } from '@/lib/admin/users'
import type { Profile } from '@/lib/auth/session'
import { problemResponse } from '@/test/msw/problems'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/renderApp'

const TOKEN = 'rT0kEn_abcdefghijklmnopqrstuvwxyz0123456789'

const verifiedAdmin: Profile = {
  id: '00000000-0000-4000-8000-000000000001',
  username: 'alice-admin',
  role: 'ADMIN',
  passwordChangeRequired: false,
  factors: { held: true, required: true, enrolled: true, rebindRequired: false },
}

const alice: AdminUser = {
  id: verifiedAdmin.id,
  username: 'alice-admin',
  email: 'alice@example.test',
  role: 'ADMIN',
  enabled: true,
  createdAt: '2026-09-01T08:00:00Z',
}

const carol: AdminUser = {
  id: '00000000-0000-4000-8000-000000000003',
  username: 'carol',
  email: 'carol@example.test',
  role: 'USER',
  enabled: true,
  createdAt: '2026-09-03T10:00:00Z',
}

let unlocks: { id: string; body: unknown }[]
let resets: string[]

beforeEach(() => {
  clearCsrfToken()
  unlocks = []
  resets = []
  const stored: Record<string, AdminUser> = { [alice.id]: alice, [carol.id]: carol }
  server.use(
    http.get(apiUrl('/api/profile'), () => HttpResponse.json(verifiedAdmin)),
    http.get(apiUrl('/api/admin/users'), () => HttpResponse.json(Object.values(stored))),
    http.get(apiUrl('/api/admin/users/:id'), ({ params }) => HttpResponse.json(stored[String(params.id)])),
    http.post(apiUrl('/api/admin/users/:id/password-reset'), ({ params }) => {
      resets.push(String(params.id))
      return HttpResponse.json(
        { userId: String(params.id), token: TOKEN },
        { headers: { 'Cache-Control': 'no-store' } },
      )
    }),
    http.post(apiUrl('/api/admin/users/:id/unlock'), async ({ params, request }) => {
      unlocks.push({ id: String(params.id), body: await request.json() })
      return HttpResponse.json(stored[String(params.id)])
    }),
  )
})

describe('/admin/users/:id credential actions', () => {
  it('issues a reset link and shows it once with its token and a copy control, never logging it', async () => {
    const log = vi.spyOn(console, 'log')
    const error = vi.spyOn(console, 'error')
    renderApp(`/admin/users/${carol.id}`)
    const user = userEvent.setup()
    const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue()

    await user.click(await screen.findByRole('button', { name: 'Issue password reset link' }))

    expect(await screen.findByText(TOKEN)).toBeInTheDocument()
    const link = `${window.location.origin}/reset#token=${TOKEN}`
    expect(screen.getByText(link)).toBeInTheDocument()
    expect(screen.getByText(/shown once and will not be shown again/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Issue password reset link' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Copy link' }))
    expect(writeText).toHaveBeenCalledWith(link)
    expect(await screen.findByText('Link copied.')).toHaveAttribute('role', 'status')
    expect(resets).toEqual([carol.id])
    for (const call of [...log.mock.calls, ...error.mock.calls]) {
      expect(JSON.stringify(call)).not.toContain(TOKEN)
    }
  })

  it('offers the reset on the administrator’s own account, and no unlock', async () => {
    renderApp(`/admin/users/${alice.id}`)

    expect(await screen.findByRole('button', { name: 'Issue password reset link' })).toBeEnabled()
    expect(screen.getByText(/signs you out everywhere/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Unlock account' })).not.toBeInTheDocument()
  })

  it('a reset the server refuses for an unactivated or disabled account says why', async () => {
    server.use(
      http.post(apiUrl('/api/admin/users/:id/password-reset'), () =>
        problemResponse('VALIDATION_FAILED', `/api/admin/users/${carol.id}/password-reset`),
      ),
    )
    renderApp(`/admin/users/${carol.id}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Issue password reset link' }))

    expect(
      await screen.findByText('A reset link can only be issued for an activated, enabled account.'),
    ).toHaveAttribute('role', 'alert')
    expect(screen.queryByText(TOKEN)).not.toBeInTheDocument()
  })

  it('unlocks another account with the chosen reason', async () => {
    renderApp(`/admin/users/${carol.id}`)
    const user = userEvent.setup()

    await user.selectOptions(await screen.findByLabelText('Reason for unlocking'), 'FALSE_POSITIVE')
    await user.click(screen.getByRole('button', { name: 'Unlock account' }))

    expect(
      await screen.findByText('Account unlocked. Its password lockout and factor lock are cleared.'),
    ).toHaveAttribute('role', 'status')
    expect(unlocks).toEqual([{ id: carol.id, body: { reason: 'FALSE_POSITIVE' } }])
  })

  it('a factor too old for an unlock sends the session to the challenge', async () => {
    server.use(
      http.post(apiUrl('/api/admin/users/:id/unlock'), () =>
        problemResponse('MISSING_FACTOR', `/api/admin/users/${carol.id}/unlock`, { factor: 'TOTP', reason: 'EXPIRED' }),
      ),
    )
    const { router } = renderApp(`/admin/users/${carol.id}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Unlock account' }))

    expect(await screen.findByRole('heading', { name: 'TOTP Verification' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/verify')
  })
})
