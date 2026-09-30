import { useQuery } from '@tanstack/react-query';
import { greetingQueryOptions } from '../queries/greeting';

/** The signed-in User's Greeting. The composing page passes the id in, so this feature never imports `auth`. */
export function useGreeting(userId: string | undefined) {
  return useQuery(greetingQueryOptions(userId));
}
