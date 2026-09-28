import { useEffect, useRef } from "react";
import { Button } from "../ui/Button";

interface ConfirmDialogProps {
  title: string;
  body: string;
  confirmLabel: string;
  destructive?: boolean;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * A confirmation step for the admin actions that cannot be undone.
 *
 * Built on the native `<dialog>` element rather than a hand-rolled overlay, so focus trapping, the
 * Escape key and the accessibility semantics come from the platform instead of from code that has to
 * be maintained and usually gets one of the three wrong.
 */
export function ConfirmDialog({
  title,
  body,
  confirmLabel,
  destructive = false,
  busy = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    dialogRef.current?.showModal();
  }, []);

  return (
    <dialog
      ref={dialogRef}
      // Escape and the backdrop both mean "cancel", and both have to end up in the same place.
      onCancel={(event) => {
        event.preventDefault();
        onCancel();
      }}
      // The backdrop is nearly opaque rather than a light wash: on a dark page a pale scrim would
      // brighten everything behind the dialog instead of pushing it back.
      className="max-w-sm rounded-lg border border-edge-strong bg-panel p-6 text-ink shadow-xl backdrop:bg-black/70"
    >
      <h2 className="text-base font-semibold text-ink">{title}</h2>
      <p className="mt-2 text-sm text-ink-muted">{body}</p>
      <div className="mt-4 flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
        <Button
          variant={destructive ? "danger" : "primary"}
          onClick={onConfirm}
          busy={busy}
          busyLabel="Applying the change"
        >
          {confirmLabel}
        </Button>
      </div>
    </dialog>
  );
}
