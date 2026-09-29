import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { api } from '../api/client'
import type { LoginRedirectState } from '../auth/guards'
import { Alert } from '../components/Alert'
import { Field } from '../components/Field'
import { describeError } from '../errors'
import { PASSWORD_MIN_LENGTH, passwordProblem } from '../passwordPolicy'

function tokenFromHash(hash: string): string | null {
  return new URLSearchParams(hash.replace(/^#/, '')).get('token')
}

/**
 * Landing page for the emailed link, which carries the token in the URL fragment
 * (#token=...). Fragments are never sent to servers or in Referer headers; the token is
 * also removed from the address bar and history as soon as it has been read.
 */
export function ResetPasswordPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const [token] = useState(() => tokenFromHash(location.hash))
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<{ password?: string; confirmPassword?: string }>({})
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (location.hash) {
      navigate({ pathname: location.pathname, search: location.search }, { replace: true })
    }
  }, [location.hash, location.pathname, location.search, navigate])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token) return
    setError(null)
    const problem = passwordProblem(password)
    const errors = {
      password: problem ?? undefined,
      confirmPassword: password !== confirmPassword ? 'Passwords do not match.' : undefined,
    }
    setFieldErrors(errors)
    if (errors.password || errors.confirmPassword) return

    setSubmitting(true)
    try {
      await api.confirmPasswordReset(token, password)
      const redirect: LoginRedirectState = {
        notice: 'Your password has been changed and all your sessions were signed out. Please sign in.',
      }
      navigate('/login', { replace: true, state: redirect })
    } catch (err) {
      setError(describeError(err))
      setSubmitting(false)
    }
  }

  if (!token) {
    return (
      <section className="card">
        <h1>Choose a new password</h1>
        <Alert kind="error">This password reset link is invalid or incomplete.</Alert>
        <p className="links">
          <Link to="/forgot-password">Request a new link</Link>
        </p>
      </section>
    )
  }

  return (
    <section className="card">
      <h1>Choose a new password</h1>
      {error && <Alert kind="error">{error}</Alert>}
      <form onSubmit={handleSubmit}>
        <Field
          label="New password"
          name="newPassword"
          type="password"
          autoComplete="new-password"
          required
          hint={`At least ${PASSWORD_MIN_LENGTH} characters.`}
          error={fieldErrors.password}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
        <Field
          label="Confirm new password"
          name="confirmPassword"
          type="password"
          autoComplete="new-password"
          required
          error={fieldErrors.confirmPassword}
          value={confirmPassword}
          onChange={(event) => setConfirmPassword(event.target.value)}
        />
        <button type="submit" disabled={submitting}>
          {submitting ? 'Saving…' : 'Set new password'}
        </button>
      </form>
      <p className="links">
        <Link to="/forgot-password">Request a new link</Link>
      </p>
    </section>
  )
}
