import type { QueryClient } from '@tanstack/react-query';
import { fetchCsrfToken, isAuthenticationRequired } from '../../../../common/http/api-client';
import { ensureCsrfToken, resetCsrfToken, startAuthenticatedCsrfSession } from '../../../../common/http/csrf';
import { fetchProfile } from '../../../account/profile';
import { seedSession } from '../queries/session';

type Navigator = { navigate: (options: { to: '/login' }) => Promise<void> };

/**
 * The one auth-lifecycle owner: every way a Session ends on the client (Logout, Session expired) goes through here.
 * In-flight queries are cancelled before the cache is cleared, so a late response can never repopulate a retired
 * Session. Clearing the whole cache, not only the session entry, drops every private query too. Guards run on every
 * navigation, so going Back afterwards lands on `/login` again. No notice is shown there.
 */
export async function endSession(queryClient: QueryClient, router: Navigator): Promise<void> {
  await queryClient.cancelQueries();
  queryClient.clear();
  resetCsrfToken();
  await router.navigate({ to: '/login' });
}

/** The server could not be asked whether the Session is still live (network, server or contract failure): retryable. */
export class SessionRestoreError extends Error {
  constructor(cause: unknown) {
    super('Unable to restore the Session', { cause });
    this.name = 'SessionRestoreError';
  }
}

/**
 * After a reload the in-memory Session is gone, but the server may still hold it (ADR 0005). Asks `GET /api/profile`
 * first; only a live Session then fetches its CSRF token, held as the authenticated session's token exactly as after
 * login, and seeds the Session cache. Nothing is restored unless both succeed.
 * @returns false when the server has no Session for this browser (Authentication-required)
 * @throws SessionRestoreError for any other failure
 */
export async function restoreSession(queryClient: QueryClient): Promise<boolean> {
  try {
    const profile = await fetchProfile();
    startAuthenticatedCsrfSession();
    await ensureCsrfToken(fetchCsrfToken);
    seedSession(queryClient, profile);
    return true;
  } catch (error) {
    resetCsrfToken();
    if (isAuthenticationRequired(error)) return false;
    throw new SessionRestoreError(error);
  }
}
