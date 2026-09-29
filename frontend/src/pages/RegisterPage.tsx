import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError, api } from '../api/client'
import type { LoginRedirectState } from '../auth/guards'
import { Alert } from '../components/Alert'
import { Field } from '../components/Field'
import { describeError } from '../errors'
import { PASSWORD_MIN_LENGTH, passwordProblem } from '../passwordPolicy'

type FieldErrors = Partial<Record<'username' | 'email' | 'password' | 'confirmPassword', string>>

export function RegisterPage() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    const clientErrors: FieldErrors = {}
    const problem = passwordProblem(password)
    if (problem) clientErrors.password = problem
    if (password !== confirmPassword) clientErrors.confirmPassword = 'Passwords do not match.'
    setFieldErrors(clientErrors)
    if (Object.keys(clientErrors).length > 0) return

    setSubmitting(true)
    try {
      await api.register(username, email, password)
      const redirect: LoginRedirectState = { notice: 'Account created. You can now sign in.' }
      navigate('/login', { state: redirect })
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setFieldErrors(err.fieldErrors)
      }
      setError(describeError(err))
      setSubmitting(false)
    }
  }

  return (
    <section className="card">
      <h1>Create an account</h1>
      {error && <Alert kind="error">{error}</Alert>}
      <form onSubmit={handleSubmit}>
        <Field
          label="Username"
          name="username"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          required
          pattern="[A-Za-z0-9._\-]{3,32}"
          hint="3–32 characters: letters, digits, '.', '_' or '-'."
          error={fieldErrors.username}
          value={username}
          onChange={(event) => setUsername(event.target.value)}
        />
        <Field
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          required
          maxLength={254}
          hint="Used only for password resets."
          error={fieldErrors.email}
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
        <Field
          label="Password"
          name="password"
          type="password"
          autoComplete="new-password"
          required
          hint={`At least ${PASSWORD_MIN_LENGTH} characters. A passphrase of several words works well.`}
          error={fieldErrors.password}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
        <Field
          label="Confirm password"
          name="confirmPassword"
          type="password"
          autoComplete="new-password"
          required
          error={fieldErrors.confirmPassword}
          value={confirmPassword}
          onChange={(event) => setConfirmPassword(event.target.value)}
        />
        <button type="submit" disabled={submitting}>
          {submitting ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p className="links">
        <Link to="/login">Already have an account? Sign in</Link>
      </p>
    </section>
  )
}
