import { useState } from 'react';
import { api } from '../api.js';

export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setMsg('');
    try {
      const res = await api.post('/api/auth/password-reset/request', { email });
      setMsg(res.message);
    } catch (err) {
      // Even on error we show a generic message (enumeration resistance).
      setMsg('If that email is registered, a reset link has been sent.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="ring">
      <h2>🎭 Lost Your Password?</h2>
      {msg && <div className="flash ok">{msg}</div>}
      <form onSubmit={submit}>
        <label>Registered Email</label>
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
        <button className="act" disabled={busy}>{busy ? 'Sending…' : 'Send Reset Link'}</button>
      </form>
      <p className="hint">
        Dev note: the backend logs the reset link to its console (stubbed email). Copy the token
        into the reset page.
      </p>
    </div>
  );
}
