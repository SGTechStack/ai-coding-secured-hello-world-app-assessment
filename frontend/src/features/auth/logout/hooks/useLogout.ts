import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from '@tanstack/react-router';
import axios from 'axios';
import { startAuthenticatedCsrfSession } from '../../../../common/http/csrf';
import { logout } from '../api/logout-api';
import { endSession } from '../../session';

const httpStatus = (error: unknown) => (axios.isAxiosError(error) ? error.response?.status : undefined);

/** One Logout request: 204 once the server ended the Session, else the 401 or 403 it answered. Anything else throws. */
const attempt = () =>
  logout().then(
    () => 204,
    (error: unknown) => {
      const status = httpStatus(error);
      if (status === 401 || status === 403) return status;
      throw error;
    },
  );

/**
 * Logout mutation. The Session ends on the client once the server has ended it (204) or reports there was none (401).
 *
 * A 403 means the CSRF token was rejected: either the Session already expired (so it holds no token) or this tab's
 * token is stale. Logout alone drops the token, lets the transport fetch the current one, and retries once: an expired
 * Session then answers 401 and a live one ends for real. A 403 on the retry ends the Session on the client anyway.
 * Other mutating requests keep the global rule that an authenticated token is never refetched.
 *
 * Any other failure (5xx, network) is left as the mutation's error, and the User stays signed in.
 */
export function useLogout() {
  const queryClient = useQueryClient();
  const router = useRouter();
  return useMutation({
    mutationFn: async () => {
      if ((await attempt()) !== 403) return;
      startAuthenticatedCsrfSession(); // drops the token; the next request fetches the current Session's
      await attempt();
    },
    onSuccess: () => endSession(queryClient, router),
  });
}
