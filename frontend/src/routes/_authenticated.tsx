import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { Topbar } from '@components/layout/topbar';
import { currentUserQueryOptions } from '@features/user/user.queries';
import { loadWithStatusHandlers } from '@lib/route-http';

/** Problem title the server sends while the holder must choose a new password. */
const PASSWORD_CHANGE_REQUIRED_TITLE = 'Password change required';

export const Route = createFileRoute('/_authenticated')({
  loader: async ({ context: { queryClient } }) => {
    await loadWithStatusHandlers({
      load: () => queryClient.ensureQueryData(currentUserQueryOptions()),
      handlers: {
        // http() already triggered the redirect to the sign-in page — just suppress the error
        401: () => undefined,
        // A Temporary Password blocks the profile lookup until it is replaced.
        403: (error) => {
          if (error.body?.title === PASSWORD_CHANGE_REQUIRED_TITLE) throw redirect({ to: '/change-password' });
          throw error;
        },
      },
    });
  },
  component: AuthenticatedLayout,
});

function AuthenticatedLayout() {
  return (
    <>
      <Topbar />
      <Outlet />
    </>
  );
}
