import { apiFetch, apiJson } from './client';
import type { Role, UserResponse } from './types';

export async function listUsers(): Promise<UserResponse[]> {
  return apiJson<UserResponse[]>('/api/admin/users');
}

export async function setUserStatus(id: string, enabled: boolean): Promise<UserResponse> {
  return apiJson<UserResponse>(`/api/admin/users/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ enabled }),
  });
}

export async function changeUserRole(id: string, role: Role): Promise<UserResponse> {
  return apiJson<UserResponse>(`/api/admin/users/${id}/role`, {
    method: 'PATCH',
    body: JSON.stringify({ role }),
  });
}

export async function deleteUser(id: string): Promise<void> {
  await apiFetch(`/api/admin/users/${id}`, { method: 'DELETE' });
}
