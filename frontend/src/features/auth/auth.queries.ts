import { useQueryClient } from '@tanstack/react-query';
import { useSignIn as useGeneratedSignIn } from '@/api/generated/sign-in-controller/sign-in-controller';
import { currentUserQueryOptions } from '@features/user/user.queries';
import { greetingQueryOptions } from '@features/greeting/greeting.queries';
import { HttpError } from '@lib/http';
import type { SignInValues } from './auth.schema';

const HTTP_TOO_MANY_REQUESTS = 429;

/** Shown when the server gives no reason; it never says which part of the credentials was wrong. */
export const SIGN_IN_FAILED_MESSAGE = 'Invalid username or password';

/** Shown when the server throttles this address for too many failures and gives no detail. */
export const SIGN_IN_THROTTLED_MESSAGE = 'Too many failed sign-in attempts. Try again later.';

/** Shown when sign-in could not be attempted at all (network or server error). */
export const SIGN_IN_UNAVAILABLE_MESSAGE = 'Sign-in is unavailable right now. Try again shortly.';

/**
 * Message for a failed sign-in: the server's detail, or a fallback when it sent none. A refused
 * password during an Account delay looks like any other 401, so only the address throttle (429)
 * is worded differently.
 */
export function signInErrorMessage(error: unknown): string {
  if (error instanceof HttpError && error.status === 401) {
    return error.body?.detail ?? SIGN_IN_FAILED_MESSAGE;
  }
  if (error instanceof HttpError && error.status === HTTP_TOO_MANY_REQUESTS) {
    return error.body?.detail ?? SIGN_IN_THROTTLED_MESSAGE;
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
