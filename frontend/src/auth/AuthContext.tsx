import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { api, ApiError } from '../lib/api';
import type { User } from '../lib/types';

interface AuthState {
  user: User | null;
  loading: boolean;
  startupError: string;
  signIn: (username: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [startupError, setStartupError] = useState('');

  useEffect(() => {
    let active = true;
    const expired = () => setUser(null);
    window.addEventListener('session-expired', expired);
    api
      .get<User>('/auth/me')
      .then((account) => {
        if (active) setUser(account);
      })
      .catch((error: unknown) => {
        if (active && !(error instanceof ApiError && error.status === 401)) {
          setStartupError(error instanceof Error ? error.message : 'Unable to load your account.');
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
      window.removeEventListener('session-expired', expired);
    };
  }, []);

  async function signIn(username: string, password: string) {
    setUser(await api.post<User>('/auth/login', { username, password }));
  }

  async function signOut() {
    await api.post('/auth/logout');
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, loading, startupError, signIn, signOut }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error('useAuth must be used inside AuthProvider.');
  return auth;
}
