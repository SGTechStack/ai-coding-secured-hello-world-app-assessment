import { useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { FormField } from '../components/FormField';
import { PasswordField } from '../components/PasswordField';
import { useAuth } from '../contexts/AuthContext';
import { useField } from '../hooks/useField';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const usernameField = useField((v) => (!v.trim() ? 'Username is required.' : null));
  const passwordField = useField((v) => (!v ? 'Password is required.' : null));

  const [formError, setFormError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const usernameRef = useRef<HTMLInputElement>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setFormError(null);

    const err1 = usernameField.touch();
    const err2 = passwordField.touch();
    const firstError = err1 ?? err2;
    if (firstError) {
      setFormError(firstError);
      usernameRef.current?.focus();
      return;
    }

    setLoading(true);
    try {
      await login(usernameField.value.trim(), passwordField.value);
      navigate('/');
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.isTooManyRequests) {
          setFormError('Too many login attempts — please wait before trying again.');
        } else {
          // Generic message: do not distinguish wrong password / unknown user / locked
          setFormError('Invalid credentials. Please check your username and password.');
        }
      } else {
        setFormError('Something went wrong. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1 className="auth-card-title">Sign in</h1>
        <p className="auth-card-subtitle">Enter your credentials to access your account.</p>

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

          <PasswordField
            id="password"
            label="Password"
            value={passwordField.value}
            error={passwordField.error}
            autoComplete="current-password"
            onChange={passwordField.onChange}
            onBlur={passwordField.onBlur}
          />

          <button
            type="submit"
            disabled={loading}
            className="btn btn-primary btn-full"
            style={{ marginTop: '0.5rem' }}
          >
            {loading && <span className="spinner" aria-hidden="true" />}
            {loading ? 'Signing in…' : 'Login'}
          </button>
        </form>

        <div className="auth-footer">
          <Link to="/forgot-password">Forgot your password?</Link>
          {' · '}
          <Link to="/register">Create an account</Link>
        </div>
      </div>
    </div>
  );
}
