import { z } from 'zod'
import { apiFetch } from '@/lib/api/client'

/** One account as the admin surface shows it (PRD Story 8). Never a hash, token or factor field. */
const adminUserSchema = z.object({
  id: z.uuid(),
  username: z.string(),
  email: z.string(),
  role: z.enum(['USER', 'ADMIN']),
  enabled: z.boolean(),
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
