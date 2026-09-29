import { useRef, useState } from 'react'
import { Link, useLoaderData } from 'react-router'
import type { Role } from '../../shared/api/role.ts'
import {
  type AdminUser,
  type MutationResult,
  deleteUser,
  setUserEnabled,
  setUserRole,
} from './api.ts'
import { TopBar } from '../../shared/ui/TopBar.tsx'
import { ConfirmDialog } from './ConfirmDialog.tsx'
import type { adminUsersLoader } from './loaders.ts'
import './AdminUsersPage.css'

const ROLES: Role[] = ['USER', 'ADMIN']

export function AdminUsersPage() {
  const { currentUser, users: initialUsers } = useLoaderData<typeof adminUsersLoader>()
  const [users, setUsers] = useState(initialUsers)
  const [error, setError] = useState<string>()
  const [pendingId, setPendingId] = useState<string>()
  const [confirmingDelete, setConfirmingDelete] = useState<AdminUser>()
  const deleteTrigger = useRef<HTMLButtonElement | null>(null)

  async function run<T>(
    id: string,
    action: () => Promise<MutationResult<T>>,
    apply: (value: T) => void,
  ) {
    setPendingId(id)
    setError(undefined)
    const result = await action()
    if (result.status === 'ok') apply(result.value)
    else setError(result.message)
    setPendingId(undefined)
  }

  function replace(updated: AdminUser) {
    setUsers((current) => current.map((user) => (user.id === updated.id ? updated : user)))
  }

  function closeDeleteDialog() {
    setConfirmingDelete(undefined)
    deleteTrigger.current?.focus()
  }

  async function confirmDelete(user: AdminUser) {
    setConfirmingDelete(undefined)
    await run(
      user.id,
      () => deleteUser(user.id),
      (id) => setUsers((current) => current.filter((candidate) => candidate.id !== id)),
    )
  }

  const enabledCount = users.filter((user) => user.enabled).length

  return (
    <div className="app-page">
      <TopBar>
        <Link className="btn btn--ghost btn--small" to="/">
          Back to home
        </Link>
      </TopBar>
      <main className="app-main">
        <div className="admin-heading rise-in">
          <p className="eyebrow">Administration</p>
          <h1>Manage users</h1>
          <p className="lede">
            {users.length} accounts, {enabledCount} enabled
          </p>
        </div>
        {error ? (
          <div role="alert" className="notice notice--error">
            {error}
          </div>
        ) : null}
        <div className="table-panel panel rise-in">
          <table className="data-table">
            <thead>
              <tr>
                <th scope="col">Username</th>
                <th scope="col">Email</th>
                <th scope="col">Role</th>
                <th scope="col">Status</th>
                <th scope="col">Created</th>
                <th scope="col">Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => {
                const isSelf = user.username === currentUser.username
                const busy = pendingId === user.id
                return (
                  <tr key={user.id} className="data-row" aria-busy={busy}>
                    <th scope="row">
                      <span className="user-cell">
                        <span className="avatar" aria-hidden="true">
                          {user.username.slice(0, 1)}
                        </span>
                        {user.username}
                      </span>
                    </th>
                    <td className="cell-muted">{user.email}</td>
                    <td className="cell-role" data-role={user.role}>
                      {user.role}
                    </td>
                    <td className={user.enabled ? 'cell-status is-enabled' : 'cell-status'}>
                      {user.enabled ? 'Enabled' : 'Disabled'}
                    </td>
                    <td className="cell-muted cell-date">{user.createdAt.slice(0, 10)}</td>
                    <td>
                      {isSelf ? (
                        <span className="self-note">You</span>
                      ) : (
                        <div className="row-actions">
                          <button
                            type="button"
                            className="btn btn--ghost btn--small"
                            disabled={busy}
                            onClick={() =>
                              void run(
                                user.id,
                                () => setUserEnabled(user.id, !user.enabled),
                                replace,
                              )
                            }
                          >
                            {user.enabled ? 'Disable' : 'Enable'}
                          </button>
                          <label className="visually-hidden" htmlFor={`role-${user.id}`}>
                            Role for {user.username}
                          </label>
                          <select
                            id={`role-${user.id}`}
                            value={user.role}
                            disabled={busy}
                            onChange={(event) => {
                              const role = event.currentTarget.value as Role
                              void run(user.id, () => setUserRole(user.id, role), replace)
                            }}
                          >
                            {ROLES.map((role) => (
                              <option key={role} value={role}>
                                {role}
                              </option>
                            ))}
                          </select>
                          <button
                            type="button"
                            className="btn btn--small btn--danger-ghost"
                            disabled={busy}
                            onClick={(event) => {
                              deleteTrigger.current = event.currentTarget
                              setConfirmingDelete(user)
                            }}
                          >
                            Delete
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        {confirmingDelete ? (
          <ConfirmDialog
            message={`Delete user ${confirmingDelete.username}? This cannot be undone.`}
            confirmLabel="Delete"
            onConfirm={() => void confirmDelete(confirmingDelete)}
            onCancel={closeDeleteDialog}
          />
        ) : null}
      </main>
    </div>
  )
}
