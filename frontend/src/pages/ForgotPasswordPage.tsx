import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { requestPasswordReset } from '../api/auth';
import { ApiError } from '../api/client';

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!email.trim()) { setError('Email is required.'); return; }
    setLoading(true);
    try {
      await requestPasswordReset(email.trim());
      // Always show generic success — do not indicate whether the email is registered
      setSubmitted(true);
    } catch (err) {
      if (err instanceof ApiError && err.isTooManyRequests) {
        setError('Too many requests — please wait before trying again.');
      } else {
        // Treat all other errors as generic; do not expose whether the email exists
        setSubmitted(true);
      }
    } finally {
      setLoading(false);
    }
  };

  if (submitted) {
    return (
      <div style={{ maxWidth: 400 }}>
        <h1>Check your email</h1>
        <p>If that email address is registered, you'll receive a password reset link shortly.</p>
        <p><Link to="/login">Back to login</Link></p>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 400 }}>
      <h1>Forgot password</h1>
      <form onSubmit={(e) => { void handleSubmit(e); }} noValidate>
        <div style={{ marginBottom: '1rem' }}>
          <label htmlFor="email">Email address</label><br />
          <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required style={{ width: '100%', padding: '0.4rem' }} />
        </div>
        {error && <p role="alert" style={{ color: 'red' }}>{error}</p>}
        <button type="submit" disabled={loading}>{loading ? 'Sending…' : 'Send reset link'}</button>
      </form>
      <p style={{ marginTop: '1rem' }}><Link to="/login">Back to login</Link></p>
    </div>
  );
}
