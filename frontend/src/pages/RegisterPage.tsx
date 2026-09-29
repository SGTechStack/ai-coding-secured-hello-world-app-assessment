import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { register } from '../api/auth';
import { ApiError } from '../api/client';

const MIN_PASSWORD = 12;
const MAX_PASSWORD = 72;

export function RegisterPage() {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!username.trim()) { setError('Username is required.'); return; }
    if (!email.trim()) { setError('Email is required.'); return; }
    if (password.length < MIN_PASSWORD) {
      setError(`Password must be at least ${MIN_PASSWORD} characters.`);
      return;
    }
    if (password.length > MAX_PASSWORD) {
      setError(`Password must not exceed ${MAX_PASSWORD} characters.`);
      return;
    }
    if (password !== confirm) { setError('Passwords do not match.'); return; }

    setLoading(true);
    try {
      await register(username.trim(), email.trim(), password);
      navigate('/login', { state: { registered: true } });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.detail);
      } else {
        setError('Registration failed. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 400 }}>
      <h1>Create account</h1>
      <form onSubmit={(e) => { void handleSubmit(e); }} noValidate>
        <div style={{ marginBottom: '1rem' }}>
          <label htmlFor="username">Username</label><br />
          <input id="username" type="text" value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required style={{ width: '100%', padding: '0.4rem' }} />
        </div>
        <div style={{ marginBottom: '1rem' }}>
          <label htmlFor="email">Email</label><br />
          <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required style={{ width: '100%', padding: '0.4rem' }} />
        </div>
        <div style={{ marginBottom: '1rem' }}>
          <label htmlFor="password">Password (min {MIN_PASSWORD} chars)</label><br />
          <input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" required style={{ width: '100%', padding: '0.4rem' }} />
        </div>
        <div style={{ marginBottom: '1rem' }}>
          <label htmlFor="confirm">Confirm password</label><br />
          <input id="confirm" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" required style={{ width: '100%', padding: '0.4rem' }} />
        </div>
        {error && <p role="alert" style={{ color: 'red' }}>{error}</p>}
        <button type="submit" disabled={loading}>{loading ? 'Creating account…' : 'Register'}</button>
      </form>
      <p style={{ marginTop: '1rem' }}><Link to="/login">Already have an account?</Link></p>
    </div>
  );
}
