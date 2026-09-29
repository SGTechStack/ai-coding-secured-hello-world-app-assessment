import { useQuery } from '@tanstack/react-query'
import { Link, Navigate, useParams } from 'react-router'
import type { ErrorCode } from '@/lib/api/errors'
import { adminUserKey, fetchAdminUser } from '@/lib/admin/users'
import { authorityRoute } from '@/lib/auth/authority'

/** An unknown id is also refused with `ACCESS_DENIED`, so that code is shown here as not found, not followed. */
const OWN_CODES: readonly ErrorCode[] = ['ACCESS_DENIED']

/**
 * `/admin/users/:id`: one account (PRD Story 8). Like the list, it renders only what the server returned, and a
 * refusal's code sends the session where it belongs.
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
      )}
      <Link to="/admin/users" className="text-sm underline">
        Back to the user list
      </Link>
    </section>
  )
}
