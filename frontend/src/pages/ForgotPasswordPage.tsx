import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { api } from '../api/client';
import { throttledMessage } from './throttle';

/** The answer is the same whether or not the email is registered, so it says nothing about who has an Account. */
const SENT = 'If that email is registered, we have sent it a link to reset your password. The link works once, for 30 minutes.';

export function ForgotPasswordPage() {
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setError(null);
    try {
      await api.requestPasswordReset(String(form.get('email')));
      setSent(true);
    } catch (e) {
      setError(throttledMessage(e) ?? 'The reset link could not be requested. Check the email and try again.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main>
      <h1>Forgot your password?</h1>
      {sent ? (
        <p role="status">{SENT}</p>
      ) : (
        <form onSubmit={onSubmit}>
          <label>
            Email
            <input name="email" type="email" autoComplete="email" required />
          </label>
          {error && <p role="alert">{error}</p>}
          <button type="submit" disabled={submitting}>
            Send reset link
          </button>
        </form>
      )}
      <p>
        <Link to="/login">Back to log in</Link>
      </p>
    </main>
  );
}
