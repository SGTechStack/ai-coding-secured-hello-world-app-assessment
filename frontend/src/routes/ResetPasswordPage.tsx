import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { confirmPasswordReset } from '../api/endpoints'
import { ErrorBanner } from '../components/Feedback'
import { describeError } from '../components/errorText'

/**
 * Confirm a reset.
 *
 * The token comes from the `?token=` query string when the user followed a link, and is editable so a
 * token delivered by other means can be pasted. Confirming clears `requirePasswordChange` and invalidates
 * every session for the account, so this always ends at the login screen.
 *
 * Expired, already-used and never-existed tokens all produce the same 400 `RESET_TOKEN_INVALID`, and this
 * screen must not try to be more helpful than that: telling someone their token has *expired* confirms
 * they guessed a real one (Std:261).
 */
export function ResetPasswordPage(): ReactNode {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const [token, setToken] = useState(params.get('token') ?? '')
  const [newPassword, setNewPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(event: FormEvent): Promise<void> {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await confirmPasswordReset(token, newPassword)
      void navigate('/login', { replace: true, state: { passwordChanged: true } })
    } catch (failure) {
      setError(describeError(failure, 'That reset link is no longer valid. Request a new one.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <h1>Choose a new password</h1>
      <ErrorBanner message={error} />
      <form onSubmit={onSubmit}>
        <label htmlFor="token">Reset token</label>
        <input
          id="token"
          value={token}
          onChange={(event) => setToken(event.target.value)}
          required
          // 32 alphanumerics. Not validated with a pattern: an invalid-looking token must reach the
          // server and come back as the same generic 400 as a valid-looking one.
        />

        <label htmlFor="newPassword">New password</label>
        <input
          id="newPassword"
          type="password"
          autoComplete="new-password"
          value={newPassword}
          onChange={(event) => setNewPassword(event.target.value)}
          required
          minLength={12}
          maxLength={72}
        />
        <p className="hint">12 to 72 characters, and not one of your last four passwords.</p>

        <button type="submit" disabled={busy}>
          {busy ? 'Saving…' : 'Set new password'}
        </button>
      </form>
      <p className="muted">
        <Link to="/forgot-password">Request another link</Link>
      </p>
    </section>
  )
}
