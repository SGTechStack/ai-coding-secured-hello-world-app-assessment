import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { authApi } from '../api/auth';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Field } from '../components/ui/Field';
import { describeError, fieldErrorsOf } from '../lib/errors';
import { MIN_PASSWORD_LENGTH, passwordProblems } from '../lib/passwordPolicy';

/** Story 7. The token arrives in the query string of the emailed link. */
export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';

  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [done, setDone] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);

  const localProblems = password ? passwordProblems(password) : [];
  const confirmMismatch = confirm.length > 0 && confirm !== password;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldError(undefined);
    if (localProblems.length > 0 || confirmMismatch) {
      setFieldError(localProblems[0] ?? 'Passwords do not match');
      return;
    }
    setSubmitting(true);
    try {
      const response = await authApi.confirmPasswordReset(token, password);
      setDone(response.message);
    } catch (err) {
      const errors = fieldErrorsOf(err);
      const passwordError = errors.newPassword ?? errors.password;
      if (passwordError) {
        setFieldError(passwordError);
      } else {
        setError(describeError(err));
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (!token) {
    return (
      <div className="auth-page">
        <Card>
          <h1>Reset your password</h1>
          <Alert tone="error">This link is missing its reset token.</Alert>
          <p>
            <Link to="/forgot-password">Request a new link</Link>
          </p>
        </Card>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <Card>
        <h1>Choose a new password</h1>
        {done ? (
          <div className="stack">
            <Alert tone="success">{done}</Alert>
            <p>
              <Link to="/login">Go to log in</Link>
            </p>
          </div>
        ) : (
          <form className="stack" onSubmit={handleSubmit} noValidate>
            {error ? <Alert tone="error">{error}</Alert> : null}
            <Field
              label="New password"
              name="newPassword"
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              hint={`At least ${MIN_PASSWORD_LENGTH} characters`}
              error={fieldError ?? (password ? localProblems[0] : undefined)}
              required
            />
            <Field
              label="Confirm new password"
              name="confirm"
              type="password"
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              error={confirmMismatch ? 'Passwords do not match' : undefined}
              required
            />
            <Button type="submit" block busy={submitting} disabled={!password || !confirm}>
              Set new password
            </Button>
          </form>
        )}
      </Card>
    </div>
  );
}
