import { useState, type ChangeEvent, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { PasswordResetError, confirmPasswordReset } from '../api/passwordResetApi';
import './PasswordResetPage.css';

const MINIMUM_PASSWORD_LENGTH = 12;
const SERVER_UNAVAILABLE_MESSAGE = 'Unable to connect to the server. Please try again later.';

export default function PasswordResetConfirmPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();

  const [newPassword, setNewPassword] = useState('');
  const [fieldError, setFieldError] = useState<string | undefined>(undefined);
  const [banner, setBanner] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

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

  function handlePasswordChange(event: ChangeEvent<HTMLInputElement>) {
    setNewPassword(event.target.value);
    setFieldError(undefined);
    setBanner(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!newPassword) {
      setFieldError('Password is required');
      return;
    }
    if (newPassword.length < MINIMUM_PASSWORD_LENGTH) {
      setFieldError(`Password must be at least ${MINIMUM_PASSWORD_LENGTH} characters long`);
      return;
    }

    setFieldError(undefined);
    setBanner(null);
    setIsSubmitting(true);

    try {
      await confirmPasswordReset(token as string, newPassword);
      navigate('/login', { replace: true });
    } catch (error) {
      setBanner(error instanceof PasswordResetError ? error.message : SERVER_UNAVAILABLE_MESSAGE);
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
