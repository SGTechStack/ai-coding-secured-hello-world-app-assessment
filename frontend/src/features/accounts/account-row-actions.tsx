import { useState } from 'react';
import { ConfirmDialog } from '@components/layout/confirm-dialog';
import { Button } from '@components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@components/ui/select';
import { accountActionErrorMessage, useAccountLifecycle } from './accounts-lifecycle.queries';
import { ACCOUNT_ROLES, type AccountRole, type AccountSummary } from './accounts.queries';

/** Enable/disable, Role change and delete for one Account row. */
export function AccountRowActions({ account }: { account: AccountSummary }) {
  const { setEnabled, changeRole, deleteAccount, error, isPending } = useAccountLifecycle();
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  return (
    <div className="flex flex-col items-start gap-2">
      <div className="flex items-center gap-2">
        <Select
          value={account.role}
          disabled={isPending}
          onValueChange={(role) =>
            role != null && void changeRole({ id: account.id, role: role as AccountRole }).catch(() => undefined)
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
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={isPending}
          onClick={() => void setEnabled({ id: account.id, enabled: !account.enabled }).catch(() => undefined)}
        >
          {account.enabled ? 'Disable' : 'Enable'}
        </Button>
        <Button type="button" variant="danger" size="sm" disabled={isPending} onClick={() => setConfirmingDelete(true)}>
          Delete
        </Button>
      </div>
      <ConfirmDialog
        open={confirmingDelete}
        onOpenChange={setConfirmingDelete}
        title="Delete account"
        description={`Delete ${account.username} permanently? This cannot be undone.`}
        confirmLabel="Delete"
        variant="destructive"
        isPending={isPending}
        onConfirm={() => void deleteAccount({ id: account.id }).catch(() => setConfirmingDelete(false))}
      />
      {error != null && (
        <p role="alert" className="text-danger text-xs">
          {accountActionErrorMessage(error)}
        </p>
      )}
    </div>
  );
}
