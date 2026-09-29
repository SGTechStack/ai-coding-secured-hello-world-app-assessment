import { useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function onSubmit(event) {
    event.preventDefault();
    setError("");
    setMessage("");
    try {
      const body = await api("/api/auth/password-reset/request", {
        method: "POST",
        body: { email },
      });
      setMessage(body.message);
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <section className="panel narrow">
      <p className="kicker">Password reset</p>
      <h1>Forgot password</h1>
      <p className="lede">
        Enter the email on the account. The reply is the same whether or not that inbox is registered.
      </p>
      <form onSubmit={onSubmit}>
        {error && <p className="banner">{error}</p>}
        {message && <p className="banner ok">{message}</p>}
        <label>
          Email
          <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
        </label>
        <button type="submit">Send reset link</button>
      </form>
      <p className="hint">
        This demo does not send mail. The reset link is written to the backend log.
      </p>
      <p className="links">
        <Link to="/login">Back to login</Link>
      </p>
    </section>
  );
}
