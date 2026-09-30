import { useState, type ChangeEvent, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { InvalidCredentialsError, TooManyAttemptsError, login } from '../api/authApi';
import './LoginPage.css';

// Loading state is held for at least this long even if the server responds
// faster, so the "Logging in..." feedback never flickers (spec: Story 1.9).
const MIN_LOADING_MS = 400;

const INVALID_CREDENTIALS_MESSAGE = 'Invalid username or password';
const TOO_MANY_ATTEMPTS_MESSAGE = 'Too many failed login attempts. Please try again later.';
const SERVER_UNAVAILABLE_MESSAGE = 'Unable to connect to the server. Please try again later.';

interface FieldErrors {
  username?: string;
  password?: string;
}

export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [banner, setBanner] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isPasswordVisible, setIsPasswordVisible] = useState(false);
  const navigate = useNavigate();

  function handleUsernameChange(event: ChangeEvent<HTMLInputElement>) {
    setUsername(event.target.value);
    setFieldErrors((previous) => (previous.username ? { ...previous, username: undefined } : previous));
    setBanner(null);
  }

  function handlePasswordChange(event: ChangeEvent<HTMLInputElement>) {
    setPassword(event.target.value);
    setFieldErrors((previous) => (previous.password ? { ...previous, password: undefined } : previous));
    setBanner(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const errors: FieldErrors = {};
    if (!username.trim()) {
      errors.username = 'Username is required';
    }
    if (!password) {
      errors.password = 'Password is required';
    }

    if (Object.keys(errors).length > 0) {
      // Submit-deferred validation only: no inline errors before this point,
      // and no network request when the form is obviously invalid.
      setFieldErrors(errors);
      return;
    }

    setFieldErrors({});
    setBanner(null);
    setIsSubmitting(true);

    const minLoadingDelay = new Promise<void>((resolve) => setTimeout(resolve, MIN_LOADING_MS));
    const [loginOutcome] = await Promise.allSettled([login({ username, password }), minLoadingDelay]);

    if (loginOutcome.status === 'fulfilled') {
      navigate('/');
      return;
    }

    if (loginOutcome.reason instanceof InvalidCredentialsError) {
      setBanner(INVALID_CREDENTIALS_MESSAGE);
    } else if (loginOutcome.reason instanceof TooManyAttemptsError) {
      setBanner(TOO_MANY_ATTEMPTS_MESSAGE);
    } else {
      setBanner(SERVER_UNAVAILABLE_MESSAGE);
    }
    setIsSubmitting(false);
  }

  return (
    <main className="login-page">
      <form className="login-form" onSubmit={handleSubmit} noValidate>
        <div className="login-heading">
          <div className="login-badge" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="4" y="10" width="16" height="10" rx="2" />
              <path d="M8 10V7a4 4 0 0 1 8 0v3" />
            </svg>
          </div>
          <h1>Welcome back</h1>
          <p className="login-subtitle">Enter your credentials to access your account</p>
        </div>

        {banner && (
          <p role="alert" className="login-banner">
            {banner}
          </p>
        )}

        <div className="form-field">
          <label htmlFor="username">Username</label>
          <div className="field-control">
            <span className="field-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="8" r="4" />
                <path d="M4 20c0-3.9 3.6-6 8-6s8 2.1 8 6" />
              </svg>
            </span>
            <input
              id="username"
              name="username"
              type="text"
              placeholder="e.g. johndoe"
              autoComplete="username"
              value={username}
              disabled={isSubmitting}
              onChange={handleUsernameChange}
              aria-invalid={Boolean(fieldErrors.username)}
              aria-describedby={fieldErrors.username ? 'username-error' : undefined}
            />
          </div>
          {fieldErrors.username && (
            <p id="username-error" className="field-error">
              {fieldErrors.username}
            </p>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="password">Password</label>
          <div className="field-control password-input">
            <span className="field-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
                <rect x="4" y="10" width="16" height="10" rx="2" />
                <path d="M8 10V7a4 4 0 0 1 8 0v3" />
              </svg>
            </span>
            <input
              id="password"
              name="password"
              type={isPasswordVisible ? 'text' : 'password'}
              placeholder="••••••••"
              autoComplete="current-password"
              value={password}
              disabled={isSubmitting}
              onChange={handlePasswordChange}
              aria-invalid={Boolean(fieldErrors.password)}
              aria-describedby={fieldErrors.password ? 'password-error' : undefined}
            />
            <button
              type="button"
              className="password-toggle"
              onClick={() => setIsPasswordVisible((visible) => !visible)}
              disabled={isSubmitting}
              aria-label={isPasswordVisible ? 'Hide password' : 'Show password'}
              aria-pressed={isPasswordVisible}
            >
              {isPasswordVisible ? (
                <svg
                  viewBox="0 0 24 24"
                  width="18"
                  height="18"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.75"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M3 3l18 18" />
                  <path d="M10.6 10.6a2 2 0 0 0 2.8 2.8" />
                  <path d="M9.4 5.5A10.6 10.6 0 0 1 12 5c5 0 8.5 3.5 10 7-.6 1.4-1.5 2.7-2.6 3.8M6.6 6.6C4.6 8 3.1 9.9 2 12c1.5 3.5 5 7 10 7 1 0 2-.1 2.9-.4" />
                </svg>
              ) : (
                <svg
                  viewBox="0 0 24 24"
                  width="18"
                  height="18"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.75"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M2 12c1.5-3.5 5-7 10-7s8.5 3.5 10 7c-1.5 3.5-5 7-10 7s-8.5-3.5-10-7z" />
                  <circle cx="12" cy="12" r="3" />
                </svg>
              )}
            </button>
          </div>
          {fieldErrors.password && (
            <p id="password-error" className="field-error">
              {fieldErrors.password}
            </p>
          )}
        </div>

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? (
            <>
              Logging in
              <span className="loading-ellipsis" aria-hidden="true">
                <span>.</span>
                <span>.</span>
                <span>.</span>
              </span>
            </>
          ) : (
            'Log in'
          )}
        </button>

        <div className="login-footer">
          <Link to="/reset-password">Forgot your password?</Link>
          <span>
            Don&apos;t have an account? <Link to="/register">Register</Link>
          </span>
        </div>
      </form>
    </main>
  );
}
