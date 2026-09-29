import { type KeyboardEvent, useEffect, useId, useRef } from 'react'

interface ConfirmDialogProps {
  message: string
  confirmLabel: string
  onConfirm: () => void
  onCancel: () => void
}

/**
 * A modal confirmation (`role="alertdialog"`) labelled by its message. Focus moves to Cancel, the safe
 * choice, when it opens, stays within its two buttons, and Escape cancels.
 */
export function ConfirmDialog({ message, confirmLabel, onConfirm, onCancel }: ConfirmDialogProps) {
  const messageId = useId()
  const cancelRef = useRef<HTMLButtonElement>(null)
  const confirmRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    cancelRef.current?.focus()
  }, [])

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      onCancel()
    } else if (event.key === 'Tab') {
      event.preventDefault()
      const next = document.activeElement === cancelRef.current ? confirmRef : cancelRef
      next.current?.focus()
    }
  }

  return (
    <div className="dialog-backdrop">
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={messageId}
        className="dialog panel"
        onKeyDown={handleKeyDown}
      >
        <p id={messageId} className="dialog__message">
          {message}
        </p>
        {/* row-reverse puts the confirm button on the right while Cancel keeps initial focus. */}
        <div className="dialog__actions">
          <button type="button" className="btn btn--danger" ref={confirmRef} onClick={onConfirm}>
            {confirmLabel}
          </button>
          <button type="button" className="btn btn--ghost" ref={cancelRef} onClick={onCancel}>
            Cancel
          </button>
        </div>
      </div>
    </div>
  )
}
