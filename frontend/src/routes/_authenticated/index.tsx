import { HomePage } from '@features/home/home-page';
import { greetingQueryOptions } from '@features/greeting/greeting.queries';
import { createFileRoute } from '@tanstack/react-router';

export const Route = createFileRoute('/_authenticated/')({
  loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(greetingQueryOptions()),
  component: HomePage,
});
