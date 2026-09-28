import { useState } from "react";
import { register } from "../api/client.js";

export default function RegisterForm({ onRegistered }) {
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    try {
      const created = await register({ username, email, password });
      setDone(true);
      onRegistered?.(created);
    } catch (err) {
      setError(err.message);
    }
  }

  if (done) {
    return <p data-testid="register-success">Account created for {username}.</p>;
  }

  return (
    <form onSubmit={handleSubmit} aria-label="register">
      <label>
        Username
        <input value={username} onChange={(e) => setUsername(e.target.value)} name="username" />
      </label>
      <label>
        Email
        <input value={email} onChange={(e) => setEmail(e.target.value)} name="email" type="email" />
      </label>
      <label>
        Password
        <input
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          name="password"
          type="password"
        />
      </label>
      {error && <p role="alert" data-testid="register-error">{error}</p>}
      <button type="submit">Register</button>
    </form>
  );
}
