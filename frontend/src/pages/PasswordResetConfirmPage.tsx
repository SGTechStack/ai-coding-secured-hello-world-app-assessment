import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { messageFor } from '../api/errors';
import { confirmPasswordReset } from '../api/passwordResetApi';
import { LIMITS, validateNewPassword } from '../validation';
import './PasswordResetPage.css';

export default function PasswordResetConfirmPage() {
  const [searchParams] = useSearchParams();
  // Captured once into state, so stripping it from the URL below doesn't lose it.
  const [token] = useState(() => searchParams.get('token'));
  const navigate = useNavigate();

  const [newPassword, setNewPassword] = useState('');
  const [fieldError, setFieldError] = useState<string | undefined>(undefined);
  const [banner, setBanner] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Strip the reset token from the address bar and session history so it
  // can't leak via history, screenshots, shoulder-surfing or a copied URL.
  // The current history state is kept so React Router's own entry
  // bookkeeping (key/idx) stays intact.
  useEffect(() => {
    if (window.location.search) {
      window.history.replaceState(window.history.state, '', window.location.pathname);
    }
  }, []);

  if (!token) {
    return (
      <main className="reset-page">
        <div className="reset-form">
          <div className="reset-heading">
            <h1>Invalid reset link</h1>
            <p className="reset-subtitle">This password reset link is missing its token.</p>
          </div>
          <p className="reset-footer">
            <Link to="/reset-password">Request a new link</Link>
          </p>
        </div>
      </main>
    );
  }

  const resetToken: string = token;

  function handlePasswordChange(event: ChangeEvent<HTMLInputElement>) {
    setNewPassword(event.target.value);
    setFieldError(undefined);
    setBanner(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const passwordError = validateNewPassword(newPassword);
    if (passwordError) {
      setFieldError(passwordError);
      return;
    }

    setFieldError(undefined);
    setBanner(null);
    setIsSubmitting(true);

    try {
      await confirmPasswordReset(resetToken, newPassword);
      navigate('/login', { replace: true, state: { notice: 'passwordReset' } });
    } catch (error) {
      setBanner(messageFor(error));
      setIsSubmitting(false);
    }
  }

  return (
    <main className="reset-page">
      <form className="reset-form" onSubmit={handleSubmit} noValidate>
        <div className="reset-heading">
          <h1>Choose a new password</h1>
          <p className="reset-subtitle">Enter a new password for your account</p>
        </div>

        {banner && (
          <p role="alert" className="reset-banner">
            {banner}
          </p>
        )}

        <div className="form-field">
          <label htmlFor="newPassword">New password</label>
          <input
            id="newPassword"
            name="newPassword"
            type="password"
            autoComplete="new-password"
            maxLength={LIMITS.passwordInputMax}
            value={newPassword}
            disabled={isSubmitting}
            onChange={handlePasswordChange}
            aria-invalid={Boolean(fieldError)}
            aria-describedby={fieldError ? 'newPassword-error' : undefined}
          />
          {fieldError && (
            <p id="newPassword-error" className="field-error">
              {fieldError}
            </p>
          )}
        </div>

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Resetting...' : 'Reset password'}
        </button>

        <p className="reset-footer">
          <Link to="/login">Back to log in</Link>
        </p>
      </form>
    </main>
  );
}
