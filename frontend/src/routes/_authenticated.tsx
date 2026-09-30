import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { Topbar } from '@components/layout/topbar';
import { currentUserQueryOptions } from '@features/user/user.queries';
import { HttpError } from '@lib/http';

/** Problem title the server sends while the holder must choose a new password. */
const PASSWORD_CHANGE_REQUIRED_TITLE = 'Password change required';

export const Route = createFileRoute('/_authenticated')({
  loader: async ({ context: { queryClient } }) => {
    try {
      await queryClient.ensureQueryData(currentUserQueryOptions());
    } catch (error) {
      if (error instanceof HttpError && error.status === 401) {
        // http() already triggered the redirect to the sign-in page — just suppress the error
        return;
      }
      // A Temporary Password blocks the profile lookup until it is replaced.
      if (error instanceof HttpError && error.status === 403 && error.body?.title === PASSWORD_CHANGE_REQUIRED_TITLE) {
        throw redirect({ to: '/change-password' });
      }
      throw error;
    }
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
