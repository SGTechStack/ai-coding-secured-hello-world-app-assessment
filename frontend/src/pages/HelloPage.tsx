import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { apiRequest } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { LogoutButton } from '../components/LogoutButton'

type State = { kind: 'loading' } | { kind: 'hello'; message: string } | { kind: 'error' }

const GENERIC_ERROR = 'Something went wrong. Please try again later.'

export function HelloPage() {
  const auth = useAuth()
  const [state, setState] = useState<State>({ kind: 'loading' })
  // Interface only: the API refuses admin requests from a User on its own.
  const isAdmin = auth.state.kind === 'authenticated' && auth.state.account.role === 'ADMIN'

  useEffect(() => {
    let active = true
    apiRequest<string>('/hello')
      .then((result) => {
        if (!active) return
        if (result.ok) setState({ kind: 'hello', message: String(result.data) })
        // A 401, and a 403 that says the password must be changed first, are both handled globally
        // (the route guard moves on, no error shown).
        else if (result.status !== 401 && result.problem?.code !== 'password_change_required')
          setState({ kind: 'error' })
      })
      .catch(() => active && setState({ kind: 'error' }))
    return () => {
      active = false
    }
  }, [])

  if (state.kind === 'loading') return <p>Loading…</p>
  if (state.kind === 'error') return <p role="alert">{GENERIC_ERROR}</p>
  return (
    <section>
      {/* Server data is rendered as text only. */}
      <h1>{state.message}</h1>
      <p>
        <Link to="/password-change">Change password</Link>
      </p>
      {isAdmin && (
        <p>
          <Link to="/admin/users">Manage Accounts</Link>
        </p>
      )}
      <LogoutButton />
    </section>
  )
}
