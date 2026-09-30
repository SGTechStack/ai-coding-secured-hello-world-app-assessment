import { useState, type FormEvent, type ReactNode } from 'react'
import { Link } from 'react-router'
import { requestPasswordReset } from '../api/endpoints'
import { ErrorBanner, NoticeBanner } from '../components/Feedback'
import { describeError } from '../components/errorText'

/**
 * Request a reset link.
 *
 * **The confirmation text is identical whether or not the email is registered**, and it is rendered from
 * a constant here rather than from the server's response. The server already returns the same body for
 * both cases (Std:247, Std:504-507); rendering a constant means a future change to that response cannot
 * reintroduce an enumeration oracle through the UI.
 */
export function ForgotPasswordPage(): ReactNode {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(event: FormEvent): Promise<void> {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await requestPasswordReset(email)
      setSent(true)
    } catch (failure) {
      // A 429 is the one failure worth showing: it is about the caller's rate, not about the address.
      setError(describeError(failure, 'The request could not be submitted.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <h1>Reset your password</h1>
      {sent && (
        <NoticeBanner message="If that email address has an account, a reset link is on its way. Check your inbox." />
      )}
      <ErrorBanner message={error} />
      <form onSubmit={onSubmit}>
        <label htmlFor="email">Email</label>
        <input
          id="email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          required
        />
        <button type="submit" disabled={busy}>
          {busy ? 'Submitting…' : 'Send reset link'}
        </button>
      </form>
      <p className="muted">
        <Link to="/login">Back to sign in</Link> · <Link to="/reset-password">I already have a token</Link>
      </p>
    </section>
  )
}
