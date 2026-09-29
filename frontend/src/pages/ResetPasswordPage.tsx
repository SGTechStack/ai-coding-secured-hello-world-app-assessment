import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { confirmPasswordReset } from '../api/auth';
import { ApiError } from '../api/client';

const MIN_PASSWORD = 12;
const MAX_PASSWORD = 72;

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';
  const navigate = useNavigate();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!token) { setError('Missing reset token. Please use the link from your email.'); return; }
    if (password.length < MIN_PASSWORD) { setError(`Password must be at least ${MIN_PASSWORD} characters.`); return; }
    if (password.length > MAX_PASSWORD) { setError(`Password must not exceed ${MAX_PASSWORD} characters.`); return; }
    if (password !== confirm) { setError('Passwords do not match.'); return; }

    setLoading(true);
    try {
      await confirmPasswordReset(token, password);
      // Reset does NOT log the user in (Story 24). Redirect to login.
      navigate('/login', { state: { passwordReset: true } });
    } catch (err) {
      if (err instanceof ApiError) {
        setError('This reset link is invalid, expired, or has already been used. Please request a new one.');
      } else {
        setError('Something went wrong. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  if (!token) {
    return (
      <div style={{ maxWidth: 400 }}>
        <p role="alert" style={{ color: 'red' }}>Invalid reset link. Please request a new one.</p>
        <Link to="/forgot-password">Request password reset</Link>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 400 }}>
      <h1>Set new password</h1>
      <form onSubmit={(e) => { void handleSubmit(e); }} noValidate>
        <div style={{ marginBottom: '1rem' }}>
          <label htmlFor="password">New password (min {MIN_PASSWORD} chars)</label><br />
          <input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" required style={{ width: '100%', padding: '0.4rem' }} />
        </div>
        <div style={{ marginBottom: '1rem' }}>
          <label htmlFor="confirm">Confirm new password</label><br />
          <input id="confirm" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" required style={{ width: '100%', padding: '0.4rem' }} />
        </div>
        {error && <p role="alert" style={{ color: 'red' }}>{error}</p>}
        <button type="submit" disabled={loading}>{loading ? 'Resetting…' : 'Reset password'}</button>
      </form>
    </div>
  );
}
