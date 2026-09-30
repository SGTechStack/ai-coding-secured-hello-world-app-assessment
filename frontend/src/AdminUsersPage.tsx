import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError, type AdminUserView } from './api';
import { useAuth } from './AuthContext';
import { Icon } from './Icon';

const createdDate = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' });
const createdFull = new Intl.DateTimeFormat(undefined, { dateStyle: 'full', timeStyle: 'long' });

const SKELETON_ROWS = 3;

/**
 * Actions consequential enough to need a second, in-row click:
 * - delete: irreversible.
 * - grant: privilege escalation (USER -> ADMIN). Demotion, enable and
 *   disable stay one click; they are reversible and reduce access.
 */
type ConfirmKind = 'delete' | 'grant';

export function AdminUsersPage({ onNavigateBack }: { onNavigateBack: () => void }) {
  const { username: currentUsername } = useAuth();
  const [users, setUsers] = useState<AdminUserView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);
  // Row currently waiting on a mutation; its controls are locked so a
  // double-click can't fire the same PATCH/DELETE twice.
  const [pendingId, setPendingId] = useState<number | null>(null);
  // Polite (non-alert) confirmation of the last successful action, so the
  // reviewer sees — and screen readers hear — that the change landed.
  const [notice, setNotice] = useState<string | null>(null);
  // Only one row can be mid-confirmation at a time.
  const [confirming, setConfirming] = useState<{ id: number; kind: ConfirmKind } | null>(null);

  // Mobile: the table scrolls sideways; show a hint until the user reaches the end.
  const scrollRef = useRef<HTMLDivElement>(null);
  const [moreToRight, setMoreToRight] = useState(false);
  const measureScroll = useCallback(() => {
    const el = scrollRef.current;
    if (el) setMoreToRight(el.scrollWidth - el.clientWidth - el.scrollLeft > 4);
  }, []);

  async function loadUsers() {
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

  useEffect(() => {
    measureScroll();
    window.addEventListener('resize', measureScroll);
    return () => window.removeEventListener('resize', measureScroll);
  }, [measureScroll, users, confirming]);

  async function runAction(user: AdminUserView, action: () => Promise<void>, success: string, failure: string) {
    setError(null);
    setNotice(null);
    setPendingId(user.id);
    try {
      await action();
      await loadUsers();
      setNotice(success);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : failure);
    } finally {
      setPendingId(null);
    }
  }

  function handleToggleEnabled(user: AdminUserView) {
    return runAction(
      user,
      () => api.adminUpdateStatus(user.id, !user.enabled),
      `${user.username} ${user.enabled ? 'disabled' : 'enabled'}.`,
      'Could not update user status.',
    );
  }

  function setRole(user: AdminUserView, newRole: 'USER' | 'ADMIN') {
    setConfirming(null);
    return runAction(
      user,
      () => api.adminUpdateRole(user.id, newRole),
      `${user.username} is now ${newRole}.`,
      'Could not update user role.',
    );
  }

  function handleDelete(user: AdminUserView) {
    setConfirming(null);
    return runAction(user, () => api.adminDeleteUser(user.id), `${user.username} deleted.`, 'Could not delete user.');
  }

  function requestConfirm(user: AdminUserView, kind: ConfirmKind) {
    setNotice(null);
    setConfirming({ id: user.id, kind });
    // Land keyboard focus on the safe choice (Cancel), not on the confirm.
    requestAnimationFrame(() => {
      const cancel = document.getElementById(`cancel-${user.id}`);
      cancel?.focus({ preventScroll: true });
      // On narrow screens the table scrolls sideways; bring both choices into view.
      cancel?.parentElement?.scrollIntoView({ block: 'nearest', inline: 'end' });
    });
  }

  function cancelConfirm(user: AdminUserView, kind: ConfirmKind) {
    setConfirming(null);
    // Return focus to the button that opened the confirmation, which re-renders in place.
    requestAnimationFrame(() => document.getElementById(`${kind}-${user.id}`)?.focus());
  }

  if (forbidden) {
    return (
      <div className="card denied">
        <span className="denied__mark">
          <Icon name="lock" />
        </span>
        <h1>Access denied</h1>
        <p className="denied__body" role="alert">
          You do not have permission to view this page.
        </p>
        <p className="denied__detail">
          Managing users requires the <span className="badge badge--admin">ADMIN</span> role. The server
          checked your current role and refused the request.
        </p>
        <div className="denied__foot">
          <button type="button" className="btn btn-secondary btn-sm" onClick={onNavigateBack}>
            <Icon name="arrowLeft" />
            Back
          </button>
        </div>
      </div>
    );
  }

  const adminCount = users?.filter((u) => u.role === 'ADMIN').length ?? 0;
  const disabledCount = users?.filter((u) => !u.enabled).length ?? 0;

  return (
    <div className="console">
      <div className="console__nav">
        <button type="button" className="btn btn-ghost btn-sm" onClick={onNavigateBack}>
          <Icon name="arrowLeft" />
          Back
        </button>
      </div>

      <header className="console__head">
        <div>
          <h1>Manage users</h1>
          <p className="console__meta">
            {users ? (
              <>
                <span className="num">{users.length}</span> {users.length === 1 ? 'account' : 'accounts'}
                <span className="sep" aria-hidden="true" />
                <span className="num">{adminCount}</span> {adminCount === 1 ? 'admin' : 'admins'}
                <span className="sep" aria-hidden="true" />
                <span className="num">{disabledCount}</span> disabled
              </>
            ) : (
              'Loading accounts…'
            )}
          </p>
        </div>
        <p className="console__notice" aria-live="polite">
          {notice && (
            <>
              <Icon name="check" />
              {notice}
            </>
          )}
        </p>
      </header>

      {error && (
        <p className="alert alert--table" role="alert">
          {error}
        </p>
      )}

      <div className="table-frame" data-more={moreToRight || undefined}>
        <div className="table-scroll" ref={scrollRef} onScroll={measureScroll}>
          <table className="data-table" aria-busy={!users || undefined}>
            <thead>
              <tr>
                <th scope="col">Username</th>
                <th scope="col">Email</th>
                <th scope="col">Role</th>
                <th scope="col">Enabled</th>
                <th scope="col">Created</th>
                <th scope="col" className="col-actions">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {!users &&
                Array.from({ length: SKELETON_ROWS }, (_, i) => (
                  <tr key={i} className="row-skeleton" aria-hidden="true">
                    {Array.from({ length: 6 }, (_, j) => (
                      <td key={j}>
                        <span className="skeleton" />
                      </td>
                    ))}
                  </tr>
                ))}
              {users?.map((user) => {
                const isSelf = user.username === currentUsername;
                const isPending = pendingId === user.id;
                const confirmKind = confirming?.id === user.id ? confirming.kind : null;
                const rowClass = [
                  !user.enabled && 'row-disabled',
                  isSelf && 'row-self',
                  confirmKind && `row-confirm row-confirm--${confirmKind}`,
                ]
                  .filter(Boolean)
                  .join(' ');
                return (
                  <tr key={user.id} className={rowClass || undefined} aria-busy={isPending || undefined}>
                    <td className="cell-username">{user.username}</td>
                    <td className="cell-email" title={user.email}>
                      {user.email}
                    </td>
                    <td>
                      <span className={`badge ${user.role === 'ADMIN' ? 'badge--admin' : ''}`}>{user.role}</span>
                    </td>
                    <td>
                      <span className={`state ${user.enabled ? 'state--on' : 'state--off'}`}>
                        {user.enabled ? 'Yes' : 'No'}
                      </span>
                    </td>
                    <td className="cell-created">
                      <time dateTime={user.createdAt} title={createdFull.format(new Date(user.createdAt))}>
                        {createdDate.format(new Date(user.createdAt))}
                      </time>
                    </td>
                    <td className="col-actions">
                      {isSelf ? (
                        <span className="self-note">
                          <Icon name="lock" />
                          (you)
                        </span>
                      ) : confirmKind ? (
                        <div
                          className="btn-row confirm-row"
                          role="group"
                          aria-labelledby={`confirm-${user.id}`}
                          onKeyDown={(e) => {
                            if (e.key === 'Escape') cancelConfirm(user, confirmKind);
                          }}
                        >
                          <span id={`confirm-${user.id}`} className="confirm-row__prompt">
                            {confirmKind === 'delete' ? `Delete ${user.username}?` : `Grant admin to ${user.username}?`}
                          </span>
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm btn-row-action"
                            id={`cancel-${user.id}`}
                            onClick={() => cancelConfirm(user, confirmKind)}
                          >
                            Cancel
                          </button>
                          {confirmKind === 'delete' ? (
                            <button
                              type="button"
                              className="btn btn-destructive btn-destructive--solid btn-sm btn-row-action"
                              disabled={isPending}
                              onClick={() => handleDelete(user)}
                            >
                              <Icon name="trash" />
                              Confirm delete
                            </button>
                          ) : (
                            <button
                              type="button"
                              className="btn btn-caution btn-sm btn-row-action"
                              disabled={isPending}
                              onClick={() => setRole(user, 'ADMIN')}
                            >
                              <Icon name="shieldUp" />
                              Grant admin
                            </button>
                          )}
                        </div>
                      ) : (
                        <div className="btn-row">
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm btn-row-action"
                            disabled={isPending}
                            onClick={() => handleToggleEnabled(user)}
                          >
                            <Icon name={user.enabled ? 'ban' : 'check'} />
                            <span className="btn-label">{user.enabled ? 'Disable' : 'Enable'}</span>
                          </button>
                          {user.role === 'ADMIN' ? (
                            <button
                              type="button"
                              className="btn btn-secondary btn-sm btn-row-action"
                              disabled={isPending}
                              onClick={() => setRole(user, 'USER')}
                            >
                              <Icon name="shieldDown" />
                              <span className="btn-label">Make USER</span>
                            </button>
                          ) : (
                            <button
                              type="button"
                              id={`grant-${user.id}`}
                              className="btn btn-secondary btn-sm btn-row-action"
                              disabled={isPending}
                              onClick={() => requestConfirm(user, 'grant')}
                            >
                              <Icon name="shieldUp" />
                              <span className="btn-label">Make ADMIN</span>
                            </button>
                          )}
                          <button
                            type="button"
                            id={`delete-${user.id}`}
                            className="btn btn-destructive btn-sm btn-row-action"
                            disabled={isPending}
                            onClick={() => requestConfirm(user, 'delete')}
                          >
                            <Icon name="trash" />
                            <span className="btn-label">Delete</span>
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {moreToRight && (
        <p className="table-hint" aria-hidden="true">
          Scroll sideways for more columns
          <Icon name="arrowRight" />
        </p>
      )}

      {users && (
        <p className="console__legend">
          <Icon name="lock" />
          (you) marks your own account. Admins can’t disable, demote, or delete themselves.
        </p>
      )}

      {users && users.length === 1 && (
        <p className="console__empty">
          You are the only account. New registrations will appear here, where you can disable them, change their role,
          or delete them.
        </p>
      )}
    </div>
  );
}
