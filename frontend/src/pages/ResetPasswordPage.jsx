import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { api } from "../api";

export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const token = params.get("token") ?? "";

  async function onSubmit(event) {
    event.preventDefault();
    setError("");
    try {
      const body = await api("/api/auth/password-reset/confirm", {
        method: "POST",
        body: { token, password },
      });
      navigate("/login", { state: { notice: body.message } });
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <section className="panel narrow">
      <p className="kicker">Choose a new password</p>
      <h1>Reset password</h1>
      {!token && <p className="banner">This page needs a reset token in the link.</p>}
      <form onSubmit={onSubmit}>
        {error && <p className="banner">{error}</p>}
        <label>
          New password
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="new-password"
            minLength={12}
            required
          />
        </label>
        <button type="submit" disabled={!token}>
          Update password
        </button>
      </form>
      <p className="links">
        <Link to="/login">Back to login</Link>
      </p>
    </section>
  );
}
