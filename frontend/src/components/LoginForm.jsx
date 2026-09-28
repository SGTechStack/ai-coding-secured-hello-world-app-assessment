import { useState } from "react";
import { login } from "../api/client.js";

export default function LoginForm({ onLoggedIn }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    try {
      const session = await login({ username, password });
      onLoggedIn?.(session);
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <form onSubmit={handleSubmit} aria-label="login">
      <label>
        Username
        <input value={username} onChange={(e) => setUsername(e.target.value)} name="username" />
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
      {error && <p role="alert" data-testid="login-error">{error}</p>}
      <button type="submit">Log in</button>
    </form>
  );
}
