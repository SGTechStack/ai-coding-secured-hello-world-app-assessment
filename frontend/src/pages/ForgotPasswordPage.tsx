import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { authApi } from '../api/auth';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Field } from '../components/ui/Field';
import { describeError } from '../lib/errors';

/** Story 6. The confirmation is identical whether or not the email exists. */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const response = await authApi.requestPasswordReset(email.trim());
      setMessage(response.message);
    } catch (err) {
      setError(describeError(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <Card>
        <h1>Reset your password</h1>
        {message ? (
          <div className="stack">
            <Alert tone="success">{message}</Alert>
            <p className="muted">
              In this reference build no real email is sent: the backend logs the reset link to
              its console. Open it, or go <Link to="/login">back to log in</Link>.
            </p>
          </div>
        ) : (
          <form className="stack" onSubmit={handleSubmit} noValidate>
            {error ? <Alert tone="error">{error}</Alert> : null}
            <Field
              label="Email"
              name="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
            <Button type="submit" block busy={submitting} disabled={!email}>
              Send reset link
            </Button>
            <p className="muted">
              <Link to="/login">Back to log in</Link>
            </p>
          </form>
        )}
      </Card>
    </div>
  );
}
