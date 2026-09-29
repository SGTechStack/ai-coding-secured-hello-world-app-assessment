import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useSession } from "../session";

export default function RegisterPage() {
  const { register } = useSession();
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: "", email: "", password: "" });
  const [error, setError] = useState("");

  function update(field) {
    return (event) => setForm((current) => ({ ...current, [field]: event.target.value }));
  }

  async function onSubmit(event) {
    event.preventDefault();
    setError("");
    try {
      await register(form.username, form.email, form.password);
      navigate("/login", { state: { notice: "Account created. Log in to continue." } });
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <section className="panel narrow">
      <p className="kicker">New account</p>
      <h1>Register</h1>
      <p className="lede">Pick a username, email, and a password of at least 12 characters.</p>
      <form onSubmit={onSubmit}>
        {error && <p className="banner">{error}</p>}
        <label>
          Username
          <input value={form.username} onChange={update("username")} autoComplete="username" required />
        </label>
        <label>
          Email
          <input type="email" value={form.email} onChange={update("email")} autoComplete="email" required />
        </label>
        <label>
          Password
          <input
            type="password"
            value={form.password}
            onChange={update("password")}
            autoComplete="new-password"
            minLength={12}
            required
          />
        </label>
        <button type="submit">Create account</button>
      </form>
      <p className="links">
        <Link to="/login">Already registered</Link>
      </p>
    </section>
  );
}
