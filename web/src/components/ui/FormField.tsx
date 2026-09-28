import { useId, type InputHTMLAttributes } from "react";

interface FormFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "id"> {
  label: string;
  error?: string;
  hint?: string;
}

/**
 * A labelled input with its error and hint wired up properly.
 *
 * The label is a real `<label>` bound by id, and the error and hint are referenced from
 * `aria-describedby` so they are read out with the field rather than sitting next to it unannounced.
 * Placeholder text is not a label: it disappears the moment someone types.
 *
 * The field gets an explicit background rather than inheriting one. A transparent input on a dark
 * panel reads as static text, and people cannot click what does not look clickable.
 */
export function FormField({ label, error, hint, ...inputProps }: FormFieldProps) {
  const id = useId();
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(" ");

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium text-ink">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        className={`rounded-md border bg-panel-hover px-3 py-2 text-sm text-ink placeholder:text-ink-faint focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-accent ${
          error ? "border-danger" : "border-edge-strong"
        }`}
        {...inputProps}
      />
      {hint ? (
        <p id={hintId} className="text-xs text-ink-faint">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className="text-xs text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
}
