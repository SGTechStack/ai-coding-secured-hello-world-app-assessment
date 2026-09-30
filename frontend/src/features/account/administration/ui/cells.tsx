import { Ban, CircleCheck, Lock, ShieldCheck, Trash2 } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '../../../../common/lib/cn';
import { accountStatus, formatSgt, roleLabel, type AccountStatus, type ListedAccount } from '../model/user-list';

const PILL = 'inline-flex items-center gap-1 whitespace-nowrap rounded-full border px-2 py-0.5 text-xs font-medium';

// Disabled is neutral rather than danger: it is reversible and not an error.
const STATUS: Record<AccountStatus, { classes: string; Icon: typeof Ban }> = {
  Active: { classes: 'border-success-line bg-success-soft text-success', Icon: CircleCheck },
  Locked: { classes: 'border-warning-line bg-warning-soft text-warning', Icon: Lock },
  Disabled: { classes: 'border-line-strong bg-surface-muted text-ink-muted', Icon: Ban },
  Deleted: { classes: 'border-danger-line bg-danger-soft text-danger', Icon: Trash2 },
};

/** Text and icon, never colour alone. Precedence Deleted > Disabled > Locked > Active. */
export function StatusPill({ account }: Readonly<{ account: ListedAccount }>) {
  const status = accountStatus(account);
  const { classes, Icon } = STATUS[status];
  return (
    <span className={cn(PILL, classes)}>
      <Icon aria-hidden="true" className="size-3.5" />
      {status}
    </span>
  );
}

/** Admin stands out as a pill; every other role is plain text. */
export function RoleLabel({ role }: Readonly<{ role: string }>) {
  if (role !== 'ADMIN') return <span>{roleLabel(role)}</span>;
  return (
    <span className={cn(PILL, 'border-info-line bg-info-soft text-info')}>
      <ShieldCheck aria-hidden="true" className="size-3.5" />
      {roleLabel(role)}
    </span>
  );
}

/** A UTC time shown in Singapore time; a missing one is "—", or `missing` (e.g. "Never" for no login). */
export function SgtTime({ value, missing }: Readonly<{ value: string | null; missing?: string }>) {
  if (value === null) {
    return missing ? <span className="text-ink-muted">{missing}</span> : <span className="text-ink-subtle">—</span>;
  }
  return <time dateTime={value}>{formatSgt(value)}</time>;
}

export function Email({ value }: { value: string | null }) {
  return value === null ? <span className="text-ink-subtle">—</span> : <span className="break-all">{value}</span>;
}

export function Field({ label, children }: Readonly<{ label: string; children: ReactNode }>) {
  return (
    <div>
      <dt className="text-xs font-medium text-ink-muted">{label}</dt>
      <dd className="mt-0.5 text-ink">{children}</dd>
    </div>
  );
}

/** The fields behind a row's or card's "Show details". */
export function AccountDetails({ account, className }: Readonly<{ account: ListedAccount; className: string }>) {
  return (
    <dl className={cn('grid gap-4 text-sm', className)}>
      <Field label="Account ID">
        <span className="break-all font-mono text-xs">{account.id}</span>
      </Field>
      <Field label="Failed login attempts">{account.failedLoginAttempts}</Field>
      <Field label="Locked until (SGT)">
        <SgtTime value={account.lockedUntil} />
      </Field>
      <Field label="Disabled at (SGT)">
        <SgtTime value={account.disabledAt} />
      </Field>
      <Field label="Deleted at (SGT)">
        <SgtTime value={account.deletedAt} />
      </Field>
      <Field label="Updated at (SGT)">
        <SgtTime value={account.updatedAt} />
      </Field>
    </dl>
  );
}
