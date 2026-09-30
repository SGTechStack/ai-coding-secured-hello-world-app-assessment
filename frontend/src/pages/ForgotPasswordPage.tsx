import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { requestPasswordReset, type RequestPasswordResetResult } from '../api/auth'
import { Field } from '../components/Field'

const FAILURE_MESSAGES: Record<Extract<RequestPasswordResetResult, { ok: false }>['reason'], string> = {
  rate_limited: 'Too many attempts. Please try again later.',
  validation: 'Enter a valid email address.',
  error: 'Something went wrong. Please try again later.',
}

/**
 * Forgot-password screen: a Visitor requests a reset by email. The server's response never reveals
 * whether the email is registered, so success always goes to the login page with the same generic
 * message.
 */
export function ForgotPasswordPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(undefined)
    try {
      const result = await requestPasswordReset(email)
      if (result.ok) {
        navigate('/login', { state: { resetRequested: true } })
        return
      }
      setError(FAILURE_MESSAGES[result.reason])
    } catch {
      setError(FAILURE_MESSAGES.error)
    }
    setSubmitting(false)
  }

  return (
    <section>
      <h1>Forgot password</h1>
      <form onSubmit={submit} noValidate>
        <Field
          name="email"
          label="Email"
          sensitivity="Sensitive Normal"
          type="email"
          autoComplete="email"
          value={email}
          onChange={setEmail}
        />
        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={submitting}>
          Send reset link
        </button>
      </form>
      <p>
        <Link to="/login">Back to login</Link>
      </p>
    </section>
  )
}
