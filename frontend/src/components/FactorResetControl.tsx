import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Navigate } from 'react-router'
import { Button } from '@/components/ui/button'
import type { ErrorCode } from '@/lib/api/errors'
import { type AdminUser, resetAdminUserFactor } from '@/lib/admin/users'
import { useAuthorityFailure } from '@/lib/auth/authority'
import { fetchProfile, PROFILE_KEY } from '@/lib/auth/session'

/** An unknown id, or the admin's own, is refused with `ACCESS_DENIED`; that is shown here, not followed. */
const OWN_CODES: readonly ErrorCode[] = ['ACCESS_DENIED']
const RESET_COPY: Partial<Record<ErrorCode, string>> = {
  ACCESS_DENIED: "This account's authenticator app cannot be reset from here.",
}
const RESET_FAILED = 'The authenticator app could not be reset. Try again.'
const RESET_DONE =
  'Authenticator app reset. The user has been signed out and must set it up again when they next sign in.'

/**
 * Resets another administrator's authenticator app, for a lost phone or a disabled factor (ADR-049). Offered only on
 * another administrator's account, once the self-read says whose account this is; the server refuses a self-reset
 * whatever renders. The reset signs the user out, so it asks once more before it is sent.
 */
export function FactorResetControl({ user }: { user: AdminUser }) {
  const profile = useQuery({ queryKey: PROFILE_KEY, queryFn: fetchProfile })
  const [confirming, setConfirming] = useState(false)
  const [pending, setPending] = useState(false)
  const [done, setDone] = useState('')
  const { redirect, failure, fail, clearFailure } = useAuthorityFailure(OWN_CODES)

  if (redirect) {
    return <Navigate to={redirect} replace />
  }
  if (user.role !== 'ADMIN' || !profile.data || profile.data.id === user.id) {
    return null
  }

  const onReset = async () => {
    clearFailure()
    setDone('')
    setPending(true)
    try {
      await resetAdminUserFactor(user.id)
      setDone(RESET_DONE)
    } catch (error) {
      fail(error, RESET_COPY, RESET_FAILED)
    } finally {
      setPending(false)
      setConfirming(false)
    }
  }

  return (
    <div className="flex flex-col gap-2">
      {confirming ? (
        <>
          <p className="text-sm">
            Reset {user.username}&apos;s authenticator app? They will be signed out and must set it up again when they
            next sign in.
          </p>
          <div className="flex gap-2">
            <Button type="button" variant="destructive" onClick={onReset} disabled={pending}>
              Confirm reset
            </Button>
            <Button type="button" variant="outline" onClick={() => setConfirming(false)} disabled={pending}>
              Cancel
            </Button>
          </div>
        </>
      ) : (
        <div>
          <Button type="button" variant="outline" onClick={() => setConfirming(true)}>
            Reset authenticator app
          </Button>
        </div>
      )}
      {/* Always rendered, so a screen reader announces each message when it appears (live regions). */}
      <p role="status" className="text-sm">
        {done}
      </p>
      <p role="alert" className="text-sm text-destructive">
        {failure}
      </p>
    </div>
  )
}
