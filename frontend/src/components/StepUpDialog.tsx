import { useQueryClient } from '@tanstack/react-query'
import { useState, useSyncExternalStore } from 'react'
import { MfaDialog } from '@/components/MfaDialog'
import { INVALID_CODE, TOO_MANY_ATTEMPTS } from '@/components/TotpCodeForm'
import { ApiError } from '@/lib/api/errors'
import { abandonStepUp, completeStepUp, isStepUpPending, subscribeStepUp } from '@/lib/api/stepUp'
import { authorityRoute } from '@/lib/auth/authority'
import { PROFILE_KEY } from '@/lib/auth/session'
import { useFactorLock } from '@/lib/mfa/useFactorLock'
import { verifyTotp } from '@/lib/mfa/verification'

const DESCRIPTION =
  'Changes need a code from the last 10 minutes. Enter the 6-digit code your authenticator app shows, and your ' +
  'change will go through.'
const GENERIC_FAILURE = 'The code could not be checked. Try again.'

/**
 * The step-up challenge (ADR-021): open while the step-up queue waits on a code. A verified code rotated the session,
 * and {@link verifyTotp} has fetched the new CSRF token by the time it resolves, so the queued requests are replayed
 * with it. Dismissing the dialog drops the queue. A refused code keeps it open; a refusal that moves the session
 * elsewhere (signed out, a disabled factor) drops the queue with that refusal, which each page then follows.
 *
 * Mounted once, around the admin surface: while it is mounted, refused admin changes are queued.
 */
export function StepUpDialog() {
  const queryClient = useQueryClient()
  const open = useSyncExternalStore(subscribeStepUp, isStepUpPending)
  const lock = useFactorLock()
  const [checking, setChecking] = useState(false)
  const [failure, setFailure] = useState('')

  const onCode = async (code: string) => {
    setFailure('')
    setChecking(true)
    try {
      await verifyTotp(code)
      completeStepUp()
      void queryClient.invalidateQueries({ queryKey: PROFILE_KEY })
      return undefined
    } catch (error) {
      if (error instanceof ApiError && error.code === 'INVALID_FACTOR') {
        return INVALID_CODE
      }
      if (lock.holdFor(error)) {
        return undefined
      }
      if (authorityRoute(error)) {
        abandonStepUp(error)
        return undefined
      }
      setFailure(error instanceof ApiError && error.code === 'TOO_MANY_REQUESTS' ? TOO_MANY_ATTEMPTS : GENERIC_FAILURE)
      return undefined
    } finally {
      setChecking(false)
    }
  }

  return (
    <MfaDialog
      open={open}
      description={DESCRIPTION}
      showSpinner={checking}
      disabled={lock.locked}
      demoCodeHint
      message={lock.message ?? failure}
      callback={onCode}
      onCancel={() => {
        setFailure('')
        abandonStepUp()
      }}
    />
  )
}
