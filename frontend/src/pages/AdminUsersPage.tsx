import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, useNavigate } from 'react-router'
import { Button } from '@/components/ui/button'
import { signInBadge } from '@/lib/admin/signInStatus'
import { ADMIN_USERS_KEY, fetchAdminUsers } from '@/lib/admin/users'
import { authorityRoute } from '@/lib/auth/authority'
import { signOut } from '@/lib/auth/session'

/**
 * `/admin/users`: the user list (PRD Story 8). Rendered only from the server's answer: without a current factor the
 * server refuses it, and the refusal's code sends the session to the challenge, enrolment or sign-in (T-FE-002). Each
 * username is a link, so the table is walked with Tab and opened with Enter.
 */
export function AdminUsersPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const users = useQuery({ queryKey: ADMIN_USERS_KEY, queryFn: fetchAdminUsers })

  const leaving = authorityRoute(users.error)
  if (leaving) {
    return <Navigate to={leaving} replace />
  }

  const onSignOut = async () => {
    await signOut(queryClient)
    await navigate('/sign-in', { replace: true })
  }

  return (
    <section aria-labelledby="users-heading" className="mt-6 flex flex-col gap-4">
      <h2 id="users-heading" className="text-xl font-semibold">
        Users
      </h2>
      {users.isPending && <p className="text-muted-foreground">Loading…</p>}
      {users.error && (
        <p role="alert" className="text-sm text-destructive">
          The user list could not be loaded.
        </p>
      )}
      {/* Never stale rows beside a failure: a refetch error hides the table. */}
      {users.data && !users.error && (
        <table className="w-full text-left text-sm">
          <caption className="sr-only">Every account, by username</caption>
          <thead>
            <tr>
              <th scope="col">Username</th>
              <th scope="col">Email</th>
              <th scope="col">Role</th>
              <th scope="col">Status</th>
              <th scope="col">Sign-in</th>
              <th scope="col">Created</th>
            </tr>
          </thead>
          <tbody>
            {users.data.map((user) => (
              <tr key={user.id}>
                <th scope="row" className="font-normal">
                  <Link to={`/admin/users/${user.id}`} className="underline">
                    {user.username}
                  </Link>
                </th>
                <td>{user.email}</td>
                <td>{user.role}</td>
                <td>{user.enabled ? 'Enabled' : 'Disabled'}</td>
                <td>{signInBadge(user.signInStatus)}</td>
                <td>
                  <time dateTime={user.createdAt}>{user.createdAt.slice(0, 10)}</time>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <Link to="/admin/users/invite" className="text-sm underline">
        Invite a user
      </Link>
      <Link to="/change-password" className="text-sm underline">
        Change password
      </Link>
      <div>
        <Button variant="outline" onClick={onSignOut}>
          Sign out
        </Button>
      </div>
    </section>
  )
}
