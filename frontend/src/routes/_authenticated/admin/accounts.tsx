import { createFileRoute, redirect } from '@tanstack/react-router';
import { AccountsPage } from '@features/accounts/accounts-page';
import { accountsQueryOptions } from '@features/accounts/accounts.queries';
import { HttpError } from '@lib/http';

export const Route = createFileRoute('/_authenticated/admin/accounts')({
  loader: async ({ context: { queryClient } }) => {
    try {
      await queryClient.ensureQueryData(accountsQueryOptions());
    } catch (error) {
      // The server decides who is an admin; anyone else is shown the forbidden page.
      if (error instanceof HttpError && error.status === 403) throw redirect({ to: '/forbidden' });
      throw error;
    }
  },
  component: AccountsPage,
});
