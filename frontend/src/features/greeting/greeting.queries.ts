import { queryOptions, useSuspenseQuery } from '@tanstack/react-query';
import { getHelloQueryKey, hello } from '@/api/generated/greeting-controller/greeting-controller';

export const greetingQueryOptions = () =>
  queryOptions({
    queryKey: getHelloQueryKey(),
    queryFn: async () => {
      const res = await hello();
      return res.data.message ?? '';
    },
  });

export const useGreeting = () => useSuspenseQuery(greetingQueryOptions());
