import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api.js';

export default function ResetPassword() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await api.confirmReset(token, password);
      setDone(true);
    } catch (err) {
      setError(err.message);
    }
  };

  if (done) {
    return <p className="notice">Password updated. <Link to="/login">Log in</Link></p>;
  }

  return (
    <form onSubmit={submit}>
      <h1>Choose a new password</h1>
      {!token && <p className="error">Missing reset token.</p>}
      {error && <p className="error" role="alert">{error}</p>}
      <label>New password (min 12 characters)
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
          autoComplete="new-password" minLength={12} required />
      </label>
      <button type="submit" disabled={!token}>Update password</button>
    </form>
  );
}
