import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router';
import { api, ApiError, ErrorCode, type AccountPage, type AdminAccount } from '../api/client';
import { useAuth } from '../auth/AuthContext';

const PAGE_SIZE = 50;

/** What to tell the Admin when a change was refused. A lost session is handled by the global 401 handler. */
function refusal(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case ErrorCode.selfActionNotAllowed:
        return 'You can’t make this change to your own account.';
      case ErrorCode.notFound:
        return 'That account no longer exists.';
      case ErrorCode.forbidden:
        return 'You no longer have admin access.';
    }
  }
  return 'The change could not be made. Please try again.';
}

export function AdminUsersPage() {
  const { me } = useAuth();
  const [page, setPage] = useState(0);
  const [accounts, setAccounts] = useState<AccountPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async (number: number) => {
    try {
      setAccounts(await api.listAccounts({ page: number, size: PAGE_SIZE }));
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 401)) setError('The accounts could not be loaded.');
    }
  }, []);

  useEffect(() => {
    void load(page);
  }, [load, page]);

  /** Makes one change, then reloads the page, which a delete or another Admin may have changed too. */
  async function change(account: AdminAccount, action: () => Promise<unknown>) {
    setBusy(account.id);
    setError(null);
    try {
      await action();
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) return;
      setError(refusal(e));
    } finally {
      setBusy(null);
    }
    await load(page);
  }

  function onDelete(account: AdminAccount) {
    if (window.confirm(`Delete ${account.username}? They will no longer be able to log in, and the username and email can never be used again.`)) {
      void change(account, () => api.deleteAccount(account.id));
    }
  }

  const totalPages = accounts?.page.totalPages ?? 0;

  return (
    <main>
      <h1>Accounts</h1>
      <p>
        <Link to="/">Home</Link>
      </p>
      {error && <p role="alert">{error}</p>}
      {accounts === null ? (
        <p>Loading…</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th scope="col">Username</th>
              <th scope="col">Email</th>
              <th scope="col">Role</th>
              <th scope="col">Status</th>
              <th scope="col">Locked</th>
              <th scope="col">Created</th>
              <th scope="col">Actions</th>
            </tr>
          </thead>
          <tbody>
            {accounts.content.map((account) => (
              <tr key={account.id}>
                <td>{account.username}</td>
                <td>{account.email}</td>
                <td>{account.role === 'ADMIN' ? 'Admin' : 'Regular user'}</td>
                <td>{account.enabled ? 'Enabled' : 'Disabled'}</td>
                <td>{account.locked ? 'Locked' : 'No'}</td>
                <td>{new Date(account.createdAt).toLocaleString()}</td>
                <td>
                  {account.id === me?.id ? (
                    'This is you'
                  ) : (
                    <AccountActions
                      account={account}
                      disabled={busy !== null}
                      onChange={(action) => void change(account, action)}
                      onDelete={() => onDelete(account)}
                    />
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {totalPages > 1 && (
        <nav aria-label="Pages">
          <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>
            Previous
          </button>
          <span>
            Page {page + 1} of {totalPages}
          </span>
          <button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>
            Next
          </button>
        </nav>
      )}
    </main>
  );
}

function AccountActions({
  account,
  disabled,
  onChange,
  onDelete,
}: {
  account: AdminAccount;
  disabled: boolean;
  onChange: (action: () => Promise<unknown>) => void;
  onDelete: () => void;
}) {
  return (
    <>
      <button type="button" disabled={disabled} onClick={() => onChange(() => api.setAccountEnabled(account.id, !account.enabled))}>
        {account.enabled ? 'Disable' : 'Enable'}
      </button>
      {account.locked && (
        <button type="button" disabled={disabled} onClick={() => onChange(() => api.unlockAccount(account.id))}>
          Unlock
        </button>
      )}
      <button
        type="button"
        disabled={disabled}
        onClick={() => onChange(() => api.changeAccountRole(account.id, account.role === 'ADMIN' ? 'USER' : 'ADMIN'))}
      >
        {account.role === 'ADMIN' ? 'Make regular user' : 'Make admin'}
      </button>
      <button type="button" disabled={disabled} onClick={onDelete}>
        Delete
      </button>
    </>
  );
}
