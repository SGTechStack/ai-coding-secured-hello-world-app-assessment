import { useState } from 'react';
import { Button } from '../../../../common/ui/button';
import { Dialog } from '../../../../common/ui/dialog';
import { useAccountStatus } from '../hooks/useAccountStatus';
import type { ListedAccount } from '../model/user-list';
import { RetryLine } from './RetryLine';

type StatusToggleProps = {
  account: ListedAccount;
  /** The caller's own row shows no control (the server refuses a self-toggle anyway). */
  isSelf: boolean;
  /** Applies the updated Account to the shown page after a successful toggle. */
  onToggled: (account: ListedAccount) => void;
  /** Full width on the mobile cards; compact in the desktop table. */
  block?: boolean;
};

/**
 * The per-row enable/disable control (Story 9). A compact secondary button both ways — quiet so it does not dominate
 * the row; the disable control is tinted `danger` as a subtle cue. Both directions confirm through a dialog that
 * carries the emphasis. The caller's own row and a deleted row show a line instead of a control.
 */
export function StatusToggle({ account, isSelf, onToggled, block = false }: Readonly<StatusToggleProps>) {
  const [confirming, setConfirming] = useState(false);
  const { toggle, pending, refusal, failed, reset } = useAccountStatus(
    (updated) => {
      setConfirming(false);
      onToggled(updated);
    },
    () => setConfirming(false),
  );

  if (account.deleted) return <Note block={block}>No actions — account deleted</Note>;
  if (isSelf) return <Note block={block}>Your account</Note>;

  const disabling = account.enabled; // enabled now → the action is "Disable"
  const verb = disabling ? 'Disable' : 'Enable';
  const busyLabel = disabling ? 'Disabling…' : 'Enabling…';
  const tint = disabling ? 'border-danger-line text-danger hover:bg-danger-soft' : '';
  const name = account.username;

  return (
    <div className={block ? 'w-full' : 'flex flex-col items-end gap-1'}>
      <Button
        variant="secondary"
        block={block}
        aria-busy={pending}
        disabled={pending}
        // Compact in the table (text-xs), still >= 44px tall from the button base; danger-tinted when disabling.
        className={block ? '' : `min-h-11 min-w-24 px-3 text-xs ${tint}`}
        aria-label={`${verb} ${name}`}
        onClick={() => {
          reset();
          setConfirming(true);
        }}
      >
        {pending ? busyLabel : verb}
      </Button>
      {refusal && (
        <p role="alert" className="text-xs text-danger">
          {refusal}
        </p>
      )}
      {failed && !refusal && <RetryLine block={block} onRetry={() => toggle(account.id, !account.enabled)} />}

      <Dialog
        open={confirming}
        title={`${verb} ${name}?`}
        description={
          disabling
            ? 'They will be signed out immediately and cannot log in until re-enabled. Their data is kept.'
            : 'They will be able to log in again.'
        }
        onClose={() => {
          if (!pending) setConfirming(false);
        }}
      >
        <Button
          variant={disabling ? 'danger' : 'primary'}
          aria-busy={pending}
          disabled={pending}
          onClick={() => toggle(account.id, !account.enabled)}
        >
          {pending ? busyLabel : verb}
        </Button>
        <Button variant="secondary" disabled={pending} onClick={() => setConfirming(false)}>
          Cancel
        </Button>
      </Dialog>
    </div>
  );
}

function Note({ children, block }: Readonly<{ children: string; block: boolean }>) {
  return <p className={`text-xs text-ink-muted ${block ? '' : 'text-right'}`}>{children}</p>;
}
