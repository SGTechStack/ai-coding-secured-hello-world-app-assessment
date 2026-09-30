import { useEffect, useState } from 'react';
import { api } from '../api.js';

export default function Admin({ currentUser }) {
  const [users, setUsers] = useState([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [forbidden, setForbidden] = useState(false);

  async function load() {
    setError('');
    try {
      const list = await api.get('/api/admin/users');
      setUsers(list);
      setForbidden(false);
    } catch (err) {
      if (err.status === 403) {
        setForbidden(true);
      } else {
        setError(err.message);
      }
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function toggleEnabled(u) {
    setNotice('');
    try {
      await api.put(`/api/admin/users/${u.id}/status?enabled=${!u.enabled}`);
      setNotice(`${u.username} is now ${!u.enabled ? 'enabled' : 'disabled'}.`);
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function changeRole(u) {
    setNotice('');
    const nextRole = u.role === 'ADMIN' ? 'USER' : 'ADMIN';
    try {
      await api.put(`/api/admin/users/${u.id}/role`, { role: nextRole });
      setNotice(`${u.username} is now ${nextRole}.`);
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function remove(u) {
    setNotice('');
    try {
      await api.del(`/api/admin/users/${u.id}`);
      setNotice(`${u.username} deleted.`);
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  if (forbidden) {
    return (
      <div className="ring">
        <h2>🦁 Admin Ring</h2>
        <div className="flash err">
          🚫 Forbidden — only ringmasters (ADMINs) may enter. You're logged in as a regular performer.
        </div>
      </div>
    );
  }

  return (
    <div className="ring wide">
      <h2>🦁 Admin Ring — Manage the Troupe</h2>
      {error && <div className="flash err">{error}</div>}
      {notice && <div className="flash ok">{notice}</div>}
      <table>
        <thead>
          <tr>
            <th>Username</th>
            <th>Email</th>
            <th>Role</th>
            <th>Status</th>
            <th>Joined</th>
            <th>Acts</th>
          </tr>
        </thead>
        <tbody>
          {users.map((u) => {
            const isSelf = u.username === currentUser.username;
            return (
              <tr key={u.id}>
                <td>{u.username}{isSelf && ' (you)'}</td>
                <td>{u.email}</td>
                <td><span className={`badge ${u.role === 'ADMIN' ? 'admin' : 'user'}`}>{u.role}</span></td>
                <td><span className={`badge ${u.enabled ? 'on' : 'off'}`}>{u.enabled ? 'ENABLED' : 'DISABLED'}</span></td>
                <td>{new Date(u.createdAt).toLocaleDateString()}</td>
                <td>
                  <button className="mini cool" disabled={isSelf} onClick={() => toggleEnabled(u)}>
                    {u.enabled ? 'Disable' : 'Enable'}
                  </button>
                  <button className="mini" disabled={isSelf} onClick={() => changeRole(u)}>
                    → {u.role === 'ADMIN' ? 'USER' : 'ADMIN'}
                  </button>
                  <button className="mini danger" disabled={isSelf} onClick={() => remove(u)}>
                    Delete
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <p className="hint">You can't disable, demote, or delete your own account — the ringmaster stays.</p>
    </div>
  );
}
