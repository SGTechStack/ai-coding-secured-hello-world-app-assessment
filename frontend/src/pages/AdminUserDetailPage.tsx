import { useQuery, useQueryClient } from '@tanstack/react-query'
import { type ComponentProps, useEffect, useRef, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'
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
import type { ErrorCode } from '@/lib/api/errors'
import {
  ADMIN_USERS_KEY,
  type AdminUser,
  adminUserKey,
  deleteAdminUser,
  fetchAdminUser,
  resetAdminUserFactor,
  setAdminUserEnabled,
  setAdminUserRole,
} from '@/lib/admin/users'
import { authorityRoute, useAuthorityFailure } from '@/lib/auth/authority'
import { fetchProfile, PROFILE_KEY } from '@/lib/auth/session'

/** An unknown id is also refused with `ACCESS_DENIED`, so that code is shown here as not found, not followed. */
const OWN_CODES: readonly ErrorCode[] = ['ACCESS_DENIED']

/** A two-admin refusal names the way out, in order (spec, user story 40). */
const twoAdminNextSteps = (refused: string) =>
  `At least two administrators with an authenticator app must remain, so this account cannot be ${refused} yet. ` +
  'To continue: invite a new user, have them redeem the invitation, promote them to administrator, and have them ' +
  'set up their authenticator app. Then try again.'
const changeCopy = (refused: string): Partial<Record<ErrorCode, string>> => ({
  TWO_ADMIN_INVARIANT: twoAdminNextSteps(refused),
  ACCESS_DENIED: 'This account cannot be changed from here.',
})
const CHANGE_FAILED = 'The account could not be changed. Try again.'

/**
 * `/admin/users/:id`: one account (PRD Story 8), and the controls that enable or disable it, change its role and
 * delete it (PRD Stories 9–11), and reset an administrator's authenticator app (ADR-049). Like the list, it renders only what the server returned, and a refusal's code sends
 * the session where it belongs.
 */
export function AdminUserDetailPage() {
  const { id = '' } = useParams()
  const [deleted, setDeleted] = useState('')
  const [notice, setNotice] = useState('')
  const user = useQuery({ queryKey: adminUserKey(id), queryFn: () => fetchAdminUser(id), enabled: !deleted })
  const heading = useRef<HTMLHeadingElement>(null)

  // The delete unmounts the dialog's trigger, so its focus return has nowhere to go: the heading takes the focus.
  useEffect(() => {
    if (deleted) {
      heading.current?.focus()
    }
  }, [deleted])

  const leaving = authorityRoute(user.error, OWN_CODES)
  if (leaving) {
    return <Navigate to={leaving} replace />
  }

  return (
    <section aria-labelledby="user-heading" className="mt-6 flex flex-col gap-4">
      <h2 id="user-heading" ref={heading} tabIndex={-1} className="text-xl font-semibold outline-none">
        {deleted || (user.data ? user.data.username : 'User')}
      </h2>
      {!deleted && user.isPending && <p className="text-muted-foreground">Loading…</p>}
      {!deleted && user.error && (
        <p role="alert" className="text-sm text-destructive">
          That user could not be found.
        </p>
      )}
      {!deleted && user.data && !user.error && (
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
          <AccountActions user={user.data} announce={setNotice} onDeleted={setDeleted} />
        </>
      )}
      {/* Always rendered, so a screen reader announces each outcome when it appears (live region). */}
      <p role="status" className="text-sm">
        {notice}
      </p>
      <Link to="/admin/users" className="text-sm underline">
        Back to the user list
      </Link>
    </section>
  )
}

/**
 * Enables or disables the account, changes its role, deletes it and, for an administrator, resets their
 * authenticator app; all but the enable control are confirmed in a dialog first. Not offered on the admin's own account, which the server refuses anyway (REJ-050). The server's
 * answer replaces the cached account, and the list is refreshed in the background. The request state is local, as on
 * the other pages that change something.
 */
function AccountActions({
  user,
  announce,
  onDeleted,
}: {
  user: AdminUser
  announce: (outcome: string) => void
  onDeleted: (username: string) => void
}) {
  const queryClient = useQueryClient()
  const profile = useQuery({ queryKey: PROFILE_KEY, queryFn: fetchProfile })
  const [pending, setPending] = useState(false)
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

  /** Runs one change and shows its outcome; `refused` completes the two-admin copy for it. */
  const run = async (change: () => Promise<string>, refused: string) => {
    clearFailure()
    announce('')
    setPending(true)
    try {
      announce(await change())
    } catch (error) {
      fail(error, changeCopy(refused), CHANGE_FAILED)
    } finally {
      setPending(false)
    }
  }

  const accept = (updated: AdminUser) => {
    queryClient.setQueryData(adminUserKey(user.id), updated)
    void queryClient.invalidateQueries({ queryKey: ADMIN_USERS_KEY, exact: true })
  }

  const onToggle = () =>
    run(async () => {
      const updated = await setAdminUserEnabled(user.id, !user.enabled)
      accept(updated)
      return updated.enabled
        ? 'Account enabled. The user must change their password when they next sign in.'
        : 'Account disabled. The user has been signed out.'
    }, 'disabled')

  const promote = user.role === 'USER'
  const onRoleChange = () =>
    run(
      async () => {
        const updated = await setAdminUserRole(user.id, promote ? 'ADMIN' : 'USER')
        accept(updated)
        return `Role changed to ${updated.role === 'ADMIN' ? 'administrator' : 'user'}. The user has been signed out.`
      },
      promote ? 'promoted' : 'demoted',
    )

  const onDelete = () =>
    run(async () => {
      await deleteAdminUser(user.id)
      queryClient.removeQueries({ queryKey: adminUserKey(user.id), exact: true })
      void queryClient.invalidateQueries({ queryKey: ADMIN_USERS_KEY, exact: true })
      onDeleted(user.username)
      return 'Account deleted. The user has been signed out, and the username and email cannot be used again.'
    }, 'deleted')

  const onFactorReset = () =>
    run(async () => {
      await resetAdminUserFactor(user.id)
      return 'Authenticator app reset. The user has been signed out and must set it up again when they next sign in.'
    }, 'reset')

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap gap-2">
        {/* Focusable while disabled, so focus can return here when a step-up challenge closes (T-FE-007). */}
        <Button
          type="button"
          variant={user.enabled ? 'destructive' : 'default'}
          onClick={onToggle}
          disabled={pending}
          focusableWhenDisabled
        >
          {user.enabled ? 'Disable account' : 'Enable account'}
        </Button>
        <ConfirmAction
          trigger={promote ? 'Make administrator' : 'Make user'}
          triggerVariant="outline"
          title={promote ? `Make ${user.username} an administrator?` : `Make ${user.username} a user?`}
          description={
            promote
              ? 'They are signed out, and must set up an authenticator app before they can use the admin pages.'
              : 'They are signed out, and lose access to the admin pages.'
          }
          confirm="Change role"
          disabled={pending}
          onConfirm={onRoleChange}
        />
        <ConfirmAction
          trigger="Delete account"
          triggerVariant="destructive"
          title={`Delete ${user.username}?`}
          description="The account is removed and they are signed out. Its username and email can never be used again. This cannot be undone."
          confirm="Delete"
          confirmVariant="destructive"
          disabled={pending}
          onConfirm={onDelete}
        />
        {/* Only administrators have an authenticator app (ADR-023). */}
        {user.role === 'ADMIN' && (
          <ConfirmAction
            trigger="Reset authenticator app"
            triggerVariant="outline"
            title={`Reset ${user.username}'s authenticator app?`}
            description="They are signed out, and must set up their authenticator app again when they next sign in."
            confirm="Reset"
            disabled={pending}
            onConfirm={onFactorReset}
          />
        )}
      </div>
      {/* Always rendered, so a screen reader announces a failure when it appears (live region). */}
      <p role="alert" className="text-sm text-destructive">
        {failure}
      </p>
    </div>
  )
}

type ButtonVariant = ComponentProps<typeof Button>['variant']

/**
 * A button that opens a confirmation dialog; only its confirm button runs `onConfirm`. The dialog opens on Cancel,
 * closes on Escape and returns the focus to the trigger (R-FE-005).
 */
function ConfirmAction({
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
