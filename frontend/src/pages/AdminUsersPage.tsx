import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { listAccounts, type AdminAccount } from '../api/admin'

type State =
  { kind: 'loading' } | { kind: 'loaded'; accounts: AdminAccount[] } | { kind: 'forbidden' } | { kind: 'error' }

const GENERIC_ERROR = 'Something went wrong. Please try again later.'

/** The admin Account list. Server data is rendered as text only. */
export function AdminUsersPage() {
  const [state, setState] = useState<State>({ kind: 'loading' })

  useEffect(() => {
    let active = true
    listAccounts()
      .then((result) => {
        if (!active) return
        if (result.ok) setState({ kind: 'loaded', accounts: result.accounts })
        else setState({ kind: result.reason })
      })
      .catch(() => active && setState({ kind: 'error' }))
    return () => {
      active = false
    }
  }, [])

  return (
    <section>
      <h1>Accounts</h1>
      {state.kind === 'loading' && <p>Loading…</p>}
      {state.kind === 'forbidden' && <p role="alert">You are not allowed to manage Accounts.</p>}
      {state.kind === 'error' && <p role="alert">{GENERIC_ERROR}</p>}
      {state.kind === 'loaded' && <AccountTable accounts={state.accounts} />}
      <p>
        <Link to="/">Back</Link>
      </p>
    </section>
  )
}

function AccountTable({ accounts }: { accounts: AdminAccount[] }) {
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
          </tr>
        </thead>
        <tbody>
          {accounts.map((account) => (
            <tr key={account.id}>
              <td>{account.username}</td>
              <td>{account.email}</td>
              <td>{account.role === 'ADMIN' ? 'Admin' : 'User'}</td>
              <td>{account.enabled ? 'Enabled' : 'Disabled'}</td>
              <td>{account.locked ? 'Locked' : 'No'}</td>
              <td>
                <time dateTime={account.createdAt}>{new Date(account.createdAt).toLocaleString()}</time>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
