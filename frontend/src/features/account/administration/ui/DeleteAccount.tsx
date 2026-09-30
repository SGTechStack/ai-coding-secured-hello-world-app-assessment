import { useState } from 'react';
import { cn } from '../../../../common/lib/cn';
import { Button } from '../../../../common/ui/button';
import { Dialog } from '../../../../common/ui/dialog';
import { useAccountDeletion } from '../hooks/useAccountDeletion';
import type { ListedAccount } from '../model/user-list';
import { RetryLine } from './RetryLine';

type DeleteAccountProps = {
  account: ListedAccount;
  /** The caller's own row shows no control (the server refuses a self-deletion anyway). */
  isSelf: boolean;
  /** Full width on the mobile cards; compact in the desktop table. */
  block?: boolean;
};

/**
 * The per-row Delete control (Story 11), beside the status toggle: a secondary button tinted `danger`, opening a
 * confirmation whose `danger` action carries the emphasis. Cancel comes first, so the safe choice has initial focus,
 * and "Try again" after a failure reopens the confirmation rather than deleting in one click. The caller's own row
 * and a deleted row show no control (the status toggle's line explains why), but a refusal that just turned the row
 * into a tombstone keeps its message. The username and the server's message are React text, never HTML.
 */
export function DeleteAccount({ account, isSelf, block = false }: Readonly<DeleteAccountProps>) {
  const [confirming, setConfirming] = useState(false);
  const { remove, pending, refusal, failed, reset } = useAccountDeletion(() => setConfirming(false));
  const name = account.username;
  const label = pending ? 'Deleting…' : 'Delete';
  const confirm = () => {
    reset();
    setConfirming(true);
  };
  const message = refusal && (
    <p role="alert" className="text-xs text-danger">
      {refusal}
    </p>
  );

  if (account.deleted || isSelf) return message || null;

  return (
    <div className={block ? 'w-full' : 'flex flex-col items-end gap-1'}>
      <Button
        variant="secondary"
        block={block}
        aria-busy={pending}
        disabled={pending}
        // Compact in the table (text-xs, wide enough for "Deleting…" so nothing shifts), still >= 44px tall.
        className={cn(
          'border-danger-line text-danger hover:bg-danger-soft',
          !block && 'min-h-11 min-w-24 px-3 text-xs',
        )}
        aria-label={`Delete ${name}`}
        onClick={confirm}
      >
        {label}
      </Button>
      {message}
      {failed && !refusal && <RetryLine block={block} onRetry={confirm} />}

      <Dialog
        open={confirming}
        title={`Delete ${name}?`}
        description="This cannot be undone. They are signed out immediately and can never log in again; their username and email stay reserved."
        onClose={() => {
          if (!pending) setConfirming(false);
        }}
      >
        <Button variant="secondary" disabled={pending} onClick={() => setConfirming(false)}>
          Cancel
        </Button>
        <Button variant="danger" aria-busy={pending} disabled={pending} onClick={() => remove(account.id)}>
          {label}
        </Button>
      </Dialog>
    </div>
  );
}
