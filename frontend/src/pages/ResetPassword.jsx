import { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { api } from '../api.js';

export default function ResetPassword() {
  const [params] = useSearchParams();
  const [token, setToken] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    const t = params.get('token');
    if (t) setToken(t);
  }, [params]);

  async function submit(e) {
    e.preventDefault();
    setError('');
    setOk('');
    setBusy(true);
    try {
      const res = await api.post('/api/auth/password-reset/confirm', { token, newPassword });
      setOk(res.message + ' 🎪');
      setTimeout(() => navigate('/login'), 1400);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="ring">
      <h2>🎪 Set a New Password</h2>
      {error && <div className="flash err">{error}</div>}
      {ok && <div className="flash ok">{ok}</div>}
      <form onSubmit={submit}>
        <label>Reset Token</label>
        <input value={token} onChange={(e) => setToken(e.target.value)} />
        <label>New Password</label>
        <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} autoComplete="new-password" />
        <p className="hint">At least 12 characters.</p>
        <button className="act" disabled={busy}>{busy ? 'Updating…' : 'Change Password'}</button>
      </form>
    </div>
  );
}
