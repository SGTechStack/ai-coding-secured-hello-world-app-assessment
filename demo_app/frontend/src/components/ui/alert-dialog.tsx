import { useEffect, useId, useRef, type ReactNode } from "react";

type AlertDialogProps = {
  open: boolean;
  /** Called with `false` when the dialog closes itself (Escape, or `close()`). */
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  description: ReactNode;
  /** The footer actions, least destructive first: it gets the initial focus. */
  children: ReactNode;
};

/**
 * A confirmation dialog in the shadcn `AlertDialog` style, built on the native `<dialog>` opened
 * with `showModal()`. The browser makes it modal (the rest of the page is inert), moves focus into
 * it, closes it on Escape and returns focus afterwards.
 *
 * Why not Radix: its AlertDialog locks scrolling through `react-remove-scroll`, which injects a
 * runtime `<style>` element that the SPA's CSP (`style-src 'self'`) blocks. The native element
 * needs no injected styles, so the CSP stays strict; the scroll lock is a plain rule in
 * `styles.css`.
 */
export function AlertDialog({
  open,
  onOpenChange,
  title,
  description,
  children,
}: AlertDialogProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      role="alertdialog"
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      onClose={() => onOpenChange(false)}
      className="m-auto w-full max-w-[calc(100%-2rem)] rounded-lg border bg-background p-6 text-foreground shadow-lg backdrop:bg-black/50 sm:max-w-lg"
    >
      {open && (
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-2 text-center sm:text-left">
            <h2 id={titleId} className="text-lg font-semibold">
              {title}
            </h2>
            <p id={descriptionId} className="text-sm text-muted-foreground">
              {description}
            </p>
          </div>
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            {children}
          </div>
        </div>
      )}
    </dialog>
  );
}
