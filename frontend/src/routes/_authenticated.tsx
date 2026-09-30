import { createFileRoute, Outlet } from '@tanstack/react-router';
import { Topbar } from '@components/layout/topbar';
import { currentUserQueryOptions } from '@features/user/user.queries';
import { HttpError } from '@lib/http';

export const Route = createFileRoute('/_authenticated')({
  loader: async ({ context: { queryClient } }) => {
    try {
      await queryClient.ensureQueryData(currentUserQueryOptions());
    } catch (error) {
      if (error instanceof HttpError && error.status === 401) {
        // http() already triggered the redirect to the sign-in page — just suppress the error
        return;
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
