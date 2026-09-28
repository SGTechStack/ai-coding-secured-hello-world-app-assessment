import { useState, FormEvent } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";

export function RequestReset() {
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await api.primeCsrf();
      const res = await api.requestReset(email);
      setMessage(res.message);
    } catch {
      // Generic message regardless of outcome (enumeration resistance).
      setMessage("If that email is registered, a reset link has been sent.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="container">
      <h1>Reset your password</h1>
      <form onSubmit={onSubmit}>
        <label htmlFor="email">Email</label>
        <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
        <button type="submit" disabled={busy}>{busy ? "Sending..." : "Send reset link"}</button>
      </form>
      {message && <p className="success">{message}</p>}
      <div className="links">
        <Link to="/login">Back to sign in</Link>
      </div>
    </div>
  );
}
