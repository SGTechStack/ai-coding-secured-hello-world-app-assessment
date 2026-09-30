import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import type { ProblemDetailJson } from '../../../../common/http/problem-detail';
import { changeAccountRole, fetchRoles } from '../api/role-change-api';
import { refusalOf } from '../api/refusal';
import type { ListedAccount } from '../model/user-list';
import { USER_LIST_KEY } from '../queries/user-list';

/** The roles an Admin may give: fetched when a popup first opens, then cached for the page. No automatic retry. */
export function useRoles(enabled: boolean) {
  return useQuery({ queryKey: ['admin-roles'], queryFn: fetchRoles, enabled, staleTime: Infinity, retry: false });
}

/** A 400 answer's fixed `detail` (e.g. an undeclared role), shown like a refusal. */
function invalidRequestDetail(error: unknown): string | undefined {
  if (!axios.isAxiosError<ProblemDetailJson>(error) || error.response?.status !== 400) return undefined;
  return error.response.data?.detail;
}

/**
 * One Account's Role change. Each popup gets its own instance, so `pending` and any error are that row's alone. A 409
 * or 400 shows the server's message; any other failure is generic and retryable. A concurrency refusal also refetches
 * the User list, so the Admin decides again on current data.
 *
 * @param onChanged applies the updated Account to the shown page
 */
export function useRoleChange(onChanged: (account: ListedAccount) => void) {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: ({ id, role }: { id: string; role: string }) => changeAccountRole(id, role),
    retry: false,
    onSuccess: onChanged,
    onError: (error) => {
      if (refusalOf(error)?.code === 'CONCURRENT_MODIFICATION')
        void queryClient.invalidateQueries({ queryKey: USER_LIST_KEY });
    },
  });
  return {
    change: (id: string, role: string) => mutation.mutate({ id, role }),
    pending: mutation.isPending,
    refusal: mutation.isError
      ? (refusalOf(mutation.error)?.message ?? invalidRequestDetail(mutation.error))
      : undefined,
    failed: mutation.isError,
    reset: mutation.reset,
  };
}
