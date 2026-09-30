import { z } from 'zod'
import { apiFetch } from '@/lib/api/client'
import { type AdminUser, adminUserSchema } from '@/lib/admin/users'

/**
 * A single-use token the server returns once (ADR-006): an invite's activation token or an admin-issued reset token.
 * It is kept in component state only, shown once and never logged, cached or re-fetched (R-FE-007).
 */
const issuedTokenSchema = z.object({
  userId: z.uuid(),
  token: z.string().regex(/^[A-Za-z0-9_-]{43}$/),
})

export type IssuedToken = z.infer<typeof issuedTokenSchema>

/** The closed unlock reasons (REJ-028), in the order the form offers them, with their labels. */
export const UNLOCK_REASONS = {
  USER_REQUEST: 'The user asked to be let back in',
  FALSE_POSITIVE: 'The failures were not an attack',
  PASSWORD_RESET_COMPLETED: 'The user has completed a password reset',
  OTHER: 'Other',
} as const

export type UnlockReason = keyof typeof UNLOCK_REASONS

export type Role = AdminUser['role']

/** The link the user opens to redeem `token`: the SPA's activation or reset page, with the token in the fragment. */
export function oneTimeLink(kind: 'activation' | 'reset', token: string, origin = window.location.origin): string {
  return `${origin}/${kind === 'activation' ? 'activate' : 'reset'}#token=${token}`
}

/**
 * `POST /api/admin/users`: invites an account and returns its activation token once (ADR-006). A taken or tombstoned
 * identifier is `USER_EXISTS`. Needs a factor from the last 10 minutes (ADR-021).
 */
export async function inviteUser(username: string, email: string, role: Role): Promise<IssuedToken> {
  return issuedTokenSchema.parse(
    await apiFetch<unknown>('/api/admin/users', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, email, role }),
    }),
  )
}

/**
 * `POST /api/admin/users/{id}/password-reset`: returns a reset token once, for any activated, enabled account, the
 * admin's own included. It ends that account's sessions, so for their own account the admin is signed out next.
 */
export async function issuePasswordReset(id: string): Promise<IssuedToken> {
  return issuedTokenSchema.parse(
    await apiFetch<unknown>(`/api/admin/users/${encodeURIComponent(id)}/password-reset`, { method: 'POST' }),
  )
}

/** `POST /api/admin/users/{id}/unlock`: clears the password lockout and the tier-1 factor lock (REJ-072). */
export async function unlockUser(id: string, reason: UnlockReason): Promise<AdminUser> {
  return adminUserSchema.parse(
    await apiFetch<unknown>(`/api/admin/users/${encodeURIComponent(id)}/unlock`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason }),
    }),
  )
}
