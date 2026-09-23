import { useState, type FormEvent } from 'react';
import { api, ApiError } from './api';

interface RegisterFormProps {
  onRegistered: () => void;
  onNavigateToLogin: () => void;
}

export function RegisterForm({ onRegistered, onNavigateToLogin }: RegisterFormProps) {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.register({ username, email, password });
      onRegistered();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Registration failed. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} aria-label="Register">
      <h1>Register</h1>
      {error && <p role="alert">{error}</p>}
      <label htmlFor="register-username">Username</label>
      <input
        id="register-username"
        value={username}
        onChange={(e) => setUsername(e.target.value)}
        autoComplete="username"
        required
      />
      <label htmlFor="register-email">Email</label>
      <input
        id="register-email"
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        autoComplete="email"
        required
      />
      <label htmlFor="register-password">Password</label>
      <input
        id="register-password"
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        autoComplete="new-password"
        minLength={12}
        required
      />
      <button type="submit" disabled={submitting}>
        {submitting ? 'Registering…' : 'Register'}
      </button>
      <p>
        Already have an account?{' '}
        <button type="button" onClick={onNavigateToLogin}>
          Log in
        </button>
      </p>
    </form>
  );
}
