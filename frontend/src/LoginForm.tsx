import { useState, type FormEvent } from 'react';
import { api, ApiError } from './api';

interface LoginFormProps {
  onLoggedIn: () => void;
  onNavigateToRegister: () => void;
  onNavigateToForgotPassword: () => void;
}

export function LoginForm({ onLoggedIn, onNavigateToRegister, onNavigateToForgotPassword }: LoginFormProps) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.login({ username, password });
      onLoggedIn();
    } catch (err) {
      // Deliberately generic: never surface whether the username exists.
      setError(err instanceof ApiError ? err.message : 'Login failed. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="card">
      <form onSubmit={handleSubmit} aria-label="Log in">
        <h1>Log in</h1>
        {error && <p className="alert" role="alert">{error}</p>}
        <div className="field">
          <label htmlFor="login-username">Username</label>
          <input
            id="login-username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            aria-invalid={error ? 'true' : undefined}
            required
          />
        </div>
        <div className="field">
          <label htmlFor="login-password">Password</label>
          <input
            id="login-password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            aria-invalid={error ? 'true' : undefined}
            required
          />
        </div>
        <div className="actions">
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Logging in…' : 'Log in'}
          </button>
        </div>
        <p className="footnote">
          <button type="button" className="btn-link" onClick={onNavigateToForgotPassword}>
            Forgot password?
          </button>
        </p>
        <p className="footnote">
          Need an account?{' '}
          <button type="button" className="btn-link" onClick={onNavigateToRegister}>
            Register
          </button>
        </p>
      </form>
    </div>
  );
}
