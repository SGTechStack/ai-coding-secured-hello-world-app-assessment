import { useState, type FormEvent } from 'react';
import { api, ApiError } from './api';

export function ForgotPasswordForm({ onNavigateToLogin }: { onNavigateToLogin: () => void }) {
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    try {
      const response = await api.requestPasswordReset({ email });
      setMessage(response.message);
    } catch (err) {
      // Even on error, avoid leaking whether the email exists.
      setMessage(err instanceof ApiError ? err.message : 'If that email is registered, a reset link has been sent');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="card">
      <form onSubmit={handleSubmit} aria-label="Request password reset">
        <h1>Reset your password</h1>
        {message && (
          <p className="alert alert--success" role="status">
            {message}
          </p>
        )}
        <div className="field">
          <label htmlFor="forgot-email">Email</label>
          <input
            id="forgot-email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
          />
        </div>
        <div className="actions">
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Sending…' : 'Send reset link'}
          </button>
        </div>
        <p className="footnote">
          <button type="button" className="btn-link" onClick={onNavigateToLogin}>
            Back to log in
          </button>
        </p>
      </form>
    </div>
  );
}
