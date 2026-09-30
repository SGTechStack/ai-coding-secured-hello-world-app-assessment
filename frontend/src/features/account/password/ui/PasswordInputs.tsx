import { useState, type ComponentProps } from 'react';
import { Check, Eye, EyeOff, X } from 'lucide-react';
import { TextField } from '../../../../common/ui/text-field';
import { STRENGTH_LABELS, type StrengthScore } from '../model/password-strength';

const STRENGTH_BAR = [
  'w-1/5 bg-meter-1',
  'w-2/5 bg-meter-2',
  'w-3/5 bg-meter-3',
  'w-4/5 bg-meter-4',
  'w-full bg-meter-5',
] as const;

type PasswordFieldProps = Omit<ComponentProps<typeof TextField>, 'type' | 'trailing' | 'autoComplete'> & {
  /** Names what the show/hide toggle reveals, e.g. "password confirmation". */
  toggleLabel: string;
};

/** A new-password field with an accessible show/hide toggle. */
export function PasswordField({ toggleLabel, disabled, id, ...props }: PasswordFieldProps) {
  const [shown, setShown] = useState(false);
  const Icon = shown ? EyeOff : Eye;
  return (
    <TextField
      autoComplete="new-password"
      disabled={disabled}
      id={id}
      type={shown ? 'text' : 'password'}
      {...props}
      trailing={
        <button
          aria-controls={id}
          aria-pressed={shown}
          className="grid size-11 cursor-pointer place-items-center rounded-control text-ink-muted transition-colors hover:text-ink focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-60"
          disabled={disabled}
          onClick={() => setShown((current) => !current)}
          type="button"
        >
          <Icon aria-hidden="true" className="size-5" />
          <span className="sr-only">
            {shown ? 'Hide' : 'Show'} {toggleLabel}
          </span>
        </button>
      }
    />
  );
}

const PASSWORD_HINT = '12–72 characters, with upper and lower case letters, a digit and a special character.';

type NewPasswordFieldProps = PasswordFieldProps & {
  checklist: PasswordGuidanceProps['checklist'];
  strength: PasswordGuidanceProps['strength'];
  passwordEntered: boolean;
};

/**
 * A new-password field with its rules. A short hint shows until the guidance opens: on focus, once a password is typed,
 * and after a submit that flagged the field. Focus is tracked on the group, so moving to the show/hide toggle keeps it.
 */
export function NewPasswordField({ checklist, strength, passwordEntered, error, ...field }: NewPasswordFieldProps) {
  const [focused, setFocused] = useState(false);
  const guidanceOpen = focused || passwordEntered || Boolean(error);
  return (
    <div
      className="grid gap-3"
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setFocused(false);
      }}
      onFocus={() => setFocused(true)}
    >
      <PasswordField
        describedBy={guidanceOpen ? 'password-guidance' : undefined}
        error={error}
        hint={guidanceOpen ? undefined : PASSWORD_HINT}
        {...field}
      />
      {guidanceOpen && <PasswordGuidance checklist={checklist} id="password-guidance" strength={strength} />}
    </div>
  );
}

type PasswordGuidanceProps = {
  id: string;
  checklist: { label: string; met: boolean }[];
  strength: StrengthScore | null;
};

/** Live password rules checklist and guidance-only strength meter, announced politely. */
function PasswordGuidance({ id, checklist, strength }: Readonly<PasswordGuidanceProps>) {
  const metCount = checklist.filter((rule) => rule.met).length;
  return (
    <div aria-live="polite" className="rounded-control border border-line bg-surface-muted p-4" id={id}>
      <div className="flex items-center gap-3">
        <div aria-hidden="true" className="h-1.5 flex-1 overflow-hidden rounded-full bg-line">
          {strength !== null && (
            <div
              className={`h-full rounded-full transition-all duration-300 ${STRENGTH_BAR[strength]}`}
              data-testid="strength-bar"
            />
          )}
        </div>
        <p className="text-xs font-medium text-ink-muted">
          Strength: {strength === null ? 'Not rated yet' : STRENGTH_LABELS[strength]}
        </p>
      </div>
      <p className="mt-3 text-xs font-semibold text-ink">
        Password must have ({metCount} of {checklist.length} met):
      </p>
      <ul className="mt-2 grid gap-1 text-sm">
        {checklist.map((rule) => (
          <li className={`flex items-center gap-2 ${rule.met ? 'text-success' : 'text-ink-muted'}`} key={rule.label}>
            <span
              aria-hidden="true"
              className={`grid size-4 shrink-0 place-items-center rounded-full ${rule.met ? 'bg-success text-ink-inverse' : 'bg-line text-ink-subtle'}`}
            >
              {rule.met ? <Check className="size-3" strokeWidth={3} /> : <X className="size-3" strokeWidth={3} />}
            </span>
            <span className="sr-only">{rule.met ? 'Met: ' : 'Not met: '}</span>
            {rule.label}
          </li>
        ))}
      </ul>
    </div>
  );
}
