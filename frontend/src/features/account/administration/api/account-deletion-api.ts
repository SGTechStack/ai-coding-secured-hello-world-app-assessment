import { apiClient } from '../../../../common/http/api-client';

/** Deletes one Account: `DELETE /api/admin/users/{id}`, answered 204 with no body. Failures propagate raw. */
export async function deleteAccount(id: string): Promise<void> {
  await apiClient.delete(`/api/admin/users/${id}`);
}
