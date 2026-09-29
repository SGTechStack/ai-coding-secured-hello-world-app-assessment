import { useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { register } from '../api/auth';
import { ApiError } from '../api/client';
import { FormField } from '../components/FormField';
import { PasswordField } from '../components/PasswordField';
import { useField } from '../hooks/useField';
import { buildRequirements, validatePassword } from '../utils/passwordPolicy';

export function RegisterPage() {
  const usernameField = useField((v) => (!v.trim() ? 'Username is required.' : null));
  const emailField = useField((v) => {
    if (!v.trim()) return 'Email is required.';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.trim())) return 'Enter a valid email address.';
    return null;
  });
  const passwordField = useField(validatePassword);
  // Confirm validates matching only; length is the password field's concern
  const confirmField = useField((v) => {
    if (!v) return 'Please confirm your password.';
    if (v !== passwordField.value) return 'Passwords do not match.';
    return null;
  });

  const [formError, setFormError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [registered, setRegistered] = useState(false);

  const usernameRef = useRef<HTMLInputElement>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setFormError(null);

    const err1 = usernameField.touch();
    const err2 = emailField.touch();
    const err3 = passwordField.touch();
    // Re-evaluate confirm against the CURRENT password value
    const confirmErr = (() => {
      const v = confirmField.value;
      if (!v) return 'Please confirm your password.';
      if (v !== passwordField.value) return 'Passwords do not match.';
      return null;
    })();
    // Sync the confirm field's visible error
    if (confirmErr) confirmField.touch();

    const firstError = err1 ?? err2 ?? err3 ?? confirmErr;
    if (firstError) {
      setFormError(firstError);
      usernameRef.current?.focus();
      return;
    }

    setLoading(true);
    try {
      await register(usernameField.value.trim(), emailField.value.trim(), passwordField.value);
      setRegistered(true);
    } catch (err) {
      if (err instanceof ApiError) {
        setFormError(err.detail);
      } else {
        setFormError('Registration failed. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  if (registered) {
    return (
      <div className="auth-page">
        <div className="auth-card" style={{ textAlign: 'center' }}>
          <span className="success-icon" role="img" aria-label="Success">🎉</span>
          <h1 className="success-title">Account created!</h1>
          <p className="success-message">
            Your account has been created. You can now sign in.
          </p>
          <Link to="/login" className="btn btn-primary">Go to Login</Link>
        </div>
      </div>
    );
  }

  const pwRequirements = buildRequirements(passwordField.value);

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1 className="auth-card-title">Create account</h1>
        <p className="auth-card-subtitle">Fill in the details below to register.</p>

        {formError && (
          <div role="alert" className="form-banner form-banner-error">
            <span className="form-banner-icon" aria-hidden="true">✕</span>
            {formError}
          </div>
        )}

        <form className="form" onSubmit={(e) => { void handleSubmit(e); }} noValidate>
          <FormField
            id="username"
            label="Username"
            type="text"
            autoComplete="username"
            value={usernameField.value}
            error={usernameField.error}
            onChange={usernameField.onChange}
            onBlur={usernameField.onBlur}
            ref={usernameRef}
          />

          <FormField
            id="email"
            label="Email"
            type="email"
            autoComplete="email"
            value={emailField.value}
            error={emailField.error}
            onChange={emailField.onChange}
            onBlur={emailField.onBlur}
          />

          <PasswordField
            id="password"
            label="Password"
            value={passwordField.value}
            error={passwordField.error}
            autoComplete="new-password"
            onChange={passwordField.onChange}
            onBlur={passwordField.onBlur}
            requirements={passwordField.touched || passwordField.value.length > 0 ? pwRequirements : undefined}
          />

          <PasswordField
            id="confirm"
            label="Confirm password"
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
            {loading ? 'Creating account…' : 'Register'}
          </button>
        </form>

        <div className="auth-footer">
          Already have an account? <Link to="/login">Sign in</Link>
        </div>
      </div>
    </div>
  );
}
