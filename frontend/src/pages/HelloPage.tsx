import { useEffect, useState } from 'react'
import { logout } from '../api/auth'
import { apiRequest } from '../api/client'
import { useAuth } from '../auth/useAuth'

type State = { kind: 'loading' } | { kind: 'hello'; message: string } | { kind: 'error' }

const GENERIC_ERROR = 'Something went wrong. Please try again later.'

export function HelloPage() {
  const auth = useAuth()
  const [state, setState] = useState<State>({ kind: 'loading' })
  const [logoutFailed, setLogoutFailed] = useState(false)

  useEffect(() => {
    let active = true
    apiRequest<string>('/hello')
      .then((result) => {
        if (!active) return
        if (result.ok) setState({ kind: 'hello', message: String(result.data) })
        // 401 is handled globally (redirect to login, no error shown).
        else if (result.status !== 401) setState({ kind: 'error' })
      })
      .catch(() => active && setState({ kind: 'error' }))
    return () => {
      active = false
    }
  }, [])

  async function logOut() {
    setLogoutFailed(false)
    try {
      if (await logout()) {
        // The route guard sends the now-anonymous caller to the login screen.
        auth.loggedOut()
        return
      }
    } catch {
      // Falls through to the error message.
    }
    setLogoutFailed(true)
  }

  if (state.kind === 'loading') return <p>Loading…</p>
  if (state.kind === 'error') return <p role="alert">{GENERIC_ERROR}</p>
  return (
    <section>
      {/* Server data is rendered as text only. */}
      <h1>{state.message}</h1>
      {logoutFailed && <p role="alert">{GENERIC_ERROR}</p>}
      <button type="button" onClick={logOut}>
        Log out
      </button>
    </section>
  )
}
