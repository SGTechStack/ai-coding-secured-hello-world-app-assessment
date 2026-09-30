import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { login, type LoginResult } from '../api/auth'
import { useAuth } from '../auth/useAuth'
import { Field } from '../components/Field'

const FAILURE_MESSAGES: Record<Extract<LoginResult, { ok: false }>['reason'], string> = {
  rejected: 'The username or password is incorrect.',
  rate_limited: 'Too many attempts. Please try again later.',
  error: 'Something went wrong. Please try again later.',
}

/** Set in navigation state by the register, forgot-password and reset-password screens. */
type LoginNotice = { registered?: boolean; resetRequested?: boolean; resetCompleted?: boolean }

export function LoginPage() {
  const navigate = useNavigate()
  const auth = useAuth()
  const notice = (useLocation().state ?? {}) as LoginNotice
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(undefined)
    try {
      const result = await login(username, password)
      if (result.ok) {
        auth.loggedIn(result.account)
        // Always one of our own two screens: a return URL is never followed. An Account whose password
        // must be changed goes straight there, since nothing else is available to it (story 110).
        navigate(result.account.passwordChangeRequired ? '/password-change' : '/', { replace: true })
        return
      }
      setError(FAILURE_MESSAGES[result.reason])
    } catch {
      setError(FAILURE_MESSAGES.error)
    }
    setPassword('')
    setSubmitting(false)
  }

  return (
    <section>
      <h1>Log in</h1>
      {notice.registered === true && <p role="status">Your Account has been created. Please log in.</p>}
      {notice.resetRequested === true && (
        <p role="status">If an Account exists for that email, a reset link has been sent.</p>
      )}
      {notice.resetCompleted === true && <p role="status">Your password has been reset. Please log in.</p>}
      {auth.state.kind === 'anonymous' && 'passwordChanged' in auth.state && (
        <p role="status">Your password has been changed. Please log in again.</p>
      )}
      {auth.state.kind === 'anonymous' && 'loggedOut' in auth.state && <p role="status">You have logged out.</p>}
      <form onSubmit={submit} noValidate>
        <Field
          name="username"
          label="Username"
          sensitivity="Sensitive Normal"
          autoComplete="username"
          value={username}
          onChange={setUsername}
        />
        <Field
          name="password"
          label="Password"
          sensitivity="Sensitive High"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={setPassword}
        />
        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={submitting}>
          Log in
        </button>
      </form>
      <p>
        <Link to="/forgot-password">Forgot password?</Link>
      </p>
      <p>
        No Account yet? <Link to="/register">Register</Link>
      </p>
    </section>
  )
}
