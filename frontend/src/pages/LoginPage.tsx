import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router';
import { useAuth } from '../auth/useAuth';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Field } from '../components/ui/Field';
import { describeError } from '../lib/errors';

interface LocationState {
  from?: string;
  notice?: string;
}

/** Story 2. Errors are shown exactly as the server phrases them (always generic). */
export function LoginPage() {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const state = (location.state ?? {}) as LocationState;

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (isAuthenticated) {
    return <Navigate to={state.from ?? '/'} replace />;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login({ username: username.trim(), password });
      navigate(state.from ?? '/', { replace: true });
    } catch (err) {
      setError(describeError(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <Card>
        <h1>Log in</h1>
        {state.notice ? <Alert tone="success">{state.notice}</Alert> : null}
        <form className="stack" onSubmit={handleSubmit} noValidate>
          {error ? <Alert tone="error">{error}</Alert> : null}
          <Field
            label="Username"
            name="username"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
          <Field
            label="Password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <Button type="submit" block busy={submitting} disabled={!username || !password}>
            Log in
          </Button>
        </form>
        <p className="muted" style={{ marginTop: 'var(--space-4)' }}>
          <Link to="/forgot-password">Forgot your password?</Link> · No account?{' '}
          <Link to="/register">Register</Link>
        </p>
      </Card>
    </div>
  );
}
