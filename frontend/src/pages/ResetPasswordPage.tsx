import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { confirmPasswordReset } from '../api/auth';
import { ApiError } from '../api/client';
import { PasswordField } from '../components/PasswordField';
import { useField } from '../hooks/useField';

const MIN_PASSWORD = 12;
const MAX_PASSWORD = 72;

function validatePassword(v: string): string | null {
  if (!v) return 'Password is required.';
  if (v.length < MIN_PASSWORD) return `Password must be at least ${MIN_PASSWORD} characters.`;
  if (v.length > MAX_PASSWORD) return `Password must not exceed ${MAX_PASSWORD} characters.`;
  return null;
}

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';
  const navigate = useNavigate();

  const passwordField = useField(validatePassword);
  // Confirm only checks matching; length is the password field's concern
  const confirmField = useField((v) => {
    if (!v) return 'Please confirm your password.';
    if (v !== passwordField.value) return 'Passwords do not match.';
    return null;
  });

  const [formError, setFormError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  if (!token) {
    return (
      <div className="auth-page">
        <div className="auth-card">
          <p role="alert" className="form-banner form-banner-error">
            <span aria-hidden="true">✕ </span>
            Invalid reset link. Please use the link from your email or request a new one.
          </p>
          <div className="auth-footer">
            <Link to="/forgot-password">Request a new reset link</Link>
          </div>
        </div>
      </div>
    );
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setFormError(null);

    const err1 = passwordField.touch();
    const confirmErr = (() => {
      const v = confirmField.value;
      if (!v) return 'Please confirm your password.';
      if (v !== passwordField.value) return 'Passwords do not match.';
      return null;
    })();
    if (confirmErr) confirmField.touch();

    const firstError = err1 ?? confirmErr;
    if (firstError) { setFormError(firstError); return; }

    setLoading(true);
    try {
      await confirmPasswordReset(token, passwordField.value);
      // Reset does NOT log the user in (Story 24). Navigate to login.
      navigate('/login', { state: { passwordReset: true } });
    } catch (err) {
      if (err instanceof ApiError) {
        setFormError('This reset link is invalid, expired, or has already been used. Please request a new one.');
      } else {
        setFormError('Something went wrong. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  const pwRequirements = [
    { label: `At least ${MIN_PASSWORD} characters`, met: passwordField.value.length >= MIN_PASSWORD },
    { label: `No more than ${MAX_PASSWORD} characters`, met: passwordField.value.length <= MAX_PASSWORD && passwordField.value.length > 0 },
  ];

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1 className="auth-card-title">Set new password</h1>
        <p className="auth-card-subtitle">Choose a strong password for your account.</p>

        {formError && (
          <div role="alert" className="form-banner form-banner-error">
            <span className="form-banner-icon" aria-hidden="true">✕</span>
            {formError}
          </div>
        )}

        <form className="form" onSubmit={(e) => { void handleSubmit(e); }} noValidate>
          <PasswordField
            id="password"
            label="New password"
            value={passwordField.value}
            error={passwordField.error}
            autoComplete="new-password"
            onChange={passwordField.onChange}
            onBlur={passwordField.onBlur}
            requirements={passwordField.touched || passwordField.value ? pwRequirements : undefined}
          />

          <PasswordField
            id="confirm"
            label="Confirm new password"
            value={confirmField.value}
            error={confirmField.error}
            autoComplete="new-password"
            onChange={confirmField.onChange}
            onBlur={confirmField.onBlur}
          />

          <button
            type="submit"
            disabled={loading}
            className="btn btn-primary btn-full"
            style={{ marginTop: '0.5rem' }}
          >
            {loading && <span className="spinner" aria-hidden="true" />}
            {loading ? 'Resetting…' : 'Reset password'}
          </button>
        </form>

        <div className="auth-footer">
          <Link to="/forgot-password">Request a new reset link</Link>
        </div>
      </div>
    </div>
  );
}
