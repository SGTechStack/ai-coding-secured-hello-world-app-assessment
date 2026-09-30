import { useMutation, useQueryClient } from '@tanstack/react-query';
import { refusalOf } from '../api/refusal';
import { setAccountEnabled } from '../api/status-toggle-api';
import type { ListedAccount } from '../model/user-list';
import { USER_LIST_KEY } from '../queries/user-list';

/**
 * One Account's status toggle. Each control gets its own instance, so `pending` and any error are that row's alone.
 * On success the caller updates the row in place from the returned Account; on failure the caller is told so it can
 * close the dialog and surface the error on the row. No retry: the control offers "Try again". A concurrency refusal
 * (the Account changed meanwhile) also refetches the User list, so the Admin decides again on current data.
 *
 * @param onToggled applies the updated Account to the shown page
 * @param onError runs after a failed toggle (e.g. to close the confirmation dialog)
 */
export function useAccountStatus(onToggled: (account: ListedAccount) => void, onError: () => void) {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) => setAccountEnabled(id, enabled),
    retry: false,
    onSuccess: onToggled,
    onError: (error) => {
      onError();
      if (refusalOf(error)?.code === 'CONCURRENT_MODIFICATION')
        void queryClient.invalidateQueries({ queryKey: USER_LIST_KEY });
    },
  });
  return {
    toggle: (id: string, enabled: boolean) => mutation.mutate({ id, enabled }),
    pending: mutation.isPending,
    /** A 409 refusal shows the server's message; any other failure is a generic, retryable error. */
    refusal: mutation.isError ? refusalOf(mutation.error)?.message : undefined,
    failed: mutation.isError,
    reset: mutation.reset,
  };
}
