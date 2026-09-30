import { queryOptions, useQueryClient, useSuspenseQuery } from '@tanstack/react-query';
import {
  getListAccountsQueryKey,
  listAccounts,
  useCreateAccount as useGeneratedCreateAccount,
} from '@/api/generated-admin/admin-account-controller/admin-account-controller';
import { CreateAccountRequestRole } from '@/api/generated-admin/openAPIDefinition.schemas';
import { HttpError } from '@lib/http';

/** Roles an admin can assign, in the order offered by the form. */
export const ACCOUNT_ROLES = [
  CreateAccountRequestRole.USER,
  CreateAccountRequestRole.USER_MANAGER,
  CreateAccountRequestRole.ADMIN,
] as const;

export type AccountRole = (typeof ACCOUNT_ROLES)[number];

export type AccountSummary = {
  username: string;
  role: string;
  enabled: boolean;
  createdAt: string;
};

/** The new Account with its Temporary Password, which the server returns only this once. */
export type CreatedAccount = {
  username: string;
  role: string;
  temporaryPassword: string;
  temporaryPasswordExpiresAt: string;
};

/** Shown when the server gives no reason for a failed creation. */
export const CREATE_ACCOUNT_FAILED_MESSAGE = 'The account could not be created. Try again shortly.';

export const accountsQueryOptions = () =>
  queryOptions({
    queryKey: getListAccountsQueryKey(),
    queryFn: async () => (await listAccounts()).data as AccountSummary[],
  });

export const useAccounts = () => useSuspenseQuery(accountsQueryOptions());

/** Message for a failed creation: the server's reason (for example a taken username) or a fallback. */
export function createAccountErrorMessage(error: unknown): string {
  if (error instanceof HttpError && error.body?.detail) return error.body.detail;
  return CREATE_ACCOUNT_FAILED_MESSAGE;
}

/** Creates an Account and refreshes the list so it appears there. */
export function useCreateAccount() {
  const queryClient = useQueryClient();
  const mutation = useGeneratedCreateAccount({
    mutation: {
      onSuccess: () => queryClient.invalidateQueries({ queryKey: accountsQueryOptions().queryKey }),
    },
  });

  return {
    createAccount: async (values: { username: string; role: AccountRole }) =>
      (await mutation.mutateAsync({ data: values })).data as CreatedAccount,
    error: mutation.error,
    isPending: mutation.isPending,
    reset: mutation.reset,
  };
}
