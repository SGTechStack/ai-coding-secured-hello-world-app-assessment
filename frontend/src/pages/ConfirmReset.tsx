import { useState, FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { api } from "../api";

export function ConfirmReset() {
  const [params] = useSearchParams();
  const [token, setToken] = useState(params.get("token") ?? "");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setSuccess("");
    setBusy(true);
    try {
      await api.primeCsrf();
      await api.confirmReset(token, password);
      setSuccess("Password reset. Redirecting to sign in...");
      setTimeout(() => navigate("/login"), 1200);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="container">
      <h1>Choose a new password</h1>
      <form onSubmit={onSubmit}>
        <label htmlFor="token">Reset token</label>
        <input id="token" value={token} onChange={(e) => setToken(e.target.value)} />
        <label htmlFor="password">New password (min 12 characters)</label>
        <input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" />
        <button type="submit" disabled={busy}>{busy ? "Resetting..." : "Reset password"}</button>
      </form>
      {error && <p className="error">{error}</p>}
      {success && <p className="success">{success}</p>}
      <div className="links">
        <Link to="/login">Back to sign in</Link>
      </div>
    </div>
  );
}
