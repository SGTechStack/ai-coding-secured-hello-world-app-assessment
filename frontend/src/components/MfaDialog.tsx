import { Dialog } from '@base-ui/react/dialog'
import { TotpCodeForm } from '@/components/TotpCodeForm'
import { Button } from '@/components/ui/button'

interface MfaDialogProps {
  /** Whether the dialog is shown. */
  open: boolean
  /** The factor asked for; the title reads "<mfaType> Verification" (FE-STD §8.1). */
  mfaType?: string
  /** Why the code is asked for, shown under the title. */
  description?: string
  /** The code is being checked: Verify shows the spinner and is not actionable. */
  showSpinner?: boolean
  /** Hold Verify disabled, as while a factor lock lasts. */
  disabled?: boolean
  /** A failure to announce in the dialog's live region. */
  message?: string
  /** Sends a well-formed code; resolves with a message for the field (a refused code) or `undefined`. */
  callback: (code: string) => Promise<string | undefined>
  /** Cancel, Esc or a press outside the dialog: the dialog asks to close. */
  onCancel: () => void
}

/**
 * The modal code prompt (FE-STD §8.1): a titled dialog with the six-digit code form, focused on the code when it opens
 * (T-FE-010), and a Cancel. Every way of dismissing it calls `onCancel`; focus then returns to where it was (T-FE-007).
 */
export function MfaDialog({
  open,
  mfaType = 'TOTP',
  description,
  showSpinner = false,
  disabled = false,
  message = '',
  callback,
  onCancel,
}: MfaDialogProps) {
  return (
    <Dialog.Root
      open={open}
      onOpenChange={(next) => {
        if (!next) {
          onCancel()
        }
      }}
    >
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 bg-black/40" />
        <Dialog.Popup className="fixed top-1/2 left-1/2 flex w-[min(28rem,calc(100vw-2rem))] -translate-x-1/2 -translate-y-1/2 flex-col gap-4 rounded-lg border bg-background p-6 shadow-lg">
          <Dialog.Title className="text-lg font-semibold">{mfaType} Verification</Dialog.Title>
          {description && <Dialog.Description className="text-sm">{description}</Dialog.Description>}
          <TotpCodeForm submitLabel="Verify" onCode={callback} showSpinner={showSpinner} disabled={disabled} />
          {/* Always rendered, so a screen reader announces the message when it appears (live region). */}
          <p role="alert" className="text-sm text-destructive">
            {message}
          </p>
          <div>
            <Dialog.Close render={<Button variant="outline" />}>Cancel</Dialog.Close>
          </div>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
