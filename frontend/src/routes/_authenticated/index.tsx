import { HomePage } from '@features/home/home-page';
import { greetingQueryOptions } from '@features/greeting/greeting.queries';
import { currentUserQueryOptions } from '@features/user/user.queries';
import { ADMIN_ROLE } from '@lib/auth';
import { createFileRoute, redirect } from '@tanstack/react-router';

export const Route = createFileRoute('/_authenticated/')({
  loader: async ({ context: { queryClient } }) => {
    // Admins land on the Admin page; a failed lookup is handled by the parent route's loader.
    const user = await queryClient.ensureQueryData(currentUserQueryOptions()).catch(() => undefined);
    if (user?.roles.includes(ADMIN_ROLE)) throw redirect({ to: '/admin/accounts' });
    return queryClient.ensureQueryData(greetingQueryOptions());
  },
  component: HomePage,
});
