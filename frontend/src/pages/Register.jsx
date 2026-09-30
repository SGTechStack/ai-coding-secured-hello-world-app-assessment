import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';

export default function Register() {
  const [form, setForm] = useState({ username: '', email: '', password: '' });
  const [error, setError] = useState('');
  const [fields, setFields] = useState({});
  const [done, setDone] = useState(false);

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setFields({});
    try {
      await api.register(form.username, form.email, form.password);
      setDone(true);
    } catch (err) {
      setError(err.message);
      setFields(err.fields ?? {});
    }
  };

  if (done) {
    return <p className="notice">Account created. <Link to="/login">Log in</Link></p>;
  }

  return (
    <form onSubmit={submit}>
      <h1>Register</h1>
      {error && <p className="error" role="alert">{error}</p>}
      <label>Username
        <input value={form.username} onChange={set('username')} autoComplete="username" required />
        {fields.username && <small className="error">{fields.username}</small>}
      </label>
      <label>Email
        <input type="email" value={form.email} onChange={set('email')} autoComplete="email" required />
        {fields.email && <small className="error">{fields.email}</small>}
      </label>
      <label>Password (min 12 characters)
        <input type="password" value={form.password} onChange={set('password')} autoComplete="new-password"
          minLength={12} required />
        {fields.password && <small className="error">{fields.password}</small>}
      </label>
      <button type="submit">Create account</button>
    </form>
  );
}
