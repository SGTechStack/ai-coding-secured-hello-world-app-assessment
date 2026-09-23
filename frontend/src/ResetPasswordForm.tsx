import { useState, type FormEvent } from 'react';
import { api, ApiError } from './api';

export function ResetPasswordForm({ onNavigateToLogin }: { onNavigateToLogin: () => void }) {
  const initialToken = new URLSearchParams(window.location.search).get('token') ?? '';
  const [token, setToken] = useState(initialToken);
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.confirmPasswordReset({ token, newPassword });
      setSuccess(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reset password. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  if (success) {
    return (
      <div>
        <h1>Password reset</h1>
        <p role="status">Your password has been reset. You can now log in.</p>
        <button type="button" onClick={onNavigateToLogin}>
          Back to log in
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} aria-label="Set new password">
      <h1>Set a new password</h1>
      {error && <p role="alert">{error}</p>}
      <label htmlFor="reset-token">Reset token</label>
      <input id="reset-token" value={token} onChange={(e) => setToken(e.target.value)} required />
      <label htmlFor="reset-new-password">New password</label>
      <input
        id="reset-new-password"
        type="password"
        value={newPassword}
        onChange={(e) => setNewPassword(e.target.value)}
        autoComplete="new-password"
        minLength={12}
        required
      />
      <button type="submit" disabled={submitting}>
        {submitting ? 'Resetting…' : 'Reset password'}
      </button>
      <p>
        <button type="button" onClick={onNavigateToLogin}>
          Back to log in
        </button>
      </p>
    </form>
  );
}
