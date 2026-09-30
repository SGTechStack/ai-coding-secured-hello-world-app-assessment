import { z } from 'zod'
import { apiFetch } from '@/lib/api/client'

/** One account as the admin surface shows it (PRD Story 8). Never a hash, token or factor field. */
export const adminUserSchema = z.object({
  id: z.uuid(),
  username: z.string(),
  email: z.string(),
  role: z.enum(['USER', 'ADMIN']),
  enabled: z.boolean(),
  activated: z.boolean(),
  createdAt: z.iso.datetime({ offset: true }),
})

export type AdminUser = z.infer<typeof adminUserSchema>

/** The query key of the user list; every single user's key extends it, so invalidating the list refreshes them too. */
export const ADMIN_USERS_KEY = ['admin', 'users'] as const

/** The query key of one user. */
export const adminUserKey = (id: string) => [...ADMIN_USERS_KEY, id] as const

/** `GET /api/admin/users`: needs the factor, of any age within the session (ADR-021). Checked at runtime. */
export async function fetchAdminUsers(): Promise<AdminUser[]> {
  return z.array(adminUserSchema).parse(await apiFetch<unknown>('/api/admin/users'))
}

/** `GET /api/admin/users/{id}`. Checked at runtime. */
export async function fetchAdminUser(id: string): Promise<AdminUser> {
  return adminUserSchema.parse(await apiFetch<unknown>(`/api/admin/users/${encodeURIComponent(id)}`))
}

/**
 * `PUT /api/admin/users/{id}/enabled`: needs a factor from the last 10 minutes (ADR-021). Returns the account as it
 * now is, checked at runtime. A disable ends the user's sessions; a re-enable makes them change their password.
 */
export async function setAdminUserEnabled(id: string, enabled: boolean): Promise<AdminUser> {
  return adminUserSchema.parse(
    await apiFetch<unknown>(`/api/admin/users/${encodeURIComponent(id)}/enabled`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled }),
    }),
  )
}

/** The two roles an account can hold (ADR-042). */
export type Role = AdminUser['role']

/**
 * `PUT /api/admin/users/{id}/role`: needs a factor from the last 10 minutes (ADR-021). Returns the account as it now
 * is, checked at runtime. A change ends the user's sessions.
 */
export async function setAdminUserRole(id: string, role: Role): Promise<AdminUser> {
  return adminUserSchema.parse(
    await apiFetch<unknown>(`/api/admin/users/${encodeURIComponent(id)}/role`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ role }),
    }),
  )
}

/**
 * `DELETE /api/admin/users/{id}`: needs a factor from the last 10 minutes (ADR-021). Deletes the account, ends its
 * sessions and leaves a tombstone that keeps its username and email from being used again (ADR-044).
 */
export async function deleteAdminUser(id: string): Promise<void> {
  await apiFetch<unknown>(`/api/admin/users/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

/**
 * `DELETE /api/admin/users/{id}/totp`: resets another administrator's authenticator app (ADR-049). Needs a factor from
 * the last 10 minutes. Ends the user's sessions; they set up the app again at their next sign-in. No body.
 */
export async function resetAdminUserFactor(id: string): Promise<void> {
  await apiFetch<unknown>(`/api/admin/users/${encodeURIComponent(id)}/totp`, { method: 'DELETE' })
}
