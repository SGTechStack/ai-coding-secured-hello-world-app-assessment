import { useEffect, useState } from 'react'
import { apiRequest, ensureCsrfToken } from '../api/client'

type State = { kind: 'loading' } | { kind: 'hello'; message: string } | { kind: 'error' }

export function HelloPage() {
  const [state, setState] = useState<State>({ kind: 'loading' })

  useEffect(() => {
    let active = true
    ensureCsrfToken()
      .then(() => apiRequest<string>('/hello'))
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

  if (state.kind === 'loading') return <p>Loading…</p>
  if (state.kind === 'error') return <p role="alert">Something went wrong. Please try again later.</p>
  // Server data is rendered as text only.
  return <h1>{state.message}</h1>
}
