import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { Alert } from '../components/Alert'
import { describeError, endsSession } from '../errors'

export function HomePage() {
  const [greeting, setGreeting] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let ignore = false
    api
      .hello()
      .then((text) => {
        if (!ignore) setGreeting(text)
      })
      .catch((err: unknown) => {
        if (!ignore && !endsSession(err)) setError(describeError(err))
      })
    return () => {
      ignore = true
    }
  }, [])

  return (
    <section className="card">
      {error && <Alert kind="error">{error}</Alert>}
      {greeting ? <h1 className="greeting">{greeting}</h1> : !error && <p className="muted">Loading…</p>}
      <p className="muted">You are signed in with a server-side session.</p>
    </section>
  )
}
