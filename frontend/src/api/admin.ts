import { apiFetch } from './client';
import type { Role, UserSummary } from './types';

export const adminApi = {
  listUsers: () => apiFetch<UserSummary[]>('/api/admin/users'),

  setEnabled: (id: string, enabled: boolean) =>
    apiFetch<UserSummary>(`/api/admin/users/${encodeURIComponent(id)}/status`, {
      method: 'PATCH',
      body: { enabled },
    }),

  changeRole: (id: string, role: Role) =>
    apiFetch<UserSummary>(`/api/admin/users/${encodeURIComponent(id)}/role`, {
      method: 'PATCH',
      body: { role },
    }),

  deleteUser: (id: string) =>
    apiFetch<void>(`/api/admin/users/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
