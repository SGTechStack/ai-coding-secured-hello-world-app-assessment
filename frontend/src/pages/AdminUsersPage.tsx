import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import {
  changeRole,
  listAccounts,
  requirePasswordChange,
  setEnabled,
  unlock,
  type AdminAccount,
  type AdminActionResult,
} from '../api/admin'

type State =
  { kind: 'loading' } | { kind: 'loaded'; accounts: AdminAccount[] } | { kind: 'forbidden' } | { kind: 'error' }

const GENERIC_ERROR = 'Something went wrong. Please try again later.'

const ACTION_ERROR_MESSAGES: Record<Extract<AdminActionResult, { ok: false }>['reason'], string> = {
  self_action_forbidden: 'An Admin cannot perform this action on their own Account.',
  last_admin: 'This change would leave no enabled Admin.',
  not_found: 'That Account no longer exists.',
  forbidden: 'You are not allowed to manage Accounts.',
  error: GENERIC_ERROR,
}

/**
 * The admin Account list, with enable/disable, role, unlock and require-password-change actions. Server
 * data is rendered as text only.
 */
export function AdminUsersPage() {
  const [state, setState] = useState<State>({ kind: 'loading' })
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

  const load = useCallback((active: () => boolean) => {
    listAccounts().then((result) => {
      if (!active()) return
      if (result.ok) setState({ kind: 'loaded', accounts: result.accounts })
      else setState({ kind: result.reason })
    })
  }, [])

  useEffect(() => {
    let active = true
    load(() => active)
    return () => {
      active = false
    }
  }, [load])

  async function runAction(id: string, action: () => Promise<AdminActionResult>) {
    setBusyId(id)
    setActionError(null)
    const result = await action()
    if (result.ok) {
      load(() => true)
    } else {
      setActionError(ACTION_ERROR_MESSAGES[result.reason])
    }
    setBusyId(null)
  }

  return (
    <section>
      <h1>Accounts</h1>
      {actionError && <p role="alert">{actionError}</p>}
      {state.kind === 'loading' && <p>Loading…</p>}
      {state.kind === 'forbidden' && <p role="alert">You are not allowed to manage Accounts.</p>}
      {state.kind === 'error' && <p role="alert">{GENERIC_ERROR}</p>}
      {state.kind === 'loaded' && (
        <AccountTable
          accounts={state.accounts}
          busyId={busyId}
          onSetEnabled={(id, enabled) => runAction(id, () => setEnabled(id, enabled))}
          onChangeRole={(id, role) => runAction(id, () => changeRole(id, role))}
          onUnlock={(id) => runAction(id, () => unlock(id))}
          onRequirePasswordChange={(id) => runAction(id, () => requirePasswordChange(id))}
        />
      )}
      <p>
        <Link to="/">Back</Link>
      </p>
    </section>
  )
}

function AccountTable({
  accounts,
  busyId,
  onSetEnabled,
  onChangeRole,
  onUnlock,
  onRequirePasswordChange,
}: {
  accounts: AdminAccount[]
  busyId: string | null
  onSetEnabled: (id: string, enabled: boolean) => void
  onChangeRole: (id: string, role: 'USER' | 'ADMIN') => void
  onUnlock: (id: string) => void
  onRequirePasswordChange: (id: string) => void
}) {
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th scope="col">Username</th>
            <th scope="col">Email</th>
            <th scope="col">Role</th>
            <th scope="col">Enabled</th>
            <th scope="col">Locked</th>
            <th scope="col">Created</th>
            <th scope="col">Actions</th>
          </tr>
        </thead>
        <tbody>
          {accounts.map((account) => {
            const busy = busyId === account.id
            const otherRole = account.role === 'ADMIN' ? 'USER' : 'ADMIN'
            return (
              <tr key={account.id}>
                <td>{account.username}</td>
                <td>{account.email}</td>
                <td>{account.role === 'ADMIN' ? 'Admin' : 'User'}</td>
                <td>{account.enabled ? 'Enabled' : 'Disabled'}</td>
                <td>{account.locked ? 'Locked' : 'No'}</td>
                <td>
                  <time dateTime={account.createdAt}>{new Date(account.createdAt).toLocaleString()}</time>
                </td>
                <td>
                  <button type="button" disabled={busy} onClick={() => onSetEnabled(account.id, !account.enabled)}>
                    {account.enabled ? 'Disable' : 'Enable'}
                  </button>{' '}
                  <button type="button" disabled={busy} onClick={() => onChangeRole(account.id, otherRole)}>
                    {account.role === 'ADMIN' ? 'Make User' : 'Make Admin'}
                  </button>{' '}
                  {account.locked && (
                    <>
                      <button type="button" disabled={busy} onClick={() => onUnlock(account.id)}>
                        Unlock
                      </button>{' '}
                    </>
                  )}
                  {/* Ends the Account's Sessions too, so a suspected attacker is logged out at once. */}
                  <button type="button" disabled={busy} onClick={() => onRequirePasswordChange(account.id)}>
                    Require password change
                  </button>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
