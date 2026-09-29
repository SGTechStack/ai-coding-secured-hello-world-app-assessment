import { SERVER_UNAVAILABLE, apiFetch, errorMessage } from '../../shared/api/http.ts'
import type { Role } from '../../shared/api/role.ts'

export interface AdminUser {
  id: string
  username: string
  email: string
  role: Role
  enabled: boolean
  createdAt: string
}

export type MutationResult<T> = { status: 'ok'; value: T } | { status: 'error'; message: string }

export async function listUsers(): Promise<AdminUser[]> {
  const response = await apiFetch('/admin/users')
  if (!response.ok) throw new Error(`User list request failed with ${response.status}`)
  return (await response.json()) as AdminUser[]
}

async function mutate<T>(
  path: string,
  init: RequestInit,
  read: (response: Response) => Promise<T>,
): Promise<MutationResult<T>> {
  try {
    const response = await apiFetch(path, init)
    if (response.ok) return { status: 'ok', value: await read(response) }
    return { status: 'error', message: (await errorMessage(response)) ?? SERVER_UNAVAILABLE }
  } catch {
    return { status: 'error', message: SERVER_UNAVAILABLE }
  }
}

function patchJson(body: unknown): RequestInit {
  return {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }
}

const readUser = async (response: Response) => (await response.json()) as AdminUser

export function setUserEnabled(id: string, enabled: boolean) {
  return mutate(`/admin/users/${encodeURIComponent(id)}/status`, patchJson({ enabled }), readUser)
}

export function setUserRole(id: string, role: Role) {
  return mutate(`/admin/users/${encodeURIComponent(id)}/role`, patchJson({ role }), readUser)
}

export function deleteUser(id: string) {
  return mutate(`/admin/users/${encodeURIComponent(id)}`, { method: 'DELETE' }, async () => id)
}
