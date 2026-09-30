import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ApiError, confirmPasswordReset } from '../api/client';

const MIN_PASSWORD_LENGTH = 12;

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setMessage(null);

    if (!token) {
      setError('This reset link is missing its token.');
      return;
    }
    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      setError(`Password must be at least ${MIN_PASSWORD_LENGTH} characters.`);
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    setSubmitting(true);
    try {
      const response = await confirmPasswordReset(token, newPassword);
      setMessage(response.message);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  if (!token) {
    return (
      <section>
        <h2>Reset password</h2>
        <p role="alert">This reset link is missing its token. Request a new one.</p>
        <p>
          <Link to="/forgot-password">Request a new reset link</Link>
        </p>
      </section>
    );
  }

  if (message) {
    return (
      <section>
        <h2>Reset password</h2>
        <p role="status">{message}</p>
        <p>
          <Link to="/login">Back to login</Link>
        </p>
      </section>
    );
  }

  return (
    <section>
      <h2>Reset password</h2>
      <form onSubmit={handleSubmit}>
        <label htmlFor="reset-new-password">New password</label>
        <input
          id="reset-new-password"
          type="password"
          value={newPassword}
          onChange={(event) => setNewPassword(event.target.value)}
          minLength={MIN_PASSWORD_LENGTH}
          required
        />

        <label htmlFor="reset-confirm-password">Confirm new password</label>
        <input
          id="reset-confirm-password"
          type="password"
          value={confirmPassword}
          onChange={(event) => setConfirmPassword(event.target.value)}
          minLength={MIN_PASSWORD_LENGTH}
          required
        />

        {error && <p role="alert">{error}</p>}

        <button type="submit" disabled={submitting}>
          {submitting ? 'Resetting…' : 'Reset password'}
        </button>
      </form>
    </section>
  );
}
