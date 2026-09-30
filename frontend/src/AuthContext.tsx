import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, ApiError, primeCsrfToken } from './api';

interface AuthState {
  status: 'loading' | 'authenticated' | 'anonymous' | 'password-change-required';
  username: string | null;
  role: 'USER' | 'ADMIN' | null;
  refresh: () => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthState['status']>('loading');
  const [username, setUsername] = useState<string | null>(null);
  const [role, setRole] = useState<AuthState['role']>(null);

  const refresh = useCallback(async () => {
    try {
      const hello = await api.hello();
      setUsername(hello.username);
      setRole(hello.role);
      setStatus('authenticated');
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setUsername(null);
        setRole(null);
        setStatus('anonymous');
      } else if (error instanceof ApiError && error.status === 403 && error.message === 'PASSWORD_CHANGE_REQUIRED') {
        // ForcePasswordChangeFilter blocks every endpoint but
        // /api/auth/change-password, /api/logout, /api/csrf until the
        // user (e.g. the seeded bootstrap admin) sets a new password.
        setUsername(null);
        setRole(null);
        setStatus('password-change-required');
      } else {
        throw error;
      }
    }
  }, []);

  const logout = useCallback(async () => {
    await api.logout();
    setUsername(null);
    setRole(null);
    setStatus('anonymous');
  }, []);

  useEffect(() => {
    primeCsrfToken().then(refresh);
  }, [refresh]);

  return (
    <AuthContext.Provider value={{ status, username, role, refresh, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
