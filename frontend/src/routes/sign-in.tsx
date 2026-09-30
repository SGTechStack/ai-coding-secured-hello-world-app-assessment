import { createFileRoute, redirect } from '@tanstack/react-router';
import { SignInPage } from '@features/auth/sign-in-page';
import { currentUserQueryOptions } from '@features/user/user.queries';
import { loadWithStatusHandlers } from '@lib/route-http';

export const Route = createFileRoute('/sign-in')({
  // Someone already signed in has no use for the form. The probe also makes the backend hand out
  // the CSRF cookie the sign-in request must echo back.
  beforeLoad: async ({ context: { queryClient } }) => {
    const alreadySignedIn = await loadWithStatusHandlers({
      load: async () => {
        await queryClient.ensureQueryData(currentUserQueryOptions());
        return true;
      },
      handlers: { 401: () => false },
    });
    if (alreadySignedIn) throw redirect({ to: '/' });
  },
  component: SignInPage,
});
