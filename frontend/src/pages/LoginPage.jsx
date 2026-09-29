import { useState } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { useSession } from "../session";

export default function LoginPage() {
  const { user, login } = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const notice = location.state?.notice;

  if (user) {
    return <Navigate to="/" replace />;
  }

  async function onSubmit(event) {
    event.preventDefault();
    setError("");
    try {
      await login(username, password);
      navigate("/");
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <section className="panel narrow">
      <p className="kicker">Session sign-in</p>
      <h1>Welcome back</h1>
      <p className="lede">Use the username and password for your Hello Desk account.</p>
      <form onSubmit={onSubmit}>
        {notice && <p className="banner ok">{notice}</p>}
        {error && <p className="banner">{error}</p>}
        <label>
          Username
          <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" required />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        <button type="submit">Log in</button>
      </form>
      <p className="links">
        <Link to="/forgot-password">Forgot password</Link>
        <Link to="/register">Create an account</Link>
      </p>
    </section>
  );
}
