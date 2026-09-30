import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from '@tanstack/react-router';
import { startAuthenticatedCsrfSession } from '../../../../common/http/csrf';
import { login } from '../api/login-api';
import { seedSession } from '../../session';

/**
 * Login mutation. The transport fetches the anonymous session's CSRF token for the login request itself. A successful
 * login replaces the session, so that token is dropped and the transport fetches the new session's token from
 * `GET /csrf` on the next mutating request.
 */
export function useLogin() {
  const queryClient = useQueryClient();
  const router = useRouter();
  return useMutation({
    mutationFn: login,
    onSuccess: async (result) => {
      startAuthenticatedCsrfSession();
      seedSession(queryClient, { id: result.profile.id, role: result.profile.role });
      await router.invalidate();
    },
  });
}
