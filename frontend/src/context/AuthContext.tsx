import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  ApiError,
  adminListUsers,
  fetchHello,
  login as apiLogin,
  logout as apiLogout,
  parseGreeting,
  type Role,
} from '../api/client';

export interface AuthUser {
  username: string;
  role: Role;
}

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

interface AuthContextValue {
  status: AuthStatus;
  user: AuthUser | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

// /api/hello confirms *that* the caller is logged in and gives the username, but
// not the role. Rather than adding a backend endpoint, reuse the existing
// admin-only listing as a role probe: 200 means the caller is an admin, 403
// means they're not. This only runs once, on cold load/refresh; a fresh login
// already returns the role directly.
async function probeRole(): Promise<Role> {
  try {
    await adminListUsers();
    return 'ADMIN';
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) {
      return 'USER';
    }
    throw error;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<AuthUser | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function probe() {
      try {
        const greeting = await fetchHello();
        const username = parseGreeting(greeting);
        if (!username) {
          throw new Error(`Unexpected greeting format: ${greeting}`);
        }
        const role = await probeRole();
        if (!cancelled) {
          setUser({ username, role });
          setStatus('authenticated');
        }
      } catch {
        if (!cancelled) {
          setUser(null);
          setStatus('anonymous');
        }
      }
    }

    void probe();
    return () => {
      cancelled = true;
    };
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      async login(username, password) {
        const response = await apiLogin(username, password);
        setUser({ username: response.username, role: response.role });
        setStatus('authenticated');
      },
      async logout() {
        try {
          await apiLogout();
        } finally {
          setUser(null);
          setStatus('anonymous');
        }
      },
    }),
    [status, user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
