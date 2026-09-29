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
