import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import { AdminCredentialActions } from '@/components/AdminCredentialActions'
import { Button } from '@/components/ui/button'
import type { ErrorCode } from '@/lib/api/errors'
import { ADMIN_USERS_KEY, type AdminUser, adminUserKey, fetchAdminUser, setAdminUserEnabled } from '@/lib/admin/users'
import { authorityRoute, useAuthorityFailure } from '@/lib/auth/authority'
import { fetchProfile, PROFILE_KEY } from '@/lib/auth/session'

/** An unknown id is also refused with `ACCESS_DENIED`, so that code is shown here as not found, not followed. */
const OWN_CODES: readonly ErrorCode[] = ['ACCESS_DENIED']

/** A two-admin refusal names the way out, in order (spec, user story 40). */
const TWO_ADMIN_NEXT_STEPS =
  'At least two administrators with an authenticator app must remain, so this account cannot be disabled yet. ' +
  'To continue: invite a new user, have them redeem the invitation, promote them to administrator, and have them ' +
  'set up their authenticator app. Then try again.'
const CHANGE_COPY: Partial<Record<ErrorCode, string>> = {
  TWO_ADMIN_INVARIANT: TWO_ADMIN_NEXT_STEPS,
  ACCESS_DENIED: 'This account cannot be changed from here.',
}
const CHANGE_FAILED = 'The account could not be changed. Try again.'

/**
 * `/admin/users/:id`: one account (PRD Story 8), and the control that enables or disables it (PRD Story 9). Like the
 * list, it renders only what the server returned, and a refusal's code sends the session where it belongs.
 */
export function AdminUserDetailPage() {
  const { id = '' } = useParams()
  const user = useQuery({ queryKey: adminUserKey(id), queryFn: () => fetchAdminUser(id) })

  const leaving = authorityRoute(user.error, OWN_CODES)
  if (leaving) {
    return <Navigate to={leaving} replace />
  }

  return (
    <section aria-labelledby="user-heading" className="mt-6 flex flex-col gap-4">
      <h2 id="user-heading" className="text-xl font-semibold">
        {user.data ? user.data.username : 'User'}
      </h2>
      {user.isPending && <p className="text-muted-foreground">Loading…</p>}
      {user.error && (
        <p role="alert" className="text-sm text-destructive">
          That user could not be found.
        </p>
      )}
      {user.data && !user.error && (
        <>
          <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-sm">
            <dt>Email</dt>
            <dd>{user.data.email}</dd>
            <dt>Role</dt>
            <dd>{user.data.role}</dd>
            <dt>Status</dt>
            <dd>{user.data.enabled ? 'Enabled' : 'Disabled'}</dd>
            <dt>Created</dt>
            <dd>
              <time dateTime={user.data.createdAt}>{user.data.createdAt.slice(0, 10)}</time>
            </dd>
          </dl>
          <EnabledControl user={user.data} />
          <AdminCredentialActions user={user.data} />
        </>
      )}
      <Link to="/admin/users" className="text-sm underline">
        Back to the user list
      </Link>
    </section>
  )
}

/**
 * Enables or disables the account. Not offered on the admin's own account, which the server refuses anyway
 * (REJ-050). The server's answer replaces the cached account, and the list is refreshed in the background. The request
 * state is local, as on the other pages that change something.
 */
function EnabledControl({ user }: { user: AdminUser }) {
  const queryClient = useQueryClient()
  const profile = useQuery({ queryKey: PROFILE_KEY, queryFn: fetchProfile })
  const [pending, setPending] = useState(false)
  const [done, setDone] = useState('')
  const { redirect, failure, fail, clearFailure } = useAuthorityFailure(OWN_CODES)

  if (redirect) {
    return <Navigate to={redirect} replace />
  }
  // Offered only once the self-read says whose account this is; the server refuses a self-action whatever renders.
  if (!profile.data) {
    return null
  }
  if (profile.data.id === user.id) {
    return <p className="text-sm text-muted-foreground">You cannot change your own account.</p>
  }

  const onToggle = async () => {
    clearFailure()
    setDone('')
    setPending(true)
    try {
      const updated = await setAdminUserEnabled(user.id, !user.enabled)
      queryClient.setQueryData(adminUserKey(user.id), updated)
      void queryClient.invalidateQueries({ queryKey: ADMIN_USERS_KEY, exact: true })
      setDone(
        updated.enabled
          ? 'Account enabled. The user must change their password when they next sign in.'
          : 'Account disabled. The user has been signed out.',
      )
    } catch (error) {
      fail(error, CHANGE_COPY, CHANGE_FAILED)
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <div>
        <Button type="button" variant={user.enabled ? 'destructive' : 'default'} onClick={onToggle} disabled={pending}>
          {user.enabled ? 'Disable account' : 'Enable account'}
        </Button>
      </div>
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
