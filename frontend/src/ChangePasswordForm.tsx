import { useState, type FormEvent } from 'react';
import { api, ApiError } from './api';
import { useAuth } from './AuthContext';

/**
 * Shown when the backend reports PASSWORD_CHANGE_REQUIRED (the seeded
 * bootstrap admin, or anyone else with forcePasswordChange set). This is
 * the only form of authenticated access available until submitted —
 * ForcePasswordChangeFilter rejects every other endpoint with 403.
 */
export function ChangePasswordForm() {
  const { logout, refresh } = useAuth();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.changePassword({ currentPassword, newPassword });
      await refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not change password. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="card">
      <form onSubmit={handleSubmit} aria-label="Change password">
        <h1>Set a new password</h1>
        <p className="footnote">Your password must be changed before you can continue.</p>
        {error && <p className="alert" role="alert">{error}</p>}
        <div className="field">
          <label htmlFor="change-current-password">Current password</label>
          <input
            id="change-current-password"
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            autoComplete="current-password"
            aria-invalid={error ? 'true' : undefined}
            required
          />
        </div>
        <div className="field">
          <label htmlFor="change-new-password">New password</label>
          <input
            id="change-new-password"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            autoComplete="new-password"
            minLength={12}
            aria-invalid={error ? 'true' : undefined}
            required
          />
        </div>
        <div className="actions">
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Changing password…' : 'Change password'}
          </button>
        </div>
        <p className="footnote">
          <button type="button" className="btn-link" onClick={() => logout()}>
            Log out instead
          </button>
        </p>
      </form>
    </div>
  );
}
