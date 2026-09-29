import { useEffect, useState } from 'react';
import { deleteUser, listUsers, setUserStatus, changeUserRole } from '../api/admin';
import { ApiError } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import type { Role, UserResponse } from '../api/types';

export function AdminPage() {
  const { user: me } = useAuth();
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [pageError, setPageError] = useState<string | null>(null);
  const [pageLoading, setPageLoading] = useState(true);
  const [actionError, setActionError] = useState<string | null>(null);
  const [loadingId, setLoadingId] = useState<string | null>(null);
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null);

  const refresh = () => {
    setPageLoading(true);
    listUsers()
      .then((data) => { setUsers(data); setPageError(null); })
      .catch((err: unknown) => {
        setPageError(err instanceof ApiError ? err.detail : 'Failed to load users.');
      })
      .finally(() => setPageLoading(false));
  };

  useEffect(refresh, []);

  const handleSetStatus = async (id: string, enabled: boolean) => {
    setActionError(null);
    setLoadingId(id);
    try {
      const updated = await setUserStatus(id, enabled);
      setUsers((prev) => prev.map((u) => (u.id === id ? updated : u)));
    } catch (err) {
      setActionError(err instanceof ApiError ? err.detail : 'Action failed.');
    } finally {
      setLoadingId(null);
    }
  };

  const handleChangeRole = async (id: string, role: Role) => {
    setActionError(null);
    setLoadingId(id);
    try {
      const updated = await changeUserRole(id, role);
      setUsers((prev) => prev.map((u) => (u.id === id ? updated : u)));
    } catch (err) {
      setActionError(err instanceof ApiError ? err.detail : 'Role change failed.');
    } finally {
      setLoadingId(null);
    }
  };

  const handleDelete = async (id: string) => {
    setActionError(null);
    setLoadingId(id);
    setDeleteConfirmId(null);
    try {
      await deleteUser(id);
      setUsers((prev) => prev.filter((u) => u.id !== id));
    } catch (err) {
      setActionError(err instanceof ApiError ? err.detail : 'Delete failed.');
    } finally {
      setLoadingId(null);
    }
  };

  if (pageLoading) {
    return (
      <div className="loading-page" aria-label="Loading users">
        <div className="loading-spinner" aria-hidden="true" />
        Loading users…
      </div>
    );
  }

  if (pageError) {
    return (
      <div role="alert" className="form-banner form-banner-error" style={{ maxWidth: 600 }}>
        <span aria-hidden="true">✕ </span>{pageError}
      </div>
    );
  }

  return (
    <div>
      <h1 className="page-title">User management</h1>

      {actionError && (
        <div role="alert" className="form-banner form-banner-error" style={{ marginBottom: '1.25rem' }}>
          <span aria-hidden="true">✕ </span>{actionError}
          <button
            className="btn btn-sm"
            style={{ marginLeft: 'auto', background: 'none', border: 'none', cursor: 'pointer', color: 'inherit', padding: '0 0.25rem' }}
            onClick={() => setActionError(null)}
            aria-label="Dismiss error"
          >✕</button>
        </div>
      )}

      {users.length === 0 ? (
        <div className="table-wrapper">
          <div className="empty-state">
            <div className="empty-state-icon" aria-hidden="true">👤</div>
            No users found.
          </div>
        </div>
      ) : (
        <div className="table-wrapper">
          <table className="data-table" aria-label="Users">
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
              {users.map((u) => {
                const isSelf = u.id === me?.id;
                const rowLoading = loadingId === u.id;
                return (
                  <tr key={u.id} aria-busy={rowLoading}>
                    <td style={{ fontWeight: 600 }}>{u.username}</td>
                    <td style={{ color: 'var(--color-text-muted)' }}>{u.email}</td>
                    <td>
                      <span className={`badge ${u.role === 'ADMIN' ? 'badge-admin' : 'badge-user'}`}>
                        {u.role}
                      </span>
                    </td>
                    <td>
                      <span className={`badge ${u.enabled ? 'badge-enabled' : 'badge-disabled'}`}>
                        {u.enabled ? 'Active' : 'Disabled'}
                      </span>
                    </td>
                    <td style={{ color: 'var(--color-text-muted)' }}>
                      {new Date(u.createdAt).toLocaleDateString()}
                    </td>
                    <td>
                      {isSelf ? (
                        <span style={{ color: 'var(--color-text-muted)', fontSize: 'var(--font-size-sm)' }}>—</span>
                      ) : (
                        <div className="table-actions">
                          {deleteConfirmId === u.id ? (
                            <div className="delete-confirm">
                              <span className="delete-confirm-text">Delete {u.username}?</span>
                              <button
                                className="btn btn-danger btn-sm"
                                disabled={rowLoading}
                                onClick={() => { void handleDelete(u.id); }}
                              >
                                {rowLoading ? <span className="spinner spinner-dark" aria-hidden="true" /> : null}
                                Confirm
                              </button>
                              <button
                                className="btn btn-secondary btn-sm"
                                onClick={() => setDeleteConfirmId(null)}
                              >
                                Cancel
                              </button>
                            </div>
                          ) : (
                            <>
                              <button
                                className="btn btn-secondary btn-sm"
                                disabled={rowLoading}
                                aria-label={u.enabled ? `Disable ${u.username}` : `Enable ${u.username}`}
                                onClick={() => { void handleSetStatus(u.id, !u.enabled); }}
                              >
                                {rowLoading ? <span className="spinner spinner-dark" aria-hidden="true" /> : null}
                                {u.enabled ? 'Disable' : 'Enable'}
                              </button>
                              <button
                                className="btn btn-secondary btn-sm"
                                disabled={rowLoading}
                                aria-label={`Change role of ${u.username}`}
                                onClick={() => { void handleChangeRole(u.id, u.role === 'ADMIN' ? 'USER' : 'ADMIN'); }}
                              >
                                {u.role === 'ADMIN' ? '→ USER' : '→ ADMIN'}
                              </button>
                              <button
                                className="btn btn-danger btn-sm"
                                disabled={rowLoading}
                                aria-label={`Delete ${u.username}`}
                                onClick={() => setDeleteConfirmId(u.id)}
                              >
                                Delete
                              </button>
                            </>
                          )}
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
