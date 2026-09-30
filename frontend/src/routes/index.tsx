import { HomePage } from '@features/home/home-page';
import { currentUserQueryOptions } from '@features/user/user.queries';
import { requireRole } from '@lib/auth';
import { createFileRoute } from '@tanstack/react-router';

export const Route = createFileRoute('/')({
  beforeLoad: async ({ context: { queryClient } }) => {
    const user = await queryClient.ensureQueryData(currentUserQueryOptions());
    requireRole(user.roles, 'USER');
  },
  component: HomePage,
});
