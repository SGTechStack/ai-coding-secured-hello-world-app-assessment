import { useQueryClient } from '@tanstack/react-query';
import { useSignIn as useGeneratedSignIn } from '@/api/generated/sign-in-controller/sign-in-controller';
import { currentUserQueryOptions } from '@features/user/user.queries';
import { greetingQueryOptions } from '@features/greeting/greeting.queries';
import { HttpError } from '@lib/http';
import type { SignInValues } from './auth.schema';

/** Shown when the server gives no reason; it never says which part of the credentials was wrong. */
export const SIGN_IN_FAILED_MESSAGE = 'Invalid username or password';

/** Shown when sign-in could not be attempted at all (network or server error). */
export const SIGN_IN_UNAVAILABLE_MESSAGE = 'Sign-in is unavailable right now. Try again shortly.';

/** Message for a failed sign-in: the server's generic detail, or a fallback when it sent none. */
export function signInErrorMessage(error: unknown): string {
  if (error instanceof HttpError && error.status === 401) {
    return error.body?.detail ?? SIGN_IN_FAILED_MESSAGE;
  }
  return SIGN_IN_UNAVAILABLE_MESSAGE;
}

/** Signs in and refreshes anything cached from before the session existed. */
export function useSignIn() {
  const queryClient = useQueryClient();
  const mutation = useGeneratedSignIn({
    mutation: {
      onSuccess: () =>
        Promise.all([
          queryClient.invalidateQueries({ queryKey: currentUserQueryOptions().queryKey }),
          queryClient.invalidateQueries({ queryKey: greetingQueryOptions().queryKey }),
        ]),
    },
  });

  return {
    signIn: (values: SignInValues) => mutation.mutateAsync({ data: values }),
    error: mutation.error,
    isPending: mutation.isPending,
  };
}
