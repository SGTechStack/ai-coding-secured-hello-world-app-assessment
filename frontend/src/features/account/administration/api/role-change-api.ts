import { z } from 'zod';
import { apiClient } from '../../../../common/http/api-client';
import { listedAccountSchema, type ListedAccount } from '../model/user-list';

/**
 * Gives one Account another role: `PUT /api/admin/users/{id}/role` with `{ "role" }`. Returns the Account in the
 * User-list row shape, so the caller updates the row in place. Failures propagate raw for the caller to classify.
 */
export async function changeAccountRole(id: string, role: string): Promise<ListedAccount> {
  const response = await apiClient.put<ListedAccount>(`/api/admin/users/${id}/role`, { role });
  return listedAccountSchema.parse(response.data);
}

/** `GET /api/admin/roles`: the roles an Admin may give, in the server's order. */
export async function fetchRoles(): Promise<string[]> {
  const response = await apiClient.get<string[]>('/api/admin/roles');
  return z.array(z.string().min(1)).parse(response.data);
}
