import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { requestPasswordReset } from '../api/auth';
import { ApiError } from '../api/client';
import { FormField } from '../components/FormField';
import { useField } from '../hooks/useField';

export function ForgotPasswordPage() {
  const emailField = useField((v) => {
    if (!v.trim()) return 'Email is required.';
    return null;
  });
  const [submitted, setSubmitted] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setFormError(null);

    const err = emailField.touch();
    if (err) { setFormError(err); return; }

    setLoading(true);
    try {
      await requestPasswordReset(emailField.value.trim());
      setSubmitted(true);
    } catch (err) {
      if (err instanceof ApiError && err.isTooManyRequests) {
        setFormError('Too many requests — please wait before trying again.');
      } else {
        // All other errors: show the same generic success so we don't reveal
        // whether the email was registered (enumeration resistance).
        setSubmitted(true);
      }
    } finally {
      setLoading(false);
    }
  };

  if (submitted) {
    return (
      <div className="auth-page">
        <div className="auth-card" style={{ textAlign: 'center' }}>
          <span className="success-icon" role="img" aria-label="Email sent">📧</span>
          <h1 className="success-title">Check your email</h1>
          <p className="success-message">
            If that email address is registered, you'll receive a password reset link shortly.
            Check your inbox (and spam folder) and follow the link to reset your password.
          </p>
          <Link to="/login" className="btn btn-secondary">Back to Login</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1 className="auth-card-title">Forgot password?</h1>
        <p className="auth-card-subtitle">
          Enter your email address and we'll send you a reset link.
        </p>

        {formError && (
          <div role="alert" className="form-banner form-banner-error">
            <span className="form-banner-icon" aria-hidden="true">✕</span>
            {formError}
          </div>
        )}

        <form className="form" onSubmit={(e) => { void handleSubmit(e); }} noValidate>
          <FormField
            id="email"
            label="Email address"
            type="email"
            autoComplete="email"
            value={emailField.value}
            error={emailField.error}
            onChange={emailField.onChange}
            onBlur={emailField.onBlur}
          />

          <button
            type="submit"
            disabled={loading}
            className="btn btn-primary btn-full"
            style={{ marginTop: '0.5rem' }}
          >
            {loading && <span className="spinner" aria-hidden="true" />}
            {loading ? 'Sending…' : 'Send reset link'}
          </button>
        </form>

        <div className="auth-footer">
          <Link to="/login">← Back to Login</Link>
        </div>
      </div>
    </div>
  );
}
