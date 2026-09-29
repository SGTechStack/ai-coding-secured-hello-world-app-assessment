import { useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { Alert } from '../components/Alert'
import { describeError } from '../errors'

export function HomePage() {
  const { sessionEnded } = useAuth()
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
        if (ignore) return
        if (err instanceof ApiError && err.status === 401) {
          sessionEnded()
        } else {
          setError(describeError(err))
        }
      })
    return () => {
      ignore = true
    }
  }, [sessionEnded])

  return (
    <section className="card">
      {error && <Alert kind="error">{error}</Alert>}
      {greeting ? <h1 className="greeting">{greeting}</h1> : !error && <p className="muted">Loading…</p>}
      <p className="muted">You are signed in with a server-side session.</p>
    </section>
  )
}
