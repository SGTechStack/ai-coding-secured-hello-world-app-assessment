import { createFileRoute, redirect } from '@tanstack/react-router';
import { AccountsPage } from '@features/accounts/accounts-page';
import { accountsQueryOptions } from '@features/accounts/accounts.queries';
import { loadWithStatusHandlers } from '@lib/route-http';

export const Route = createFileRoute('/_authenticated/admin/accounts')({
  loader: async ({ context: { queryClient } }) => {
    await loadWithStatusHandlers({
      load: () => queryClient.ensureQueryData(accountsQueryOptions()),
      handlers: {
        // The server decides who is an admin; anyone else is shown the forbidden page.
        403: () => {
          throw redirect({ to: '/forbidden' });
        },
      },
    });
  },
  component: AccountsPage,
});
