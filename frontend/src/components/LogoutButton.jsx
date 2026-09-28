import { useState } from "react";
import { logout } from "../api/client.js";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";

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
    <div className="flex flex-col items-end gap-2">
      <Button type="button" variant="outline" onClick={handleClick} disabled={busy}>
        Log out
      </Button>
      {error && (
        <Alert variant="destructive" data-testid="logout-error">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
    </div>
  );
}
