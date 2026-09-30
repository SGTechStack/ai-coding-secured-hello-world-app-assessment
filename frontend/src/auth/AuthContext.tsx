import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, ApiError, type Me } from '../api/client';

/** Who is logged in, as reported by `/me`. Held in memory only, never in browser storage. */
interface Auth {
  loading: boolean;
  me: Me | null;
  login(username: string, password: string): Promise<void>;
  /** Ends the session and clears the SPA's state. Never fails: an expired session is already over. */
  logout(): Promise<void>;
}

const AuthContext = createContext<Auth | null>(null);

/** The login succeeded, but the logged-in Account couldn't be loaded. */
export class AccountLoadError extends Error {
  constructor() {
    super('Logged in, but the account could not be loaded.');
    this.name = 'AccountLoadError';
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [loading, setLoading] = useState(true);
  const [me, setMe] = useState<Me | null>(null);

  // Any lost session clears the state; pages that need a login then redirect to it.
  useEffect(() => {
    api.setUnauthenticatedHandler(() => setMe(null));
    return () => api.setUnauthenticatedHandler(undefined);
  }, []);

  const loadMe = useCallback(async () => {
    try {
      setMe(await api.me());
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setMe(null);
      } else {
        throw error;
      }
    }
  }, []);

  useEffect(() => {
    api
      .start()
      .then(loadMe)
      .catch(() => setMe(null))
      .finally(() => setLoading(false));
  }, [loadMe]);

  const login = useCallback(
    async (username: string, password: string) => {
      // Only a failure here means the login itself failed.
      await api.login(username, password);
      try {
        await loadMe();
      } catch {
        throw new AccountLoadError();
      }
    },
    [loadMe],
  );

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } catch {
      // The session had already ended, e.g. by expiring: the user is logged out either way.
    } finally {
      setMe(null);
    }
  }, []);

  const value = useMemo(() => ({ loading, me, login, logout }), [loading, me, login, logout]);
  return <AuthContext value={value}>{children}</AuthContext>;
}

export function useAuth(): Auth {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error('useAuth must be used inside AuthProvider');
  return auth;
}
