import { useCallback, useMemo, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import * as authApi from "../api/auth.api";
import type { Session } from "../api/types";
import { AuthContext, type AuthState } from "./auth-context";

/** Not exported: nothing outside this provider should be reaching into the session cache directly. */
const SESSION_QUERY_KEY = ["session"] as const;

/**
 * Holds the answer to "who is logged in", asked once on load and re-derived from the server after
 * every login or logout.
 *
 * The server is the only authority here. Nothing about the session is kept in `localStorage`: the
 * session lives in an `HttpOnly` cookie the frontend cannot read, and a client-side copy would only
 * be a second version of the truth to go stale — showing a logged-in shell around requests the
 * server is already refusing.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();

  const { data: session, isLoading } = useQuery<Session>({
    queryKey: SESSION_QUERY_KEY,
    queryFn: authApi.getSession,
    // Anonymous is a perfectly good answer, not a failure worth retrying.
    retry: false,
    staleTime: 30_000,
  });

  const logIn = useCallback(
    async (input: { username: string; password: string }) => {
      const result = await authApi.login(input);
      queryClient.setQueryData(SESSION_QUERY_KEY, result);
    },
    [queryClient],
  );

  const logOut = useCallback(async () => {
    await authApi.logout();
    queryClient.setQueryData(SESSION_QUERY_KEY, { authenticated: false } satisfies Session);
    // Anything cached under the old identity — the admin list, for instance — must not survive into
    // the next session.
    await queryClient.invalidateQueries();
  }, [queryClient]);

  const value = useMemo<AuthState>(() => {
    const authenticated = session?.authenticated === true;
    return {
      session,
      isLoading,
      username: authenticated ? session.username : null,
      role: authenticated ? session.role : null,
      isAuthenticated: authenticated,
      isAdmin: authenticated && session.role === "ADMIN",
      logIn,
      logOut,
    };
  }, [session, isLoading, logIn, logOut]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
