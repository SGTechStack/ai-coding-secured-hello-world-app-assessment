import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { api } from '../api.js';

export default function Login({ onLoggedIn }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  async function submit(e) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      const res = await api.post('/api/auth/login', { username, password });
      onLoggedIn({ username: res.username });
      navigate('/');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="ring">
      <h2>🤹 Come On In!</h2>
      {error && <div className="flash err">{error}</div>}
      <form onSubmit={submit}>
        <label>Username</label>
        <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" />
        <label>Password</label>
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
        <button className="act" disabled={busy}>{busy ? 'Juggling…' : 'Enter the Tent'}</button>
      </form>
      <p className="hint">
        No ticket yet? <Link className="inline-link" to="/register">Register here</Link> ·{' '}
        <Link className="inline-link" to="/forgot-password">Lost your password?</Link>
      </p>
    </div>
  );
}
