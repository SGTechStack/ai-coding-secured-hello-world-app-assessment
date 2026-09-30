import type { QueryClient } from '@tanstack/react-query';
import type { SessionProfile } from '../model/types';

const sessionQueryKey = ['session'] as const;

/**
 * The Session cache is the client's source of truth for "am I logged in". It is seeded from the login response, or
 * restored once after a reload (ADR 0005), never refetched, so it must never go stale or be garbage-collected while nothing observes it (the default
 * 5-minute gcTime would silently log the user out). Scoped to this key only, not applied globally.
 */
const SESSION_POLICY = {
  staleTime: Infinity,
  gcTime: Infinity,
  refetchOnMount: false,
  refetchOnWindowFocus: false,
  refetchOnReconnect: false,
} as const;

export function seedSession(queryClient: QueryClient, profile: SessionProfile): void {
  // Defaults apply when the query is first built, so they are set before the data creates it.
  queryClient.setQueryDefaults(sessionQueryKey, SESSION_POLICY);
  queryClient.setQueryData(sessionQueryKey, profile);
}

/** The signed-in profile, or undefined when there is no Session. */
export function readSession(queryClient: QueryClient): SessionProfile | undefined {
  return queryClient.getQueryData<SessionProfile>(sessionQueryKey);
}
