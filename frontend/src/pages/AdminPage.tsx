import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  type AdminUser,
  NotFoundError,
  UnauthenticatedError,
  deleteUser,
  listUsers,
  setEnabled,
  setRole,
} from '../api/adminApi';
import { messageFor } from '../api/errors';
import './AdminPage.css';

const USER_GONE_MESSAGE = 'That user no longer exists. The list has been refreshed.';

interface AdminPageProps {
  /** The signed-in admin's own username, so their own row's actions can be disabled client-side. */
  currentUsername: string;
}

export default function AdminPage({ currentUsername }: AdminPageProps) {
  const [users, setUsers] = useState<AdminUser[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [pendingId, setPendingId] = useState<number | null>(null);
  const [confirmingDeleteId, setConfirmingDeleteId] = useState<number | null>(null);
  // Bumped to re-fetch the list (e.g. after a 404 shows it is stale).
  const [reloadKey, setReloadKey] = useState(0);
  const navigate = useNavigate();

  useEffect(() => {
    let cancelled = false;

    listUsers()
      .then((fetched) => {
        if (!cancelled) {
          setUsers(fetched);
          setLoadError(null);
        }
      })
      .catch((error: unknown) => {
        if (cancelled) {
          return;
        }
        if (error instanceof UnauthenticatedError) {
          navigate('/login', { replace: true });
          return;
        }
        setLoadError(messageFor(error));
      });

    return () => {
      cancelled = true;
    };
  }, [reloadKey, navigate]);

  const handleActionError = useCallback(
    (error: unknown) => {
      if (error instanceof UnauthenticatedError) {
        navigate('/login', { replace: true });
        return;
      }
      if (error instanceof NotFoundError) {
        setActionError(USER_GONE_MESSAGE);
        setReloadKey((key) => key + 1);
        return;
      }
      setActionError(messageFor(error));
    },
    [navigate],
  );

  function replaceUser(updated: AdminUser) {
    setUsers((previous) => previous && previous.map((row) => (row.id === updated.id ? updated : row)));
  }

  async function handleToggleEnabled(row: AdminUser) {
    setActionError(null);
    setPendingId(row.id);
    try {
      const updated = await setEnabled(row.id, !row.enabled);
      replaceUser(updated);
    } catch (error) {
      handleActionError(error);
    } finally {
      setPendingId(null);
    }
  }

  async function handleToggleRole(row: AdminUser) {
    setActionError(null);
    setPendingId(row.id);
    try {
      const updated = await setRole(row.id, row.role === 'ADMIN' ? 'USER' : 'ADMIN');
      replaceUser(updated);
    } catch (error) {
      handleActionError(error);
    } finally {
      setPendingId(null);
    }
  }

  async function handleConfirmDelete(row: AdminUser) {
    setActionError(null);
    setPendingId(row.id);
    try {
      await deleteUser(row.id);
      setUsers((previous) => previous && previous.filter((user) => user.id !== row.id));
    } catch (error) {
      handleActionError(error);
    } finally {
      setPendingId(null);
      setConfirmingDeleteId(null);
    }
  }

  return (
    <main className="admin-page">
      <div className="admin-header">
        <h1>User administration</h1>
        <Link to="/">Back to home</Link>
      </div>

      {loadError && (
        <p role="alert" className="admin-banner">
          {loadError}
        </p>
      )}
      {actionError && (
        <p role="alert" className="admin-banner">
          {actionError}
        </p>
      )}

      {users && (
        <table className="admin-table">
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
            {users.map((row) => {
              const isSelf = row.username === currentUsername;
              const isPending = pendingId === row.id;
              const isConfirmingDelete = confirmingDeleteId === row.id;

              return (
                <tr key={row.id}>
                  <td>{row.username}</td>
                  <td>{row.email}</td>
                  <td>{row.role}</td>
                  <td>{row.enabled ? 'Enabled' : 'Disabled'}</td>
                  <td>{new Date(row.createdAt).toLocaleString()}</td>
                  <td className="admin-actions">
                    <button
                      type="button"
                      onClick={() => handleToggleEnabled(row)}
                      disabled={isSelf || isPending}
                    >
                      {row.enabled ? 'Disable' : 'Enable'}
                    </button>
                    <button type="button" onClick={() => handleToggleRole(row)} disabled={isSelf || isPending}>
                      Make {row.role === 'ADMIN' ? 'USER' : 'ADMIN'}
                    </button>
                    {isConfirmingDelete ? (
                      <span className="admin-confirm-delete">
                        <button type="button" onClick={() => handleConfirmDelete(row)} disabled={isPending}>
                          Confirm delete
                        </button>
                        <button type="button" onClick={() => setConfirmingDeleteId(null)} disabled={isPending}>
                          Cancel
                        </button>
                      </span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setConfirmingDeleteId(row.id)}
                        disabled={isSelf || isPending}
                      >
                        Delete
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </main>
  );
}
