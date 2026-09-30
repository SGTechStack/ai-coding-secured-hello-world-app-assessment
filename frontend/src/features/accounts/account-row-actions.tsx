import { useState } from 'react';
import { Trash2, UserCheck, UserMinus } from 'lucide-react';
import { ActionTooltip } from './action-tooltip';
import { ConfirmDialog } from '@components/layout/confirm-dialog';
import { Button } from '@components/ui/button';
import { toast } from '@components/ui/toast';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@components/ui/select';
import { accountActionErrorMessage, useAccountLifecycle } from './accounts-lifecycle.queries';
import { ACCOUNT_ROLES, type AccountRole, type AccountSummary } from './accounts.queries';

/** Enable/disable and delete for one Account row. */
export function AccountRowActions({ account }: { account: AccountSummary }) {
  const { setEnabled, deleteAccount, error, isPending } = useAccountLifecycle();
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  return (
    <div className="flex flex-col items-start gap-2">
      <div className="flex items-center gap-2">
        <ActionTooltip label={account.enabled ? 'Disable Account' : 'Enable Account'}>
          <Button
            type="button"
            variant="outline"
            size="icon"
            className="size-8"
            aria-label={account.enabled ? 'Disable' : 'Enable'}
            disabled={isPending}
            onClick={() =>
              void setEnabled({ id: account.id, enabled: !account.enabled })
                .then(() =>
                  toast.add({
                    type: 'success',
                    title: account.enabled ? 'Account disabled' : 'Account enabled',
                    description: account.username,
                  }),
                )
                .catch(() => undefined)
            }
          >
            {account.enabled ? <UserMinus className="size-4" /> : <UserCheck className="size-4" />}
          </Button>
        </ActionTooltip>
        <ActionTooltip label="Delete Account">
          <Button
            type="button"
            variant="danger"
            size="icon"
            className="size-8"
            aria-label={`Delete ${account.username}`}
            disabled={isPending}
            onClick={() => setConfirmingDelete(true)}
          >
            <Trash2 className="size-4" />
          </Button>
        </ActionTooltip>
      </div>
      <ConfirmDialog
        open={confirmingDelete}
        onOpenChange={setConfirmingDelete}
        title="Delete account"
        description={`Delete ${account.username} permanently? This cannot be undone.`}
        confirmLabel="Delete"
        variant="destructive"
        isPending={isPending}
        onConfirm={() =>
          void deleteAccount({ id: account.id })
            .then(() => toast.add({ type: 'success', title: 'Account deleted', description: account.username }))
            .catch(() => setConfirmingDelete(false))
        }
      />
      {error != null && (
        <p role="alert" className="text-danger text-xs">
          {accountActionErrorMessage(error)}
        </p>
      )}
    </div>
  );
}

/** Role change for one Account row, shown in the Role column. */
export function AccountRoleSelect({ account }: { account: AccountSummary }) {
  const { changeRole, error, isPending } = useAccountLifecycle();

  return (
    <div className="flex flex-col items-start gap-2">
      <Select
        value={account.role}
        disabled={isPending}
        onValueChange={(role) =>
          role != null &&
          void changeRole({ id: account.id, role: role as AccountRole })
            .then(() =>
              toast.add({ type: 'success', title: 'Role changed', description: `${account.username} is now ${role}` }),
            )
            .catch(() => undefined)
        }
      >
        <SelectTrigger aria-label={`Role for ${account.username}`} className="h-8 w-auto gap-2 px-2 text-xs">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {ACCOUNT_ROLES.map((role) => (
            <SelectItem key={role} value={role}>
              {role}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {error != null && (
        <p role="alert" className="text-danger text-xs">
          {accountActionErrorMessage(error)}
        </p>
      )}
    </div>
  );
}
