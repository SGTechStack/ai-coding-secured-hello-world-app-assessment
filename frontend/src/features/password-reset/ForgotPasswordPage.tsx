import { type FormEvent, useState } from 'react'
import { Link } from 'react-router'
import { SERVER_UNAVAILABLE } from '../../shared/api/http.ts'
import { AuthCard } from '../../shared/ui/AuthCard.tsx'
import { requestPasswordReset } from './api.ts'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [status, setStatus] = useState<string>()
  const [error, setError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setStatus(undefined)
    setError(undefined)
    const result = await requestPasswordReset(email)
    if (result.status === 'accepted') setStatus(result.message)
    else setError(SERVER_UNAVAILABLE)
    setSubmitting(false)
  }

  return (
    <AuthCard
      title="Forgot password"
      description="Enter your account email and we'll send you a reset link."
      footer={
        <>
          <span className="muted">Remembered it?</span>
          <Link to="/login">Back to log in</Link>
        </>
      }
    >
      {status ? (
        <p role="status" className="notice notice--success">
          {status}
        </p>
      ) : null}
      {error ? (
        <div role="alert" className="notice notice--error">
          {error}
        </div>
      ) : null}
      <form className="form-stack" noValidate onSubmit={submit}>
        <div className="field">
          <label htmlFor="forgot-email">Email</label>
          <input
            id="forgot-email"
            name="email"
            type="email"
            autoComplete="email"
            value={email}
            disabled={submitting}
            onChange={(event) => setEmail(event.currentTarget.value)}
          />
        </div>
        <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
          Send reset link
        </button>
      </form>
    </AuthCard>
  )
}
