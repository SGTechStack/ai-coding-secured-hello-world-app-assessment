import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router';
import { ApiError, ErrorCode } from '../api/client';
import { AccountLoadError, useAuth } from '../auth/AuthContext';
import { throttledMessage } from './throttle';

export function LoginPage() {
  const { me, login } = useAuth();
  const navigate = useNavigate();
  const arrivedFrom = useLocation().state as { registered?: boolean; passwordReset?: boolean } | null;
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (me) return <Navigate to="/" replace />;

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setError(null);
    try {
      await login(String(form.get('username')), String(form.get('password')));
      navigate('/', { replace: true });
    } catch (e) {
      if (e instanceof AccountLoadError) {
        // The session exists; a reload loads the Account again on startup.
        setError('You are logged in, but your account details could not be loaded. Reload the page to continue.');
      } else if (e instanceof ApiError && e.code === ErrorCode.invalidCredentials) {
        setError('Invalid username or password.');
      } else {
        setError(throttledMessage(e) ?? 'Login failed. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main>
      <h1>Log in</h1>
      {arrivedFrom?.registered && <p role="status">Account created. Please log in.</p>}
      {arrivedFrom?.passwordReset && <p role="status">Your password has been reset. Please log in.</p>}
      <form onSubmit={onSubmit}>
        <label>
          Username
          <input name="username" autoComplete="username" required />
        </label>
        <label>
          Password
          <input name="password" type="password" autoComplete="current-password" required />
        </label>
        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={submitting}>
          Log in
        </button>
      </form>
      <p>
        <Link to="/forgot-password">Forgot your password?</Link>
      </p>
      <p>
        No account yet? <Link to="/register">Register</Link>
      </p>
    </main>
  );
}
