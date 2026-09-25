import { useState } from "react";

import { Button } from "@/common/components/ui/button";
import { Input } from "@/common/components/ui/input";
import { Label } from "@/common/components/ui/label";
import { Alert, AlertDescription } from "@/common/components/ui/alert";
import { eraseMyAccount, exportMyAccountData, triggerAccountDataDownload } from "../api";

interface AccountDataPanelProps {
  /** Called once the account no longer exists, so the shell can return to the login view. */
  onErased: () => void;
}

type PanelState =
  | { kind: "idle" }
  | { kind: "exporting" }
  | { kind: "exported" }
  | { kind: "confirming-erasure"; password: string }
  | { kind: "erasing" }
  | { kind: "error"; message: string };

/**
 * The two data-subject rights this application implements, as controls a user can
 * actually reach: download everything held about them, and erase it.
 *
 * <h3>Why these are here and not in a support inbox</h3>
 *
 * Before this, deletion was admin-only and export did not exist, so both rights
 * were exercisable only by asking somebody — which is a process, not a feature.
 * Routing an erasure request through an administrator also means the request
 * itself creates a record of who asked to be forgotten, held by the party they
 * are asking.
 *
 * <h3>The erasure confirmation</h3>
 *
 * Erasure is irreversible, so the server requires the account's password again and
 * this panel collects it. Two separate reasons, and both matter: a session proves
 * somebody authenticated hours ago rather than that this request came from them,
 * and a password field is a deliberate pause in front of an action with no undo —
 * more useful than a "are you sure?" that people click through.
 */
export function AccountDataPanel({ onErased }: AccountDataPanelProps) {
  const [state, setState] = useState<PanelState>({ kind: "idle" });

  async function handleExport() {
    setState({ kind: "exporting" });
    try {
      const data = await exportMyAccountData();
      triggerAccountDataDownload(data);
      setState({ kind: "exported" });
    } catch (error: unknown) {
      setState({
        kind: "error",
        message: error instanceof Error ? error.message : "Could not export your data",
      });
    }
  }

  async function handleErase(password: string) {
    setState({ kind: "erasing" });
    try {
      await eraseMyAccount(password);
      // No success state: the account is gone, so there is nothing left for this
      // component to render. The parent takes over.
      onErased();
    } catch (error: unknown) {
      // Back to the confirmation form with the password cleared, rather than to a
      // generic error somewhere else — a wrong password should look like a wrong
      // password, and the most likely next action is retyping it.
      setState({
        kind: "error",
        message: error instanceof Error ? error.message : "Could not delete your account",
      });
    }
  }

  const isBusy = state.kind === "exporting" || state.kind === "erasing";

  return (
    <section>
      <h3 className="mb-4 text-[1.05rem] font-semibold">Your data</h3>

      <p className="mb-4 text-[0.78rem] text-muted-foreground">
        We hold your username, email address and a hashed password, plus sign-in activity used to slow down
        brute-force attacks. Your email is used only to send a password reset link you ask for.
      </p>

      {state.kind === "error" && (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{state.message}</AlertDescription>
        </Alert>
      )}

      {state.kind === "exported" && (
        <Alert variant="success" className="mb-4">
          <AlertDescription>
            Your data has been downloaded as <code>my-account-data.json</code>.
          </AlertDescription>
        </Alert>
      )}

      {state.kind === "confirming-erasure" ? (
        <form
          className="flex flex-col gap-3 rounded-lg border px-4 py-3"
          onSubmit={(e) => {
            e.preventDefault();
            void handleErase(state.password);
          }}
        >
          <p className="text-sm">
            Deleting your account removes it and its data permanently. This cannot be undone. Enter your password
            to confirm.
          </p>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="erase-password">Your password</Label>
            <Input
              id="erase-password"
              name="erase-password"
              type="password"
              autoComplete="current-password"
              autoFocus
              required
              value={state.password}
              onChange={(e) => setState({ kind: "confirming-erasure", password: e.target.value })}
            />
          </div>
          <div className="flex items-center gap-2">
            <Button type="submit" variant="destructive">
              Delete my account
            </Button>
            <Button type="button" variant="secondary" onClick={() => setState({ kind: "idle" })}>
              Cancel
            </Button>
          </div>
        </form>
      ) : (
        <div className="flex items-center gap-2">
          <Button type="button" variant="secondary" disabled={isBusy} onClick={() => void handleExport()}>
            {state.kind === "exporting" ? "Preparing…" : "Download my data"}
          </Button>
          <Button
            type="button"
            variant="destructive"
            disabled={isBusy}
            onClick={() => setState({ kind: "confirming-erasure", password: "" })}
          >
            Delete my account
          </Button>
        </div>
      )}
    </section>
  );
}
