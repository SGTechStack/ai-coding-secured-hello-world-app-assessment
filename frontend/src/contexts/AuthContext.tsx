import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { bootstrapCsrf, getMe, login as apiLogin, logout as apiLogout } from '../api/auth';
import { ApiError } from '../api/client';
import type { UserResponse } from '../api/types';

interface AuthContextValue {
  user: UserResponse | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  setUser: (user: UserResponse | null) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    const init = async () => {
      try {
        // Bootstrap CSRF cookie before any state-changing request can fire.
        await bootstrapCsrf();
        // Rehydrate session: if /me succeeds the user is authenticated.
        const me = await getMe();
        if (!cancelled) setUser(me);
      } catch (err) {
        // 401 means no active session — normal unauthenticated start.
        if (err instanceof ApiError && err.isUnauthorized) {
          if (!cancelled) setUser(null);
        }
        // Other errors (network, 5xx) leave user as null; the UI will
        // prompt for login.
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void init();
    return () => { cancelled = true; };
  }, []);

  const login = async (username: string, password: string) => {
    await apiLogin(username, password);
    // Re-fetch /me after login so we have the full UserResponse (incl. UUID
    // needed for admin self-action guard on the frontend).
    const me = await getMe();
    setUser(me);
  };

  const logout = async () => {
    try {
      await apiLogout();
    } finally {
      setUser(null);
    }
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, setUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
