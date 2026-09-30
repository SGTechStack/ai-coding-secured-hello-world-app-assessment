import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'

// Deliberately shows the SAME message after every submission, success or failure, and never
// branches UI behavior on the response content — the backend response is generic by design
// (enumeration resistance, PRD Story 6), and the UI must not undo that by reacting differently.
const GENERIC_MESSAGE = 'If that email is registered, a reset link has been sent.'

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    try {
      await api.post('/api/auth/password-reset/request', { email })
    } catch {
      // Ignored deliberately: the same generic message is shown regardless of outcome.
    } finally {
      setSubmitting(false)
      setSubmitted(true)
    }
  }

  return (
    <main>
      <h1>Forgot password</h1>
      {submitted ? (
        <p role="status">{GENERIC_MESSAGE}</p>
      ) : (
        <form onSubmit={handleSubmit}>
          <label>
            Email
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </label>
          <button type="submit" disabled={submitting}>
            {submitting ? 'Sending…' : 'Send reset link'}
          </button>
        </form>
      )}
      <p>
        <Link to="/login">Back to login</Link>
      </p>
    </main>
  )
}
