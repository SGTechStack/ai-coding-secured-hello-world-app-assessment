import { useId, useLayoutEffect, useRef, type ReactNode } from 'react';

type DialogProps = {
  open: boolean;
  title: string;
  /** Optional; without it the dialog is not `aria-describedby` anything. */
  description?: string;
  /** Optional content between the description and the actions, e.g. a radio group. */
  body?: ReactNode;
  onClose: () => void;
  /** The actions; put the default one first. */
  children: ReactNode;
};

/**
 * A modal alert dialog on the native `<dialog>`: the browser supplies the backdrop, the inert page behind it, focus
 * containment and Esc. The backdrop is a static Tailwind class, so nothing is injected at runtime (CSP-safe).
 * It is controlled: `open` drives it, and Esc only asks the owner to close via `onClose`. Initial focus goes to a
 * checked radio in the body if there is one, otherwise to the first enabled button, so put the default action first.
 */
export function Dialog({ open, title, description, body, onClose, children }: Readonly<DialogProps>) {
  const ref = useRef<HTMLDialogElement>(null);
  const id = useId();
  // Layout effect: the dialog is already closed when the owner's flushSync returns, so it can move focus right after.
  useLayoutEffect(() => {
    const dialog = ref.current;
    if (!dialog || open === dialog.open) return;
    if (open) {
      dialog.showModal();
      dialog.querySelector<HTMLElement>('input[type="radio"]:checked, button:enabled')?.focus();
    } else dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      role="alertdialog"
      aria-labelledby={`${id}-title`}
      aria-describedby={description ? `${id}-description` : undefined}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-md rounded-card border border-line bg-surface p-6 text-ink shadow-sm
      backdrop:bg-ink/50 sm:p-8"
    >
      <h2 id={`${id}-title`} className="text-lg font-semibold text-ink">
        {title}
      </h2>
      {description && (
        <p id={`${id}-description`} className="mt-2 text-sm text-ink-muted">
          {description}
        </p>
      )}
      {body && <div className="mt-5">{body}</div>}
      <div className="mt-6 flex flex-col gap-3 sm:flex-row">{children}</div>
    </dialog>
  );
}
