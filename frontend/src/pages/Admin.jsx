import { useCallback, useEffect, useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../AuthContext.jsx';

export default function Admin() {
  const { user: me } = useAuth();
  const [users, setUsers] = useState([]);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    api.admin.list().then(setUsers).catch((e) => setError(e.message));
  }, []);
  useEffect(load, [load]);

  const act = (fn) => async () => {
    setError('');
    try {
      await fn();
      load();
    } catch (e) {
      setError(e.message);
    }
  };

  return (
    <>
      <h1>Users</h1>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="card">
      <table>
        <thead>
          <tr><th>Username</th><th>Email</th><th>Role</th><th>Enabled</th><th>Created</th><th /></tr>
        </thead>
        <tbody>
          {users.map((u) => {
            const self = u.username === me.username;
            return (
              <tr key={u.id}>
                <td>{u.username}</td>
                <td>{u.email}</td>
                <td><span className={`badge ${u.role.toLowerCase()}`}>{u.role}</span></td>
                <td><span className={`badge ${u.enabled ? 'on' : 'off'}`}>{u.enabled ? 'Active' : 'Disabled'}</span></td>
                <td>{new Date(u.createdAt).toLocaleString()}</td>
                <td className="actions">
                  <button disabled={self} onClick={act(() => api.admin.setEnabled(u.id, !u.enabled))}>
                    {u.enabled ? 'Disable' : 'Enable'}
                  </button>
                  <button disabled={self}
                    onClick={act(() => api.admin.setRole(u.id, u.role === 'ADMIN' ? 'USER' : 'ADMIN'))}>
                    Make {u.role === 'ADMIN' ? 'USER' : 'ADMIN'}
                  </button>
                  <button className="danger" disabled={self} onClick={() => {
                    if (window.confirm(`Delete ${u.username}?`)) act(() => api.admin.remove(u.id))();
                  }}>
                    Delete
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      </div>
    </>
  );
}
