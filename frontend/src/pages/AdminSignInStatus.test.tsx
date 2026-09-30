import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { apiUrl, clearCsrfToken } from '@/lib/api/client'
import { isUnlockable, type SignInStatus, signInBadge, signInRestrictions } from '@/lib/admin/signInStatus'
import type { AdminUser } from '@/lib/admin/users'
import type { Profile } from '@/lib/auth/session'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/renderApp'

const verifiedAdmin: Profile = {
  id: '00000000-0000-4000-8000-000000000001',
  username: 'alice-admin',
  role: 'ADMIN',
  passwordChangeRequired: false,
  factors: { held: true, required: true, enrolled: true, rebindRequired: false },
}

const CLEAR: SignInStatus = {
  accountLockedUntil: null,
  failuresSinceSuccess: 0,
  lockedDevices: 0,
  nextDeviceUnlock: null,
  capDisabledAt: null,
  factorLockedUntil: null,
  factorDisabled: false,
}

const LOCKED: SignInStatus = {
  ...CLEAR,
  accountLockedUntil: '2026-09-30T13:20:00Z',
  failuresSinceSuccess: 7,
  lockedDevices: 2,
  nextDeviceUnlock: '2026-09-30T13:40:00Z',
}

const user = (id: string, username: string, signInStatus: SignInStatus): AdminUser => ({
  id,
  username,
  email: `${username}@example.test`,
  role: 'USER',
  enabled: true,
  activated: true,
  createdAt: '2026-09-03T10:00:00Z',
  signInStatus,
})

const dave = user('00000000-0000-4000-8000-000000000004', 'dave', LOCKED)
const erin = user('00000000-0000-4000-8000-000000000005', 'erin', CLEAR)
const frank = user('00000000-0000-4000-8000-000000000006', 'frank', {
  ...CLEAR,
  capDisabledAt: '2026-09-29T08:00:00Z',
  factorDisabled: true,
})

beforeEach(() => {
  clearCsrfToken()
  const stored: Record<string, AdminUser> = { [dave.id]: dave, [erin.id]: erin, [frank.id]: frank }
  server.use(
    http.get(apiUrl('/api/profile'), () => HttpResponse.json(verifiedAdmin)),
    http.get(apiUrl('/api/admin/users'), () => HttpResponse.json(Object.values(stored))),
    http.get(apiUrl('/api/admin/users/:id'), ({ params }) => HttpResponse.json(stored[String(params.id)])),
  )
})

describe('the admin sign-in status', () => {
  it('T-FE-029: the list shows a compact sign-in badge beside, and apart from, Enabled', async () => {
    renderApp('/admin/users')
    const row = (await screen.findByRole('link', { name: 'dave' })).closest('tr')!
    expect(within(row).getByText('Enabled')).toBeInTheDocument()
    expect(within(row).getByText('Locked')).toBeInTheDocument()
    const erinRow = screen.getByRole('link', { name: 'erin' }).closest('tr')!
    expect(within(erinRow).getByText('Can sign in')).toBeInTheDocument()
    const frankRow = screen.getByRole('link', { name: 'frank' }).closest('tr')!
    expect(within(frankRow).getByText('Disabled by failures')).toBeInTheDocument()
  })

  it('T-FE-029: the detail page shows every lock in full and offers unlock only when one is in force', async () => {
    renderApp(`/admin/users/${dave.id}`)
    expect(await screen.findByText('Locked for unrecognised devices until 2026-09-30 13:20 UTC')).toBeInTheDocument()
    expect(screen.getByText('2 trusted devices locked, the first until 2026-09-30 13:40 UTC')).toBeInTheDocument()
    expect(screen.getByText('Wrong passwords since the last sign-in: 7')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Unlock account' })).toBeInTheDocument()
  })

  it('T-FE-029: an account with nothing to unlock, or only rebinding-only disables, offers no unlock', async () => {
    renderApp(`/admin/users/${frank.id}`)
    expect(
      await screen.findByText(/Password disabled by the failure cap since 2026-09-29 08:00 UTC/),
    ).toBeInTheDocument()
    expect(screen.getByText(/Authenticator app disabled/)).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Issue password reset link' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Unlock account' })).not.toBeInTheDocument()
  })

  it('T-FE-029: unlockable means a lock an unlock clears; an unknown status keeps unlock offered', () => {
    expect(isUnlockable(CLEAR)).toBe(false)
    expect(isUnlockable({ ...CLEAR, factorLockedUntil: '2026-09-30T13:20:00Z' })).toBe(true)
    expect(isUnlockable({ ...CLEAR, lockedDevices: 1, nextDeviceUnlock: '2026-09-30T13:20:00Z' })).toBe(true)
    expect(isUnlockable({ ...CLEAR, capDisabledAt: '2026-09-30T13:20:00Z', factorDisabled: true })).toBe(false)
    expect(isUnlockable(undefined)).toBe(true)
    expect(signInRestrictions(CLEAR)).toEqual([])
    expect(signInBadge(undefined)).toBe('—')
  })
})
