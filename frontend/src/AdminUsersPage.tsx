import { useEffect, useState } from 'react';
import { api, ApiError, type AdminUserView } from './api';
import { useAuth } from './AuthContext';

export function AdminUsersPage({ onNavigateBack }: { onNavigateBack: () => void }) {
  const { username: currentUsername } = useAuth();
  const [users, setUsers] = useState<AdminUserView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);

  async function loadUsers() {
    setError(null);
    try {
      const list = await api.adminListUsers();
      setUsers(list);
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        setForbidden(true);
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not load users.');
      }
    }
  }

  useEffect(() => {
    loadUsers();
  }, []);

  async function handleToggleEnabled(user: AdminUserView) {
    setError(null);
    try {
      await api.adminUpdateStatus(user.id, !user.enabled);
      await loadUsers();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update user status.');
    }
  }

  async function handleRoleChange(user: AdminUserView) {
    const newRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN';
    setError(null);
    try {
      await api.adminUpdateRole(user.id, newRole);
      await loadUsers();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update user role.');
    }
  }

  async function handleDelete(user: AdminUserView) {
    setError(null);
    try {
      await api.adminDeleteUser(user.id);
      await loadUsers();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete user.');
    }
  }

  if (forbidden) {
    return (
      <div>
        <p role="alert">You do not have permission to view this page.</p>
        <button type="button" onClick={onNavigateBack}>
          Back
        </button>
      </div>
    );
  }

  return (
    <div>
      <h1>Manage users</h1>
      <button type="button" onClick={onNavigateBack}>
        Back
      </button>
      {error && <p role="alert">{error}</p>}
      {!users && <p>Loading…</p>}
      {users && (
        <table>
          <thead>
            <tr>
              <th>Username</th>
              <th>Email</th>
              <th>Role</th>
              <th>Enabled</th>
              <th>Created</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => {
              const isSelf = user.username === currentUsername;
              return (
                <tr key={user.id}>
                  <td>{user.username}</td>
                  <td>{user.email}</td>
                  <td>{user.role}</td>
                  <td>{user.enabled ? 'Yes' : 'No'}</td>
                  <td>{new Date(user.createdAt).toLocaleString()}</td>
                  <td>
                    {isSelf ? (
                      <span>(you)</span>
                    ) : (
                      <>
                        <button type="button" onClick={() => handleToggleEnabled(user)}>
                          {user.enabled ? 'Disable' : 'Enable'}
                        </button>
                        <button type="button" onClick={() => handleRoleChange(user)}>
                          Make {user.role === 'ADMIN' ? 'USER' : 'ADMIN'}
                        </button>
                        <button type="button" onClick={() => handleDelete(user)}>
                          Delete
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}
