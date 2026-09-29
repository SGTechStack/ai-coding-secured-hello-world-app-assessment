import { type FormEvent, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useSubmitThenLogIn } from '../../shared/navigation/useSubmitThenLogIn.ts'
import { AuthCard } from '../../shared/ui/AuthCard.tsx'
import { confirmPasswordReset } from './api.ts'

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const [newPassword, setNewPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const {
    error,
    setError,
    submitting,
    submit: send,
  } = useSubmitThenLogIn('Your password has been reset. Please log in.')

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (newPassword !== confirmation) {
      setError('Passwords do not match')
      return
    }
    void send(() => confirmPasswordReset(token, newPassword))
  }

  return (
    <AuthCard
      title="Reset password"
      description="Choose a new password for your account."
      footer={
        <>
          <span className="muted">Link expired?</span>
          <Link to="/forgot-password">Request a new link</Link>
        </>
      }
    >
      {error ? (
        <div role="alert" className="notice notice--error">
          {error}
        </div>
      ) : null}
      <form className="form-stack" onSubmit={submit}>
        <div className="field">
          <label htmlFor="reset-new-password">New password</label>
          <input
            id="reset-new-password"
            name="newPassword"
            type="password"
            autoComplete="new-password"
            value={newPassword}
            disabled={submitting}
            onChange={(event) => setNewPassword(event.currentTarget.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="reset-confirm-password">Confirm new password</label>
          <input
            id="reset-confirm-password"
            name="confirmPassword"
            type="password"
            autoComplete="new-password"
            value={confirmation}
            disabled={submitting}
            onChange={(event) => setConfirmation(event.currentTarget.value)}
          />
        </div>
        <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
          Reset password
        </button>
      </form>
    </AuthCard>
  )
}
