import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { authApi } from '../api/auth';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Field } from '../components/ui/Field';
import { describeError, fieldErrorsOf } from '../lib/errors';
import { MIN_PASSWORD_LENGTH, passwordProblems } from '../lib/passwordPolicy';

/** Story 1. Client checks give fast feedback; the server remains the source of truth. */
export function RegisterPage() {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const localProblems = password ? passwordProblems(password, username) : [];
  const confirmMismatch = confirm.length > 0 && confirm !== password;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    if (localProblems.length > 0 || confirmMismatch) {
      setFieldErrors({
        ...(localProblems.length > 0 ? { password: localProblems[0] } : {}),
        ...(confirmMismatch ? { confirm: 'Passwords do not match' } : {}),
      });
      return;
    }
    setSubmitting(true);
    try {
      await authApi.register({ username: username.trim(), email: email.trim(), password });
      navigate('/login', { replace: true, state: { notice: 'Account created. You can log in now.' } });
    } catch (err) {
      const errors = fieldErrorsOf(err);
      setFieldErrors(errors);
      if (Object.keys(errors).length === 0) {
        setError(describeError(err));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <Card>
        <h1>Create an account</h1>
        <form className="stack" onSubmit={handleSubmit} noValidate>
          {error ? <Alert tone="error">{error}</Alert> : null}
          <Field
            label="Username"
            name="username"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            hint="3–32 characters: letters, digits, '.', '_' or '-'"
            error={fieldErrors.username}
            required
          />
          <Field
            label="Email"
            name="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            hint="Only used for password reset"
            error={fieldErrors.email}
            required
          />
          <Field
            label="Password"
            name="password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            hint={`At least ${MIN_PASSWORD_LENGTH} characters`}
            error={fieldErrors.password ?? (password ? localProblems[0] : undefined)}
            required
          />
          <Field
            label="Confirm password"
            name="confirm"
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            error={fieldErrors.confirm ?? (confirmMismatch ? 'Passwords do not match' : undefined)}
            required
          />
          <Button
            type="submit"
            block
            busy={submitting}
            disabled={!username || !email || !password || !confirm}
          >
            Register
          </Button>
        </form>
        <p className="muted" style={{ marginTop: 'var(--space-4)' }}>
          Already registered? <Link to="/login">Log in</Link>
        </p>
      </Card>
    </div>
  );
}
