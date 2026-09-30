import { useQueryClient } from '@tanstack/react-query';
import {
  useChangeRole,
  useDelete,
  useSetEnabled,
} from '@/api/generated-admin/admin-account-lifecycle-controller/admin-account-lifecycle-controller';
import { HttpError } from '@lib/http';
import { accountsQueryOptions, type AccountRole } from './accounts.queries';

/** Shown when the server gives no reason for a refused enable, disable, Role change or delete. */
export const ACCOUNT_ACTION_FAILED_MESSAGE = 'The change could not be made. Try again shortly.';

/** Message for a failed action: the server's reason (for example "cannot disable your own account") or a fallback. */
export function accountActionErrorMessage(error: unknown): string {
  if (error instanceof HttpError && error.body?.detail) return error.body.detail;
  return ACCOUNT_ACTION_FAILED_MESSAGE;
}

/** Enable, disable, Role change and delete of one Account; each refreshes the Account list. */
export function useAccountLifecycle() {
  const queryClient = useQueryClient();
  const refreshList = () => queryClient.invalidateQueries({ queryKey: accountsQueryOptions().queryKey });
  const setEnabledMutation = useSetEnabled({ mutation: { onSuccess: refreshList } });
  const changeRoleMutation = useChangeRole({ mutation: { onSuccess: refreshList } });
  const deleteMutation = useDelete({ mutation: { onSuccess: refreshList } });

  return {
    setEnabled: (args: { id: string; enabled: boolean }) =>
      setEnabledMutation.mutateAsync({ id: args.id, data: { enabled: args.enabled } }),
    changeRole: (args: { id: string; role: AccountRole }) =>
      changeRoleMutation.mutateAsync({ id: args.id, data: { role: args.role } }),
    deleteAccount: (args: { id: string }) => deleteMutation.mutateAsync({ id: args.id }),
    error: setEnabledMutation.error ?? changeRoleMutation.error ?? deleteMutation.error,
    isPending: setEnabledMutation.isPending || changeRoleMutation.isPending || deleteMutation.isPending,
  };
}
