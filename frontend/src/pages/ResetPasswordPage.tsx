import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { api, ApiError, ErrorCode } from '../api/client';
import { PasswordRules } from './passwordRules';
import { throttledMessage } from './throttle';

/** Opened from the emailed link, which carries the Password reset token in its `token` query parameter. */
export function ResetPasswordPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  // Read once: the token then leaves the address bar, so it isn't kept in history or shown on screen.
  const [token] = useState(() => searchParams.get('token'));
  const [passwordErrors, setPasswordErrors] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [linkSpent, setLinkSpent] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (searchParams.has('token')) setSearchParams({}, { replace: true });
  }, [searchParams, setSearchParams]);

  if (!token || linkSpent) {
    return (
      <main>
        <h1>Reset your password</h1>
        <p role="alert">This reset link has expired or has already been used.</p>
        <p>
          <Link to="/forgot-password">Request a new link</Link>
        </p>
      </main>
    );
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setError(null);
    setPasswordErrors([]);
    try {
      await api.confirmPasswordReset(token!, String(form.get('newPassword')));
      navigate('/login', { replace: true, state: { passwordReset: true } });
    } catch (e) {
      if (e instanceof ApiError && e.code === ErrorCode.passwordResetTokenInvalid) {
        setLinkSpent(true);
      } else if (e instanceof ApiError && e.code === ErrorCode.validationFailed && e.problem.errors) {
        setPasswordErrors(e.problem.errors.map(({ message }) => message));
      } else if (e instanceof ApiError && e.code === ErrorCode.serviceUnavailable) {
        setError('Passwords can’t be checked right now. Please try again in a few minutes.');
      } else {
        setError(throttledMessage(e) ?? 'Your password could not be reset. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main>
      <h1>Reset your password</h1>
      <form onSubmit={onSubmit}>
        <label>
          New password
          <input
            name="newPassword"
            type="password"
            autoComplete="new-password"
            required
            aria-invalid={passwordErrors.length > 0}
            aria-describedby="password-rules"
          />
          {passwordErrors.map((message) => (
            <span key={message} role="alert">
              {message}
            </span>
          ))}
        </label>
        <PasswordRules id="password-rules" />
        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={submitting}>
          Set new password
        </button>
      </form>
    </main>
  );
}
