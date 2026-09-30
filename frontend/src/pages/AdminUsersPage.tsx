import { useEffect, useState } from 'react';
import {
  ApiError,
  adminDeleteUser,
  adminListUsers,
  adminSetEnabled,
  adminSetRole,
  type Role,
  type UserSummary,
} from '../api/client';
import { useAuth } from '../context/AuthContext';

export default function AdminUsersPage() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState<UserSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  useEffect(() => {
    void loadUsers();
  }, []);

  async function loadUsers() {
    try {
      setUsers(await adminListUsers());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load users.');
    }
  }

  async function handleToggleEnabled(target: UserSummary) {
    setError(null);
    setBusyId(target.id);
    try {
      await adminSetEnabled(target.id, !target.enabled);
      setUsers((prev) =>
        prev?.map((entry) => (entry.id === target.id ? { ...entry, enabled: !target.enabled } : entry)) ?? null,
      );
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to update user.');
    } finally {
      setBusyId(null);
    }
  }

  async function handleChangeRole(target: UserSummary, role: Role) {
    setError(null);
    setBusyId(target.id);
    try {
      await adminSetRole(target.id, role);
      setUsers((prev) => prev?.map((entry) => (entry.id === target.id ? { ...entry, role } : entry)) ?? null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to update user.');
    } finally {
      setBusyId(null);
    }
  }

  async function handleDelete(target: UserSummary) {
    if (!window.confirm(`Delete user "${target.username}"? This cannot be undone.`)) {
      return;
    }
    setError(null);
    setBusyId(target.id);
    try {
      await adminDeleteUser(target.id);
      setUsers((prev) => prev?.filter((entry) => entry.id !== target.id) ?? null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to delete user.');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section>
      <h2>User management</h2>
      {error && <p role="alert">{error}</p>}
      {!users ? (
        <p>Loading users…</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Username</th>
              <th>Email</th>
              <th>Role</th>
              <th>Status</th>
              <th>Created</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.map((target) => {
              const isSelf = target.username === currentUser?.username;
              const busy = busyId === target.id;
              return (
                <tr key={target.id}>
                  <td>{target.username}</td>
                  <td>{target.email}</td>
                  <td>{target.role}</td>
                  <td>{target.enabled ? 'Enabled' : 'Disabled'}</td>
                  <td>{target.createdAt}</td>
                  <td>
                    <button
                      type="button"
                      disabled={isSelf || busy}
                      onClick={() => void handleToggleEnabled(target)}
                    >
                      {target.enabled ? 'Disable' : 'Enable'}
                    </button>
                    <button
                      type="button"
                      disabled={isSelf || busy}
                      onClick={() => void handleChangeRole(target, target.role === 'ADMIN' ? 'USER' : 'ADMIN')}
                    >
                      Make {target.role === 'ADMIN' ? 'User' : 'Admin'}
                    </button>
                    <button type="button" disabled={isSelf || busy} onClick={() => void handleDelete(target)}>
                      Delete
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </section>
  );
}
