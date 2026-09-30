import { createFileRoute, redirect } from '@tanstack/react-router';
import { SignInPage } from '@features/auth/sign-in-page';
import { currentUserQueryOptions } from '@features/user/user.queries';
import { HttpError } from '@lib/http';

export const Route = createFileRoute('/sign-in')({
  // Someone already signed in has no use for the form. The probe also makes the backend hand out
  // the CSRF cookie the sign-in request must echo back.
  beforeLoad: async ({ context: { queryClient } }) => {
    try {
      await queryClient.ensureQueryData(currentUserQueryOptions());
    } catch (error) {
      if (error instanceof HttpError && error.status === 401) return;
      throw error;
    }
    throw redirect({ to: '/' });
  },
  component: SignInPage,
});
