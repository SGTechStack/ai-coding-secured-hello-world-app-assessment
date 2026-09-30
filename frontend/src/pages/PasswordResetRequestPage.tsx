import { useState, type ChangeEvent, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { messageFor } from '../api/errors';
import { requestPasswordReset } from '../api/passwordResetApi';
import { LIMITS, validateEmail } from '../validation';
import './PasswordResetPage.css';

const SUCCESS_MESSAGE = "If an account exists for that email, we've sent a link to reset your password.";

export default function PasswordResetRequestPage() {
  const [email, setEmail] = useState('');
  const [fieldError, setFieldError] = useState<string | undefined>(undefined);
  const [banner, setBanner] = useState<string | null>(null);
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function handleEmailChange(event: ChangeEvent<HTMLInputElement>) {
    setEmail(event.target.value);
    setFieldError(undefined);
    setBanner(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const emailError = validateEmail(email);
    if (emailError) {
      setFieldError(emailError);
      return;
    }

    setFieldError(undefined);
    setBanner(null);
    setIsSubmitting(true);

    try {
      await requestPasswordReset(email);
      // The response is identical whether or not the email is registered
      // (anti-enumeration), so this success message is shown unconditionally
      // once the server actually received the request.
      setIsSubmitted(true);
    } catch (error) {
      setBanner(messageFor(error));
      setIsSubmitting(false);
    }
  }

  return (
    <main className="reset-page">
      <form className="reset-form" onSubmit={handleSubmit} noValidate>
        <div className="reset-heading">
          <h1>Reset your password</h1>
          <p className="reset-subtitle">Enter your account email and we'll send you a reset link</p>
        </div>

        {banner && (
          <p role="alert" className="reset-banner">
            {banner}
          </p>
        )}

        {isSubmitted && (
          <p role="status" className="reset-success">
            {SUCCESS_MESSAGE}
          </p>
        )}

        <div className="form-field">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            maxLength={LIMITS.emailMax}
            value={email}
            disabled={isSubmitting || isSubmitted}
            onChange={handleEmailChange}
            aria-invalid={Boolean(fieldError)}
            aria-describedby={fieldError ? 'email-error' : undefined}
          />
          {fieldError && (
            <p id="email-error" className="field-error">
              {fieldError}
            </p>
          )}
        </div>

        <button type="submit" disabled={isSubmitting || isSubmitted}>
          {isSubmitting ? 'Sending...' : 'Send reset link'}
        </button>

        <p className="reset-footer">
          <Link to="/login">Back to log in</Link>
        </p>
      </form>
    </main>
  );
}
