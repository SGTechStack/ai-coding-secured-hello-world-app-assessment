import type { ComponentProps } from 'react'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'

type ButtonVariant = ComponentProps<typeof Button>['variant']

/**
 * A button that opens a confirmation dialog; only its confirm button runs `onConfirm`. The dialog opens on Cancel,
 * closes on Escape and returns the focus to the trigger (R-FE-005).
 */
export function ConfirmAction({
  trigger,
  triggerVariant,
  title,
  description,
  confirm,
  confirmVariant,
  disabled,
  onConfirm,
}: {
  trigger: string
  triggerVariant: ButtonVariant
  title: string
  description: string
  confirm: string
  confirmVariant?: ButtonVariant
  disabled: boolean
  onConfirm: () => void
}) {
  return (
    <AlertDialog>
      <AlertDialogTrigger render={<Button type="button" variant={triggerVariant} disabled={disabled} />}>
        {trigger}
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogTitle>{title}</AlertDialogTitle>
        <AlertDialogDescription>{description}</AlertDialogDescription>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction variant={confirmVariant} onClick={onConfirm}>
            {confirm}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
