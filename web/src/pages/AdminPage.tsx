import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as adminApi from "../api/admin.api";
import type { AccountSummary } from "../api/types";
import { useAuth } from "../auth/useAuth";
import { AccountRow, type AccountAction } from "../components/admin/AccountRow";
import { ConfirmDialog } from "../components/admin/ConfirmDialog";
import { Alert } from "../components/ui/Alert";
import { Card } from "../components/ui/Card";
import { Spinner } from "../components/ui/Spinner";
import { toMessage } from "../lib/errors";

interface PendingAction {
  action: AccountAction;
  account: AccountSummary;
}

const ACCOUNTS_QUERY_KEY = ["admin", "accounts"] as const;

/** Stories 8 to 11 — the admin module. */
export function AdminPage() {
  const { username } = useAuth();
  const queryClient = useQueryClient();
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const {
    data: accounts,
    isPending,
    error,
  } = useQuery({
    queryKey: ACCOUNTS_QUERY_KEY,
    queryFn: adminApi.listAccounts,
    retry: false,
  });

  const mutation = useMutation({
    mutationFn: ({ action, account }: PendingAction) => applyAction(action, account),
    onSuccess: async (_result, variables) => {
      setNotice(describeOutcome(variables));
      setPending(null);
      // Refetched rather than patched in place. A role change ends the target's sessions and a
      // delete removes their tokens, so the server's copy is the only one that is certainly right.
      await queryClient.invalidateQueries({ queryKey: ACCOUNTS_QUERY_KEY });
    },
    onError: (caught) => {
      setActionError(toMessage(caught, "Could not apply that change."));
      setPending(null);
    },
  });

  function startAction(action: AccountAction, account: AccountSummary) {
    setActionError(null);
    setNotice(null);
    setPending({ action, account });
  }

  return (
    <Card title="Accounts">
      <p className="mb-4 text-sm text-ink-muted">
        Disabling an account keeps its data and refuses its logins. Changing a role or disabling an
        account also ends that person's current sessions.
      </p>

      {notice ? <Alert tone="success">{notice}</Alert> : null}
      {actionError ? <Alert tone="error">{actionError}</Alert> : null}
      {error ? <Alert tone="error">{toMessage(error, "Could not load the accounts.")}</Alert> : null}

      {isPending ? (
        <p className="flex items-center gap-2 text-sm text-ink-faint">
          <Spinner label="Loading accounts" />
          Loading accounts
        </p>
      ) : null}

      {accounts ? (
        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-sm">
            <caption className="sr-only">
              Every registered account, with its role and status
            </caption>
            <thead>
              <tr className="border-b border-edge-strong text-left text-ink-faint">
                <th scope="col" className="px-3 py-2 font-medium">
                  Username
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Email
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Role
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Status
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Created
                </th>
                <th scope="col" className="px-3 py-2 text-right font-medium">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((account) => (
                <AccountRow
                  key={account.id}
                  account={account}
                  isSelf={account.username === username}
                  onAction={startAction}
                />
              ))}
            </tbody>
          </table>
        </div>
      ) : null}

      {pending ? (
        <ConfirmDialog
          title={confirmTitle(pending)}
          body={confirmBody(pending)}
          confirmLabel={confirmLabel(pending)}
          destructive={pending.action === "delete"}
          busy={mutation.isPending}
          onConfirm={() => mutation.mutate(pending)}
          onCancel={() => setPending(null)}
        />
      ) : null}
    </Card>
  );
}

/**
 * Discards each call's return value on purpose. The updated account comes back in the response, but
 * the list is refetched anyway — a role change or a disable has side effects beyond the row itself
 * (ended sessions, deleted tokens), so patching one row from the response would show a half-updated
 * picture.
 */
async function applyAction(action: AccountAction, account: AccountSummary): Promise<void> {
  switch (action) {
    case "enable":
      await adminApi.setAccountEnabled(account.id, true);
      return;
    case "disable":
      await adminApi.setAccountEnabled(account.id, false);
      return;
    case "promote":
      await adminApi.setAccountRole(account.id, "ADMIN");
      return;
    case "demote":
      await adminApi.setAccountRole(account.id, "USER");
      return;
    case "delete":
      await adminApi.deleteAccount(account.id);
      return;
  }
}

function confirmTitle({ action, account }: PendingAction) {
  switch (action) {
    case "enable":
      return `Enable ${account.username}?`;
    case "disable":
      return `Disable ${account.username}?`;
    case "promote":
      return `Make ${account.username} an admin?`;
    case "demote":
      return `Remove admin from ${account.username}?`;
    case "delete":
      return `Delete ${account.username}?`;
  }
}

function confirmBody({ action, account }: PendingAction) {
  switch (action) {
    case "enable":
      return `${account.username} will be able to log in again.`;
    case "disable":
      return `${account.username} will be logged out and refused at login. Their data is kept.`;
    case "promote":
      return `${account.username} will be able to manage every account, including yours. They will be logged out so the new role takes effect.`;
    case "demote":
      return `${account.username} will lose access to account management and be logged out.`;
    case "delete":
      return `This removes ${account.username} and their data permanently. There is no undo.`;
  }
}

function confirmLabel({ action }: PendingAction) {
  switch (action) {
    case "enable":
      return "Enable";
    case "disable":
      return "Disable";
    case "promote":
      return "Make admin";
    case "demote":
      return "Make user";
    case "delete":
      return "Delete";
  }
}

function describeOutcome({ action, account }: PendingAction) {
  switch (action) {
    case "enable":
      return `${account.username} can log in again.`;
    case "disable":
      return `${account.username} is disabled and has been logged out.`;
    case "promote":
      return `${account.username} is now an admin.`;
    case "demote":
      return `${account.username} is now a regular user.`;
    case "delete":
      return `${account.username} has been deleted.`;
  }
}
