import { queryOptions } from '@tanstack/react-query';
import { fetchGreeting } from '../api/greeting-api';

/**
 * A private per-user query: keyed by the User's id and disabled until it is known. Ending the Session clears the
 * whole cache, so another User never sees this one's Greeting. No automatic retry: a failure shows at once and the
 * Home page offers "Try again", and a 401 is never retried.
 */
export function greetingQueryOptions(userId: string | undefined) {
  return queryOptions({
    queryKey: ['greeting', userId],
    queryFn: fetchGreeting,
    enabled: userId !== undefined,
    retry: false,
  });
}
