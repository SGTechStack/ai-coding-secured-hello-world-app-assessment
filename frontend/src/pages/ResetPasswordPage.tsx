import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'

export default function ResetPasswordPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const [newPassword, setNewPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await api.post('/api/auth/password-reset/confirm', { token, newPassword })
      navigate('/login')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Password reset failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main>
      <h1>Reset password</h1>
      {!token && (
        <p role="alert" style={{ color: 'crimson' }}>
          Missing or invalid reset link.
        </p>
      )}
      <form onSubmit={handleSubmit}>
        <label>
          New password
          <input
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
            minLength={12}
            aria-describedby="password-hint"
          />
        </label>
        <p id="password-hint">Must be at least 12 characters.</p>
        {error && (
          <p role="alert" style={{ color: 'crimson' }}>
            {error}
          </p>
        )}
        <button type="submit" disabled={submitting || !token}>
          {submitting ? 'Resetting…' : 'Reset password'}
        </button>
      </form>
      <p>
        <Link to="/login">Back to login</Link>
      </p>
    </main>
  )
}
