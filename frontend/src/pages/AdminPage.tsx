import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { Notice } from '../components/Notice';
import { api } from '../lib/api';
import type { User } from '../lib/types';

export function AdminPage() {
  const { user } = useAuth();
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    let active = true;
    api
      .get<User[]>('/admin/users')
      .then((data) => {
        if (active) setUsers(data);
      })
      .catch((caught: unknown) => {
        if (active) setError(caught instanceof Error ? caught.message : 'Unable to load users.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  async function change(target: User, action: 'status' | 'role' | 'delete') {
    if (
      action === 'delete' &&
      !window.confirm(`Delete ${target.username}? This permanently removes their account.`)
    )
      return;
    setBusy(target.id);
    setError('');
    setMessage('');
    try {
      if (action === 'delete') {
        await api.delete(`/admin/users/${target.id}`);
        setUsers((current) => current.filter((entry) => entry.id !== target.id));
        setMessage(`${target.username} was deleted.`);
      } else {
        const updated = await api.patch<User>(
          `/admin/users/${target.id}/${action}`,
          action === 'status'
            ? { enabled: !target.enabled }
            : { role: target.role === 'ADMIN' ? 'USER' : 'ADMIN' },
        );
        setUsers((current) => current.map((entry) => (entry.id === updated.id ? updated : entry)));
        setMessage(`Updated ${target.username}. They’ll need to sign in again.`);
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to update this account.');
    } finally {
      setBusy('');
    }
  }

  return (
    <section className="workspace admin-page">
      <span className="eyebrow">ADMINISTRATION</span>
      <div className="section-heading">
        <div>
          <h1>The people here.</h1>
          <p className="muted">Manage accounts, roles, and access.</p>
        </div>
        <span className="count-badge">{users.length} members</span>
      </div>
      <Notice message={error} error />
      <Notice message={message} />
      {loading ? (
        <p role="status">Loading members…</p>
      ) : (
        <div className="table-scroll">
          <table>
            <caption className="sr-only">Registered users and account management actions</caption>
            <thead>
              <tr>
                <th scope="col">Member</th>
                <th scope="col">Role</th>
                <th scope="col">Status</th>
                <th scope="col">Joined</th>
                <th scope="col">Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map((entry) => (
                <tr key={entry.id}>
                  <td>
                    <strong>
                      {entry.username}
                      {entry.id === user?.id && <span className="you-label">you</span>}
                    </strong>
                    <span className="table-email">{entry.email}</span>
                  </td>
                  <td>
                    <span className="role-label">
                      {entry.role === 'ADMIN' ? 'Admin' : 'Member'}
                    </span>
                  </td>
                  <td>
                    <span className={`account-status ${entry.enabled ? 'enabled' : ''}`}>
                      {entry.enabled ? 'Active' : 'Disabled'}
                    </span>
                  </td>
                  <td className="date-cell">
                    {new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(
                      new Date(entry.createdAt),
                    )}
                  </td>
                  <td>
                    {entry.id === user?.id ? (
                      <span className="muted">Your account</span>
                    ) : (
                      <div className="row-actions">
                        <button
                          disabled={!!busy}
                          onClick={() => void change(entry, 'status')}
                          aria-label={`${entry.enabled ? 'Disable' : 'Enable'} ${entry.username}`}
                        >
                          {entry.enabled ? 'Disable' : 'Enable'}
                        </button>
                        <button
                          disabled={!!busy}
                          onClick={() => void change(entry, 'role')}
                          aria-label={`Make ${entry.username} ${entry.role === 'ADMIN' ? 'member' : 'admin'}`}
                        >
                          {entry.role === 'ADMIN' ? 'Make member' : 'Make admin'}
                        </button>
                        <button
                          className="danger-text"
                          disabled={!!busy}
                          onClick={() => void change(entry, 'delete')}
                          aria-label={`Delete ${entry.username}`}
                        >
                          {busy === entry.id ? 'Working…' : 'Delete'}
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="table-note">
        Account changes end the member’s active sessions. You can only manage other accounts.
      </p>
    </section>
  );
}
