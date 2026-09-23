import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, ApiError, primeCsrfToken } from './api';

interface AuthState {
  status: 'loading' | 'authenticated' | 'anonymous';
  username: string | null;
  refresh: () => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthState['status']>('loading');
  const [username, setUsername] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const greeting = await api.hello();
      const match = greeting.match(/^Hello, (.+)$/);
      setUsername(match ? match[1] : greeting);
      setStatus('authenticated');
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setUsername(null);
        setStatus('anonymous');
      } else {
        throw error;
      }
    }
  }, []);

  const logout = useCallback(async () => {
    await api.logout();
    setUsername(null);
    setStatus('anonymous');
  }, []);

  useEffect(() => {
    primeCsrfToken().then(refresh);
  }, [refresh]);

  return (
    <AuthContext.Provider value={{ status, username, refresh, logout }}>
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
