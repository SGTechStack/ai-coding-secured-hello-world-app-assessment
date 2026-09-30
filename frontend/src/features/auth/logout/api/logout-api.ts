import { apiClient, WITHOUT_CSRF_RETRY } from '../../../../common/http/api-client';

/**
 * Ends the Session on the server: 204 when it did, 401 when there was none. The transport never retries a 403 here;
 * useLogout owns that retry.
 */
export async function logout(): Promise<void> {
  await apiClient.post('/api/auth/logout', undefined, WITHOUT_CSRF_RETRY);
}
