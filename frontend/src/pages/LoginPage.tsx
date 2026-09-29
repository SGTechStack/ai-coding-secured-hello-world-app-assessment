import { useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { useAuth } from '../auth/useAuth'
import type { LoginRedirectState } from '../auth/guards'
import { Alert } from '../components/Alert'
import { Field } from '../components/Field'
import { describeError } from '../errors'

export function LoginPage() {
  const { login } = useAuth()
  const notice = (useLocation().state as LoginRedirectState | null)?.notice
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      // On success GuestOnly redirects, since the auth state is now authenticated.
      await login(username, password)
    } catch (err) {
      setError(describeError(err))
      setPassword('')
      setSubmitting(false)
    }
  }

  return (
    <section className="card">
      <h1>Sign in</h1>
      {notice && !error && <Alert kind="info">{notice}</Alert>}
      {error && <Alert kind="error">{error}</Alert>}
      <form onSubmit={handleSubmit}>
        <Field
          label="Username"
          name="username"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          required
          maxLength={64}
          value={username}
          onChange={(event) => setUsername(event.target.value)}
        />
        <Field
          label="Password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
          maxLength={256}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
        <button type="submit" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <p className="links">
        <Link to="/forgot-password">Forgot your password?</Link>
        <Link to="/register">Create an account</Link>
      </p>
    </section>
  )
}
