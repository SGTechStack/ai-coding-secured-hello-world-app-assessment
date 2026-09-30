import { Pencil } from 'lucide-react';
import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { Button } from '../../../../common/ui/button';
import { Dialog } from '../../../../common/ui/dialog';
import { useRoleChange, useRoles } from '../hooks/useRoleChange';
import { roleLabel, type ListedAccount } from '../model/user-list';
import { RoleLabel } from './cells';
import { RetryLine } from './RetryLine';

type RoleChangeProps = {
  account: ListedAccount;
  /** The caller's own row shows the role only (the server refuses a self-change anyway). */
  isSelf: boolean;
  /** Applies the updated Account to the shown page after a successful change. */
  onChanged: (account: ListedAccount) => void;
};

const WARNING = "They'll be signed out and get the new role when they next sign in.";

/**
 * The role in a User list row or card, with a pencil that opens the Role change popup (Story 10, prototype variant A).
 * The caller's own row and a deleted row show the role only. The popup, with its queries, mounts only while open: every
 * row renders this twice (table and card), so a closed popup costs nothing.
 */
export function RoleChange({ account, isSelf, onChanged }: Readonly<RoleChangeProps>) {
  const [open, setOpen] = useState(false);
  if (isSelf || account.deleted) return <RoleLabel role={account.role} />;
  return (
    <span className="inline-flex items-center gap-1">
      <RoleLabel role={account.role} />
      <Button
        variant="ghost"
        className="size-11 px-0"
        aria-label={`Change role for ${account.username}`}
        onClick={() => setOpen(true)}
      >
        <Pencil aria-hidden="true" className="size-4" />
      </Button>
      {open && <RolePopup account={account} onChanged={onChanged} onClose={() => setOpen(false)} />}
    </span>
  );
}

/**
 * The popup: the roles the server defines as radios with the current one checked, and "Change role" disabled until
 * another is picked. A 409 or 400 shows the server's message inside it; any other failure, and a failed roles fetch,
 * offer "Try again" there.
 */
function RolePopup({
  account,
  onChanged,
  onClose,
}: Readonly<{
  account: ListedAccount;
  onChanged: (account: ListedAccount) => void;
  onClose: () => void;
}>) {
  const [choice, setChoice] = useState(account.role);
  const roles = useRoles(true);
  const { change, pending, refusal, failed } = useRoleChange((updated) => {
    onClose();
    onChanged(updated);
  });
  const group = useRef<HTMLFieldSetElement>(null);
  const legend = useId();
  const loaded = roles.data !== undefined;
  // The roles may arrive after the popup opened: move focus to the checked radio then.
  useEffect(() => {
    if (loaded) group.current?.querySelector<HTMLInputElement>('input:checked')?.focus();
  }, [loaded]);

  const close = () => {
    if (!pending) onClose();
  };
  const submit = () => change(account.id, choice);

  let picker: ReactNode;
  if (roles.isError) picker = <RetryLine block onRetry={() => void roles.refetch()} />;
  else if (!loaded)
    picker = (
      <div role="status" aria-label="Loading roles" className="flex flex-col gap-2">
        <div className="h-11 rounded-control bg-line" />
        <div className="h-11 rounded-control bg-line" />
      </div>
    );
  else
    picker = (
      <fieldset ref={group} aria-labelledby={legend} disabled={pending}>
        <legend id={legend} className="text-sm font-medium text-ink">
          Role
        </legend>
        <div className="mt-2 flex flex-col">
          {roles.data.map((role) => (
            <label key={role} className="flex min-h-11 cursor-pointer items-center gap-3 text-sm text-ink">
              <input
                type="radio"
                name={`role-${account.id}`}
                value={role}
                checked={choice === role}
                onChange={() => setChoice(role)}
                className="size-4 accent-primary"
              />
              {roleLabel(role)}
              {role === account.role && <span className="text-xs text-ink-muted">(current)</span>}
            </label>
          ))}
        </div>
      </fieldset>
    );

  return (
    <Dialog
      open
      title={`Change role for ${account.username}`}
      onClose={close}
      body={
        <>
          {picker}
          <p className="mt-4 text-sm text-ink-muted">{WARNING}</p>
          {refusal && (
            <p role="alert" className="mt-4 text-sm text-danger">
              {refusal}
            </p>
          )}
          {failed && !refusal && (
            <div className="mt-4">
              <RetryLine block onRetry={submit} />
            </div>
          )}
        </>
      }
    >
      <Button aria-busy={pending} disabled={pending || !loaded || choice === account.role} onClick={submit}>
        {pending ? 'Changing…' : 'Change role'}
      </Button>
      <Button variant="secondary" disabled={pending} onClick={close}>
        Cancel
      </Button>
    </Dialog>
  );
}
