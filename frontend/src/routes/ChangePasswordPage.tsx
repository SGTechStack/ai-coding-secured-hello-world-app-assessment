import { useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { changePassword } from '../api/endpoints'
import { useAuth } from '../auth/useAuth'
import { ErrorBanner, NoticeBanner } from '../components/Feedback'
import { describeError } from '../components/errorText'

/**
 * Self-service password change, and the second step of the three-step first boot.
 *
 * **A successful change is treated as a logout**, because it is one: the server invalidates every session
 * for the account including the caller's own. Anything else here would leave the app holding a session
 * cookie the server has already discarded, and the next call would 401 for reasons the user cannot
 * connect to what they just did.
 *
 * This is also the only screen a flagged account can reach, so it says so — an account that lands here
 * without asking needs to know why.
 */
export function ChangePasswordPage(): ReactNode {
  const { user, forgetSession } = useAuth()
  const navigate = useNavigate()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const forced = user?.requirePasswordChange === true

  async function onSubmit(event: FormEvent): Promise<void> {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await changePassword(currentPassword, newPassword)
      // Local state first, then the login screen. forgetSession rather than logout(): the session is
      // already gone server-side, and POSTing to /auth/logout would answer 401 on a dead session.
      forgetSession()
      void navigate('/login', { replace: true, state: { passwordChanged: true } })
    } catch (failure) {
      // A wrong current password is a 400 with CURRENT_PASSWORD_INVALID, never a 401 -- so the user stays
      // signed in and can simply retype it. That choice is the reason this catch can be this simple.
      setError(describeError(failure, 'The password could not be changed.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <h1>Change your password</h1>
      {forced && (
        <NoticeBanner message="This account must choose a new password before it can be used." />
      )}
      <ErrorBanner message={error} />
      <form onSubmit={onSubmit}>
        <label htmlFor="currentPassword">Current password</label>
        <input
          id="currentPassword"
          type="password"
          autoComplete="current-password"
          value={currentPassword}
          onChange={(event) => setCurrentPassword(event.target.value)}
          required
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
        <p className="hint">
          12 to 72 characters, and not one of your last four passwords. You will be signed out and will
          need to sign in again — every session for this account ends, including this one.
        </p>

        <button type="submit" disabled={busy}>
          {busy ? 'Changing…' : 'Change password'}
        </button>
      </form>
    </section>
  )
}
