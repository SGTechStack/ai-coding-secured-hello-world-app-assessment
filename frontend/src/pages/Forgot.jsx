import { useState } from 'react';
import { api } from '../api.js';

export default function Forgot() {
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const res = await api.requestReset(email);
      setMessage(res.message);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <form onSubmit={submit}>
      <h1>Forgot password</h1>
      {message && <p className="notice">{message}</p>}
      {error && <p className="error" role="alert">{error}</p>}
      <label>Email
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
      </label>
      <button type="submit">Send reset link</button>
    </form>
  );
}
