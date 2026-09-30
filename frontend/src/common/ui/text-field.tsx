import type { ComponentProps, ReactNode } from 'react';
import { CircleAlert } from 'lucide-react';
import { cn } from '../lib/cn';
import { FOCUS_RING } from './styles';

const INPUT_CLASSES = `block min-h-11 w-full rounded-control border border-line-strong bg-surface px-3.5 py-2 text-base
  text-ink shadow-xs transition-colors duration-150 placeholder:text-ink-subtle hover:border-ink-muted ${FOCUS_RING}
  aria-invalid:border-danger aria-invalid:bg-danger-soft
  disabled:cursor-not-allowed disabled:bg-surface-muted disabled:text-ink-muted`;

type TextFieldProps = ComponentProps<'input'> & {
  id: string;
  label: string;
  /** Shown under the input with role="alert" and wired to aria-describedby / aria-invalid. */
  error?: string;
  /** Short helper text shown under the label's input when there is no error. */
  hint?: string;
  /** Extra ids for aria-describedby, e.g. a live guidance panel rendered elsewhere. */
  describedBy?: string;
  /** Control inside the input's right edge (e.g. a show-password toggle); must be 44x44px. */
  trailing?: ReactNode;
};

/** Labelled input with its hint and error. Every page form field uses this; never a bare <input>. */
export function TextField({ id, label, error, hint, describedBy, trailing, className, ...input }: TextFieldProps) {
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  return (
    <div className="grid gap-1.5">
      <label className="text-sm font-medium text-ink" htmlFor={id}>
        {label}
      </label>
      <div className="relative">
        <input
          className={cn(INPUT_CLASSES, Boolean(trailing) && 'pr-12', className)}
          id={id}
          {...input}
          aria-invalid={error ? true : undefined}
          aria-describedby={cn(error && errorId, !error && hint && hintId, describedBy) || undefined}
        />
        {trailing && <div className="absolute inset-y-0 right-0 flex items-center">{trailing}</div>}
      </div>
      {error ? (
        <FieldError id={errorId}>{error}</FieldError>
      ) : (
        hint && (
          <p className="text-sm text-ink-muted" id={hintId}>
            {hint}
          </p>
        )
      )}
    </div>
  );
}

/** Inline field error. Text is a direct child so it reads as one message. */
function FieldError({ id, children }: Readonly<{ id: string; children: string }>) {
  return (
    <p className="flex items-start gap-1.5 text-sm whitespace-pre-line text-danger" id={id} role="alert">
      <CircleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      {children}
    </p>
  );
}
