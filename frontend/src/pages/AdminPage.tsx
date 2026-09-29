import { useEffect, useState } from 'react';
import { listUsers, setUserStatus, changeUserRole, deleteUser } from '../api/admin';
import { ApiError } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import type { Role, UserResponse } from '../api/types';

export function AdminPage() {
  const { user: me } = useAuth();
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionError, setActionError] = useState<string | null>(null);

  const refresh = () => {
    setLoading(true);
    listUsers()
      .then((data) => { setUsers(data); setError(null); })
      .catch((err: unknown) => {
        if (err instanceof ApiError) {
          setError(err.detail);
        } else {
          setError('Failed to load users.');
        }
      })
      .finally(() => setLoading(false));
  };

  useEffect(refresh, []);

  const handleSetStatus = async (id: string, enabled: boolean) => {
    setActionError(null);
    try {
      const updated = await setUserStatus(id, enabled);
      setUsers((prev) => prev.map((u) => (u.id === id ? updated : u)));
    } catch (err) {
      if (err instanceof ApiError) {
        setActionError(err.detail);
      } else {
        setActionError('Action failed.');
      }
    }
  };

  const handleChangeRole = async (id: string, role: Role) => {
    setActionError(null);
    try {
      const updated = await changeUserRole(id, role);
      setUsers((prev) => prev.map((u) => (u.id === id ? updated : u)));
    } catch (err) {
      if (err instanceof ApiError) {
        setActionError(err.detail);
      } else {
        setActionError('Role change failed.');
      }
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Delete this user? This cannot be undone.')) return;
    setActionError(null);
    try {
      await deleteUser(id);
      setUsers((prev) => prev.filter((u) => u.id !== id));
    } catch (err) {
      if (err instanceof ApiError) {
        setActionError(err.detail);
      } else {
        setActionError('Delete failed.');
      }
    }
  };

  if (loading) return <p>Loading users…</p>;
  if (error) return <p role="alert" style={{ color: 'red' }}>{error}</p>;

  return (
    <div>
      <h1>User management</h1>
      {actionError && <p role="alert" style={{ color: 'red', border: '1px solid red', padding: '0.5rem' }}>{actionError}</p>}
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
        <thead>
          <tr style={{ background: '#f5f5f5' }}>
            <th style={th}>Username</th>
            <th style={th}>Email</th>
            <th style={th}>Role</th>
            <th style={th}>Enabled</th>
            <th style={th}>Created</th>
            <th style={th}>Actions</th>
          </tr>
        </thead>
        <tbody>
          {users.map((u) => {
            const isSelf = u.id === me?.id;
            return (
              <tr key={u.id}>
                <td style={td}>{u.username}</td>
                <td style={td}>{u.email}</td>
                <td style={td}>{u.role}</td>
                <td style={td}>{u.enabled ? 'Yes' : 'No'}</td>
                <td style={td}>{new Date(u.createdAt).toLocaleDateString()}</td>
                <td style={td}>
                  {!isSelf && (
                    <>
                      <button
                        aria-label={u.enabled ? `Disable ${u.username}` : `Enable ${u.username}`}
                        onClick={() => { void handleSetStatus(u.id, !u.enabled); }}
                        style={{ marginRight: '0.3rem' }}
                      >
                        {u.enabled ? 'Disable' : 'Enable'}
                      </button>
                      <button
                        aria-label={`Change role of ${u.username}`}
                        onClick={() => { void handleChangeRole(u.id, u.role === 'ADMIN' ? 'USER' : 'ADMIN'); }}
                        style={{ marginRight: '0.3rem' }}
                      >
                        {u.role === 'ADMIN' ? '→ USER' : '→ ADMIN'}
                      </button>
                      <button
                        aria-label={`Delete ${u.username}`}
                        onClick={() => { void handleDelete(u.id); }}
                        style={{ color: 'red' }}
                      >
                        Delete
                      </button>
                    </>
                  )}
                  {isSelf && <span style={{ color: '#888' }}>—</span>}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

const th: React.CSSProperties = { padding: '0.4rem', textAlign: 'left', borderBottom: '1px solid #ccc' };
const td: React.CSSProperties = { padding: '0.4rem', borderBottom: '1px solid #eee', verticalAlign: 'middle' };
