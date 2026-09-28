import { useState, FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";

export function ChangePassword() {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const { user, setUser } = useAuth();
  const navigate = useNavigate();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      await api.primeCsrf();
      await api.changePassword(currentPassword, newPassword);
      if (user) {
        setUser({ ...user, mustChangePassword: false });
      }
      navigate(user?.role === "ADMIN" ? "/admin" : "/greeting");
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="container">
      <h1>Change your password</h1>
      {user?.mustChangePassword && (
        <p className="error">You must change your password before continuing.</p>
      )}
      <form onSubmit={onSubmit}>
        <label htmlFor="current">Current password</label>
        <input id="current" type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} autoComplete="current-password" />
        <label htmlFor="new">New password (min 12 characters)</label>
        <input id="new" type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} autoComplete="new-password" />
        <button type="submit" disabled={busy}>{busy ? "Saving..." : "Change password"}</button>
      </form>
      {error && <p className="error">{error}</p>}
    </div>
  );
}
