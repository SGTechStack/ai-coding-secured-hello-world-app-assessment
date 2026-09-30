import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { Navigate } from 'react-router'
import { OneTimeToken } from '@/components/OneTimeToken'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import type { ErrorCode } from '@/lib/api/errors'
import { issuePasswordReset, oneTimeLink, UNLOCK_REASONS, type UnlockReason, unlockUser } from '@/lib/admin/credentials'
import { ADMIN_USERS_KEY, type AdminUser, adminUserKey } from '@/lib/admin/users'
import { useAuthorityFailure } from '@/lib/auth/authority'
import { fetchProfile, PROFILE_KEY } from '@/lib/auth/session'

/** A self-action refusal is shown here, not followed to `/hello`. */
const OWN_CODES: readonly ErrorCode[] = ['ACCESS_DENIED']

const RESET_COPY: Partial<Record<ErrorCode, string>> = {
  VALIDATION_FAILED: 'A reset link can only be issued for an activated, enabled account.',
  ACCESS_DENIED: 'This account cannot be changed from here.',
}
const RESET_FAILED = 'The reset link could not be issued. Try again.'
const UNLOCK_COPY: Partial<Record<ErrorCode, string>> = {
  ACCESS_DENIED: 'This account cannot be changed from here.',
}
const UNLOCK_FAILED = 'The account could not be unlocked. Try again.'

/**
 * The admin credential actions on one account: issue a password-reset link, shown once (ADR-006), for any account
 * including the admin's own; and unlock another account, with a reason (REJ-028; REJ-072).
 */
export function AdminCredentialActions({ user }: { user: AdminUser }) {
  const profile = useQuery({ queryKey: PROFILE_KEY, queryFn: fetchProfile })
  // Offered only once the self-read says whose account this is; the server enforces both rules whatever renders.
  if (!profile.data) {
    return null
  }
  const own = profile.data.id === user.id
  return (
    <div className="flex flex-col gap-4">
      <ResetControl user={user} own={own} />
      {!own && <UnlockControl user={user} />}
    </div>
  )
}

function ResetControl({ user, own }: { user: AdminUser; own: boolean }) {
  const [pending, setPending] = useState(false)
  const [token, setToken] = useState('')
  const { redirect, failure, fail, clearFailure } = useAuthorityFailure(OWN_CODES)

  if (redirect) {
    return <Navigate to={redirect} replace />
  }

  const onIssue = async () => {
    clearFailure()
    setPending(true)
    try {
      setToken((await issuePasswordReset(user.id)).token)
    } catch (error) {
      fail(error, RESET_COPY, RESET_FAILED)
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <p className="text-sm text-muted-foreground">
        {own
          ? 'Issuing a reset link for your own account signs you out everywhere, this session included.'
          : 'Issuing a reset link signs the user out everywhere. It does not unlock the account.'}
      </p>
      <div>
        <Button type="button" variant="outline" onClick={onIssue} disabled={pending || token !== ''}>
          Issue password reset link
        </Button>
      </div>
      {token && <OneTimeToken heading="Password reset link" token={token} link={oneTimeLink('reset', token)} />}
      <p role="alert" className="text-sm text-destructive">
        {failure}
      </p>
    </div>
  )
}

function UnlockControl({ user }: { user: AdminUser }) {
  const queryClient = useQueryClient()
  const reasonId = useId()
  const [reason, setReason] = useState<UnlockReason>('USER_REQUEST')
  const [pending, setPending] = useState(false)
  const [done, setDone] = useState('')
  const { redirect, failure, fail, clearFailure } = useAuthorityFailure(OWN_CODES)

  if (redirect) {
    return <Navigate to={redirect} replace />
  }

  const onUnlock = async () => {
    clearFailure()
    setDone('')
    setPending(true)
    try {
      queryClient.setQueryData(adminUserKey(user.id), await unlockUser(user.id, reason))
      void queryClient.invalidateQueries({ queryKey: ADMIN_USERS_KEY, exact: true })
      setDone('Account unlocked. Its password lockout and factor lock are cleared.')
    } catch (error) {
      fail(error, UNLOCK_COPY, UNLOCK_FAILED)
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <Label htmlFor={reasonId}>Reason for unlocking</Label>
      <select
        id={reasonId}
        value={reason}
        onChange={(event) => setReason(event.target.value as UnlockReason)}
        className="h-9 w-fit rounded-md border bg-transparent px-3 text-sm"
      >
        {Object.entries(UNLOCK_REASONS).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </select>
      <div>
        <Button type="button" onClick={onUnlock} disabled={pending}>
          Unlock account
        </Button>
      </div>
      <p role="status" className="text-sm">
        {done}
      </p>
      <p role="alert" className="text-sm text-destructive">
        {failure}
      </p>
    </div>
  )
}
