import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { api } from '../api/client'
import { Alert } from '../components/Alert'
import { Field } from '../components/Field'
import { describeError } from '../errors'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      // The server answers the same way whether or not the address is registered.
      const response = await api.requestPasswordReset(email)
      setMessage(response.message)
    } catch (err) {
      setError(describeError(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="card">
      <h1>Reset your password</h1>
      {message ? (
        <Alert kind="success">{message}</Alert>
      ) : (
        <>
          {error && <Alert kind="error">{error}</Alert>}
          <p className="muted">Enter the email address on your account and we will send you a reset link.</p>
          <form onSubmit={handleSubmit}>
            <Field
              label="Email"
              name="email"
              type="email"
              autoComplete="email"
              required
              maxLength={254}
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
            <button type="submit" disabled={submitting}>
              {submitting ? 'Sending…' : 'Send reset link'}
            </button>
          </form>
        </>
      )}
      <p className="links">
        <Link to="/login">Back to sign in</Link>
      </p>
    </section>
  )
}
