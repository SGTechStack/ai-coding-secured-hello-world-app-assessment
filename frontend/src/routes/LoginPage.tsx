import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '../auth/useAuth'
import { ErrorBanner } from '../components/Feedback'
import { describeError } from '../components/errorText'

export function LoginPage(): ReactNode {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(event: FormEvent): Promise<void> {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const user = await login(username, password)
      // The forced-change gate would redirect anyway; navigating straight there avoids a visible flash
      // of the greeting screen for an account that cannot use it.
      void navigate(user.requirePasswordChange ? '/change-password' : '/hello', { replace: true })
    } catch (failure) {
      setError(describeError(failure, 'Sign-in failed.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <h1>Sign in</h1>
      <ErrorBanner message={error} />
      <form onSubmit={onSubmit}>
        <label htmlFor="username">Username</label>
        <input
          id="username"
          name="username"
          autoComplete="username"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          required
        />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
        />

        <button type="submit" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <p className="muted">
        <Link to="/register">Create an account</Link> · <Link to="/forgot-password">Forgot password</Link>
      </p>
    </section>
  )
}
