import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useAuth } from '../context/AuthContext'

interface AdminUserView {
  id: number
  username: string
  email: string
  role: 'USER' | 'ADMIN'
  enabled: boolean
  createdAt: string
  lastLoginAt: string | null
}

/**
 * Admin-only account management (PRD Stories 8-11). Controls for the row matching the current
 * user are disabled client-side as a UX nicety only — the real enforcement of "an admin cannot
 * act on their own account" lives server-side in AdminUserService.requireNotSelf.
 */
export default function AdminUsersPage() {
  const { username: currentUsername } = useAuth()
  const [users, setUsers] = useState<AdminUserView[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    loadUsers()
  }, [])

  async function loadUsers() {
    setLoading(true)
    try {
      const result = await api.get<AdminUserView[]>('/api/admin/users')
      setUsers(result)
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load users')
    } finally {
      setLoading(false)
    }
  }

  async function handleToggleEnabled(user: AdminUserView) {
    setError(null)
    try {
      await api.patch(`/api/admin/users/${user.id}/status`, { enabled: !user.enabled })
      await loadUsers()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to update status')
    }
  }

  async function handleRoleChange(user: AdminUserView, role: 'USER' | 'ADMIN') {
    setError(null)
    try {
      await api.patch(`/api/admin/users/${user.id}/role`, { role })
      await loadUsers()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to update role')
    }
  }

  async function handleDelete(user: AdminUserView) {
    setError(null)
    try {
      await api.delete(`/api/admin/users/${user.id}`)
      await loadUsers()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to delete user')
    }
  }

  if (loading) {
    return <p>Loading…</p>
  }

  return (
    <main>
      <h1>User management</h1>
      <p>
        <Link to="/hello">Back</Link>
      </p>
      {error && (
        <p role="alert" style={{ color: 'crimson' }}>
          {error}
        </p>
      )}
      <table>
        <thead>
          <tr>
            <th>Username</th>
            <th>Email</th>
            <th>Role</th>
            <th>Enabled</th>
            <th>Created at</th>
            <th>Last login</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {users.map((user) => {
            const isSelf = user.username === currentUsername
            return (
              <tr key={user.id}>
                <td>{user.username}</td>
                <td>{user.email}</td>
                <td>{user.role}</td>
                <td>{user.enabled ? 'Yes' : 'No'}</td>
                <td>{new Date(user.createdAt).toLocaleString()}</td>
                <td>{user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString() : 'Never'}</td>
                <td>
                  <button disabled={isSelf} onClick={() => handleToggleEnabled(user)}>
                    {user.enabled ? 'Disable' : 'Enable'}
                  </button>
                  <button
                    disabled={isSelf}
                    onClick={() => handleRoleChange(user, user.role === 'ADMIN' ? 'USER' : 'ADMIN')}
                  >
                    Make {user.role === 'ADMIN' ? 'USER' : 'ADMIN'}
                  </button>
                  <button disabled={isSelf} onClick={() => handleDelete(user)}>
                    Delete
                  </button>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </main>
  )
}
