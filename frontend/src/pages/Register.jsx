import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { api } from '../api.js';

export default function Register() {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  async function submit(e) {
    e.preventDefault();
    setError('');
    setOk('');
    setBusy(true);
    try {
      await api.post('/api/auth/register', { username, email, password });
      setOk('🎉 Account created! Sending you to the login…');
      setTimeout(() => navigate('/login'), 1200);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="ring">
      <h2>🎨 Join the Circus</h2>
      {error && <div className="flash err">{error}</div>}
      {ok && <div className="flash ok">{ok}</div>}
      <form onSubmit={submit}>
        <label>Username</label>
        <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" />
        <label>Email</label>
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
        <label>Password</label>
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" />
        <p className="hint">Must be at least 12 characters — make it a showstopper.</p>
        <button className="act" disabled={busy}>{busy ? 'Setting up…' : 'Grab My Ticket'}</button>
      </form>
      <p className="hint">
        Already a performer? <Link className="inline-link" to="/login">Login</Link>
      </p>
    </div>
  );
}
