import { useCallback, useEffect, useState } from 'react'
import { ApiError, api, type AdminUser, type Role, type UserPage } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { Alert } from '../components/Alert'
import { describeError, endsSession } from '../errors'

const PAGE_SIZE = 20

export function AdminUsersPage() {
  const { state } = useAuth()
  const currentUserId = state.status === 'authenticated' ? state.user.id : null
  const [page, setPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)
  const [data, setData] = useState<UserPage | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

  const handleError = useCallback((err: unknown) => {
    if (endsSession(err)) return
    setError(
      err instanceof ApiError && err.code === 'FORBIDDEN'
        ? 'You no longer have administrator access. Sign out and back in to refresh your permissions.'
        : describeError(err),
    )
  }, [])

  useEffect(() => {
    let ignore = false
    api
      .listUsers(page, PAGE_SIZE)
      .then((result) => {
        if (ignore) return
        // The last user on a later page was deleted: step back instead of showing an empty page.
        if (result.items.length === 0 && page > 0) {
          setPage(page - 1)
        } else {
          setData(result)
        }
      })
      .catch((err: unknown) => {
        if (!ignore) handleError(err)
      })
    return () => {
      ignore = true
    }
  }, [page, reloadKey, handleError])

  async function run(action: () => Promise<unknown>, user: AdminUser, success: string) {
    setBusyId(user.id)
    setError(null)
    setNotice(null)
    try {
      await action()
      setNotice(success)
      setReloadKey((key) => key + 1)
    } catch (err) {
      handleError(err)
    } finally {
      setBusyId(null)
    }
  }

  function toggleEnabled(user: AdminUser) {
    const enabled = !user.enabled
    return run(() => api.setUserEnabled(user.id, enabled), user, `${user.username} ${enabled ? 'enabled' : 'disabled'}.`)
  }

  function changeRole(user: AdminUser, role: Role) {
    return run(() => api.setUserRole(user.id, role), user, `${user.username} is now ${role}.`)
  }

  function remove(user: AdminUser) {
    if (!window.confirm(`Delete ${user.username}? This cannot be undone.`)) return
    return run(() => api.deleteUser(user.id), user, `${user.username} deleted.`)
  }

  return (
    <section className="card wide">
      <h1>Users</h1>
      {error && <Alert kind="error">{error}</Alert>}
      {notice && <Alert kind="success">{notice}</Alert>}
      {!data ? (
        !error && <p className="muted">Loading…</p>
      ) : (
        <>
          <div className="table-scroll">
            <table>
              <caption className="visually-hidden">Registered users</caption>
              <thead>
                <tr>
                  <th scope="col">Username</th>
                  <th scope="col">Email</th>
                  <th scope="col">Role</th>
                  <th scope="col">Status</th>
                  <th scope="col">Created</th>
                  <th scope="col">
                    <span className="visually-hidden">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((user) => {
                  const isSelf = user.id === currentUserId
                  const disabled = isSelf || busyId !== null
                  const selfNote = isSelf ? 'You cannot change your own account' : undefined
                  return (
                    <tr key={user.id}>
                      <th scope="row">
                        {user.username}
                        {isSelf && <span className="muted"> (you)</span>}
                      </th>
                      <td>{user.email}</td>
                      <td>
                        <select
                          aria-label={`Role for ${user.username}`}
                          value={user.role}
                          disabled={disabled}
                          title={selfNote}
                          onChange={(event) => changeRole(user, event.target.value as Role)}
                        >
                          <option value="USER">USER</option>
                          <option value="ADMIN">ADMIN</option>
                        </select>
                      </td>
                      <td>
                        <span className={user.enabled ? 'status-on' : 'status-off'}>
                          {user.enabled ? 'Enabled' : 'Disabled'}
                        </span>
                      </td>
                      <td>
                        <time dateTime={user.createdAt}>{new Date(user.createdAt).toLocaleDateString()}</time>
                      </td>
                      <td className="actions">
                        <button
                          type="button"
                          className="secondary"
                          aria-label={`${user.enabled ? 'Disable' : 'Enable'} ${user.username}`}
                          disabled={disabled}
                          title={selfNote}
                          onClick={() => toggleEnabled(user)}
                        >
                          {user.enabled ? 'Disable' : 'Enable'}
                        </button>
                        <button
                          type="button"
                          className="danger"
                          aria-label={`Delete ${user.username}`}
                          disabled={disabled}
                          title={selfNote}
                          onClick={() => remove(user)}
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <nav className="pager" aria-label="Pagination">
            <button type="button" className="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
              Previous
            </button>
            <span>
              Page {data.page + 1} of {Math.max(data.totalPages, 1)} · {data.totalItems} users
            </span>
            <button
              type="button"
              className="secondary"
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage(page + 1)}
            >
              Next
            </button>
          </nav>
        </>
      )}
    </section>
  )
}
