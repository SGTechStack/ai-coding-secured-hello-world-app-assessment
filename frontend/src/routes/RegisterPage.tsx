import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { register } from '../api/endpoints'
import { ErrorBanner } from '../components/Feedback'
import { describeError } from '../components/errorText'

export function RegisterPage(): ReactNode {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(event: FormEvent): Promise<void> {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await register(username, email, password)
      void navigate('/login', { replace: true, state: { registered: true } })
    } catch (failure) {
      setError(describeError(failure, 'Registration failed.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <h1>Create an account</h1>
      <ErrorBanner message={error} />
      <form onSubmit={onSubmit}>
        <label htmlFor="username">Username</label>
        <input
          id="username"
          autoComplete="username"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          required
          maxLength={100}
        />

        <label htmlFor="email">Email</label>
        <input
          id="email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          required
          maxLength={255}
        />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
          minLength={12}
          maxLength={72}
        />
        {/*
          Length only, and no pattern attribute. The policy has NO composition rules: a twelve-character
          all-lowercase passphrase with spaces is valid, and a `pattern` here would reject passwords the
          server accepts. maxLength is 72 because BCrypt throws above 72 bytes -- the server answers 400
          rather than 500, and stopping it in the field is a courtesy, not the enforcement.
        */}
        <p className="hint">12 to 72 characters. A passphrase is fine; no symbols or digits required.</p>

        <button type="submit" disabled={busy}>
          {busy ? 'Creating…' : 'Create account'}
        </button>
      </form>
      <p className="muted">
        <Link to="/login">Back to sign in</Link>
      </p>
    </section>
  )
}
