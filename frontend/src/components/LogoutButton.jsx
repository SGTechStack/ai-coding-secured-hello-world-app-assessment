import { useState } from "react";
import { logout } from "../api/client.js";

/**
 * Logout control. Ends the server-side session, then notifies the parent so it
 * can drop back to the unauthenticated view. Errors are surfaced inline and the
 * session is left untouched on failure.
 */
export default function LogoutButton({ onLoggedOut }) {
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function handleClick() {
    setError("");
    setBusy(true);
    try {
      await logout();
      onLoggedOut?.();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <button type="button" onClick={handleClick} disabled={busy}>
        Log out
      </button>
      {error && <p role="alert" data-testid="logout-error">{error}</p>}
    </div>
  );
}
