import type { ApiResult } from './client'
import { apiRequest } from './client'

/** One row of the admin Account list. Never carries credential material. */
export type AdminAccount = {
  id: string
  username: string
  email: string
  role: 'USER' | 'ADMIN'
  enabled: boolean
  locked: boolean
  createdAt: string
}

export type AccountListResult = { ok: true; accounts: AdminAccount[] } | { ok: false; reason: 'forbidden' | 'error' }

/** Every Account, for an Admin. A 401 is handled globally as an ended Session. */
export async function listAccounts(): Promise<AccountListResult> {
  const result = await apiRequest<AdminAccount[]>('/admin/users')
  if (result.ok) return { ok: true, accounts: result.data }
  return { ok: false, reason: result.status === 403 ? 'forbidden' : 'error' }
}

/**
 * The refusals an Admin can hit on an enable/disable or role-change action, on top of the generic
 * ones. `not_found` covers an Account another Admin already deleted from under this screen.
 */
export type AdminActionResult =
  { ok: true } | { ok: false; reason: 'self_action_forbidden' | 'last_admin' | 'not_found' | 'forbidden' | 'error' }

/** Enables or re-enables (`enabled: true`) or disables (`enabled: false`) an Account. */
export async function setEnabled(id: string, enabled: boolean): Promise<AdminActionResult> {
  return toActionResult(
    await apiRequest(`/admin/users/${id}/enabled`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled }),
    }),
  )
}

/** Changes an Account's role between User and Admin. */
export async function changeRole(id: string, role: 'USER' | 'ADMIN'): Promise<AdminActionResult> {
  return toActionResult(
    await apiRequest(`/admin/users/${id}/role`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ role }),
    }),
  )
}

function toActionResult(result: ApiResult<unknown>): AdminActionResult {
  if (result.ok) return { ok: true }
  const code = result.problem?.code
  if (result.status === 403 && code === 'self_action_forbidden') return { ok: false, reason: 'self_action_forbidden' }
  if (result.status === 409 && code === 'last_admin') return { ok: false, reason: 'last_admin' }
  if (result.status === 404) return { ok: false, reason: 'not_found' }
  if (result.status === 403) return { ok: false, reason: 'forbidden' }
  return { ok: false, reason: 'error' }
}
