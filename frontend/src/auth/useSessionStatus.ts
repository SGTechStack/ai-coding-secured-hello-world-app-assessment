import { useEffect, useState } from 'react';
import { type AuthenticatedUser, getMe } from '../api/authApi';

/**
 * Session status as restored from the server, not from any locally-cached
 * flag: every mount calls `GET /api/v1/auth/me` fresh. This is deliberate —
 * it's what makes the route guards below correct both for a hard refresh
 * (spec: session-restore-on-load) and immediately after a login/logout
 * navigation (the cookie has just changed and there is no other signal that
 * would tell a freshly-mounted guard about that).
 */
export type SessionStatus =
  | { state: 'checking' }
  | { state: 'authenticated'; user: AuthenticatedUser }
  | { state: 'unauthenticated' };

export function useSessionStatus(): SessionStatus {
  const [status, setStatus] = useState<SessionStatus>({ state: 'checking' });

  useEffect(() => {
    let cancelled = false;

    getMe().then((user) => {
      if (cancelled) {
        return;
      }
      setStatus(user ? { state: 'authenticated', user } : { state: 'unauthenticated' });
    });

    return () => {
      cancelled = true;
    };
  }, []);

  return status;
}
