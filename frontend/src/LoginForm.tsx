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
    <form onSubmit={handleSubmit} aria-label="Log in">
      <h1>Log in</h1>
      {error && <p role="alert">{error}</p>}
      <label htmlFor="login-username">Username</label>
      <input
        id="login-username"
        value={username}
        onChange={(e) => setUsername(e.target.value)}
        autoComplete="username"
        required
      />
      <label htmlFor="login-password">Password</label>
      <input
        id="login-password"
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        autoComplete="current-password"
        required
      />
      <button type="submit" disabled={submitting}>
        {submitting ? 'Logging in…' : 'Log in'}
      </button>
      <p>
        <button type="button" onClick={onNavigateToForgotPassword}>
          Forgot password?
        </button>
      </p>
      <p>
        Need an account?{' '}
        <button type="button" onClick={onNavigateToRegister}>
          Register
        </button>
      </p>
    </form>
  );
}
