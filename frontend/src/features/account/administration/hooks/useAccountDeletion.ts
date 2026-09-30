import { useMutation, useQueryClient } from '@tanstack/react-query';
import { deleteAccount } from '../api/account-deletion-api';
import { refusalOf } from '../api/refusal';
import { USER_LIST_KEY } from '../queries/user-list';

/**
 * One Account's deletion. Each control gets its own instance, so `pending` and any error are that row's alone. A
 * success refetches the shown User list page, whose row then reads "Deleted". An already-deleted or concurrency
 * refusal shows the server's message and refetches too, correcting the stale row; a self-target refusal or any other
 * failure leaves the row as it was. No retry: the control offers "Try again".
 *
 * @param onSettled runs after the request finishes either way (e.g. to close the confirmation dialog)
 */
export function useAccountDeletion(onSettled: () => void) {
  const queryClient = useQueryClient();
  const refetch = () => void queryClient.invalidateQueries({ queryKey: USER_LIST_KEY });
  const mutation = useMutation({
    mutationFn: deleteAccount,
    retry: false,
    onSuccess: () => {
      onSettled();
      refetch();
    },
    onError: (error) => {
      onSettled();
      const code = refusalOf(error)?.code;
      if (code === 'ACCOUNT_DELETED' || code === 'CONCURRENT_MODIFICATION') refetch();
    },
  });
  return {
    remove: (id: string) => mutation.mutate(id),
    pending: mutation.isPending,
    /** A 409 refusal shows the server's message; any other failure is a generic, retryable error. */
    refusal: mutation.isError ? refusalOf(mutation.error)?.message : undefined,
    failed: mutation.isError,
    reset: mutation.reset,
  };
}
