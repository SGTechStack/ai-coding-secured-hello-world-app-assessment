import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { listUsers, type UserSummary } from '../../api/endpoints'
import { ErrorBanner } from '../../components/Feedback'
import { describeError } from '../../components/errorText'

/**
 * The user list (story 1.14).
 *
 * It renders exactly the fields the list projection returns and no more. The detail projection carries
 * lock state and the failed-attempt count; this one does not, and adding a "locked?" column here would
 * mean widening the list payload — which is the erosion `Std:414` is about.
 *
 * No tombstone filter, because deleted rows are genuinely gone from `users`.
 */
export function UserListPage(): ReactNode {
  const [users, setUsers] = useState<UserSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setUsers(await listUsers())
    } catch (failure) {
      setError(describeError(failure, 'The user list could not be loaded.'))
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <section className="card">
      <h1>Users</h1>
      <ErrorBanner message={error} />
      <p className="muted">
        <Link to="/admin/users/new">Create a user</Link>
      </p>
      {users === null ? (
        <p className="muted">Loading…</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Username</th>
              <th>Email</th>
              <th>Role</th>
              <th>Status</th>
              <th>Created</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.id}>
                <td>{user.username}</td>
                <td>{user.email}</td>
                <td>{user.role}</td>
                <td>{user.enabled ? 'enabled' : 'disabled'}</td>
                <td>{user.createdAt}</td>
                <td>
                  <Link to={`/admin/users/${user.id}`}>Manage</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
