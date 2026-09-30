import { createContext } from 'react';
import type { LoginInput, SessionUser } from '../api/types';

export interface AuthContextValue {
  /** null while the initial session lookup is in flight. */
  user: SessionUser | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  isAdmin: boolean;
  login: (input: LoginInput) => Promise<SessionUser>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

/** TanStack Query key under which the current session is cached. */
export const SESSION_QUERY_KEY = ['session'] as const;
