import { useState } from 'react';
import { Button } from '@components/ui/button';
import { accountActionErrorMessage, useAccountLifecycle } from './accounts-lifecycle.queries';
import { ACCOUNT_ROLES, type AccountRole, type AccountSummary } from './accounts.queries';

/** Enable/disable, Role change and delete for one Account row. */
export function AccountRowActions({ account }: { account: AccountSummary }) {
  const { setEnabled, changeRole, deleteAccount, error, isPending } = useAccountLifecycle();
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  return (
    <div className="flex flex-col items-start gap-2">
      <div className="flex items-center gap-2">
        <select
          aria-label={`Role for ${account.username}`}
          className="bg-surface border-input h-8 rounded-md border px-2 text-xs"
          value={account.role}
          disabled={isPending}
          onChange={(event) =>
            void changeRole({ id: account.id, role: event.target.value as AccountRole }).catch(() => undefined)
          }
        >
          {ACCOUNT_ROLES.map((role) => (
            <option key={role} value={role}>
              {role}
            </option>
          ))}
        </select>
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
      {confirmingDelete && (
        <div className="flex items-center gap-2 text-xs">
          <span>Delete {account.username} permanently?</span>
          <Button
            type="button"
            variant="danger"
            size="sm"
            disabled={isPending}
            onClick={() => void deleteAccount({ id: account.id }).catch(() => setConfirmingDelete(false))}
          >
            Confirm delete
          </Button>
          <Button type="button" variant="outline" size="sm" onClick={() => setConfirmingDelete(false)}>
            Cancel
          </Button>
        </div>
      )}
      {error != null && (
        <p role="alert" className="text-danger text-xs">
          {accountActionErrorMessage(error)}
        </p>
      )}
    </div>
  );
}
