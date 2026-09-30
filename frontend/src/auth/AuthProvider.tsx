import { useCallback, useMemo, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { authApi } from '../api/auth';
import { ANONYMOUS, type LoginInput, type SessionUser } from '../api/types';
import { AuthContext, SESSION_QUERY_KEY, type AuthContextValue } from './AuthContext';

/**
 * Loads the current session once from the server (the cookie is HttpOnly, so this is the only
 * way to know who we are) and exposes login/logout that keep the cache in sync.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();

  const sessionQuery = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: authApi.me,
    staleTime: Infinity,
  });

  const login = useCallback(
    async (input: LoginInput) => {
      const session = await authApi.login(input);
      queryClient.setQueryData(SESSION_QUERY_KEY, session);
      return session;
    },
    [queryClient],
  );

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      queryClient.clear();
      queryClient.setQueryData(SESSION_QUERY_KEY, ANONYMOUS);
    }
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(() => {
    const user: SessionUser | null = sessionQuery.data ?? (sessionQuery.isError ? ANONYMOUS : null);
    return {
      user,
      isLoading: user === null,
      isAuthenticated: user?.authenticated === true,
      isAdmin: user?.authenticated === true && user.role === 'ADMIN',
      login,
      logout,
    };
  }, [sessionQuery.data, sessionQuery.isError, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
