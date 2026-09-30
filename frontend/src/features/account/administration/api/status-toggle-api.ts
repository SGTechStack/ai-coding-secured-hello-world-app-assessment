import { apiClient } from '../../../../common/http/api-client';
import { listedAccountSchema, type ListedAccount } from '../model/user-list';

/**
 * Sets one Account's enabled state: `PATCH /api/admin/users/{id}` with `{ "enabled" }`. Returns the updated Account in
 * the User-list row shape, so the caller updates the row in place. Failures propagate raw for the caller to classify.
 */
export async function setAccountEnabled(id: string, enabled: boolean): Promise<ListedAccount> {
  const response = await apiClient.patch<ListedAccount>(`/api/admin/users/${id}`, { enabled });
  return listedAccountSchema.parse(response.data);
}
