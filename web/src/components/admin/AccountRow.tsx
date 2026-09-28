import type { AccountSummary } from "../../api/types";
import { Button } from "../ui/Button";

export type AccountAction = "enable" | "disable" | "promote" | "demote" | "delete";

interface AccountRowProps {
  account: AccountSummary;
  /** True for the signed-in admin's own row. */
  isSelf: boolean;
  onAction: (action: AccountAction, account: AccountSummary) => void;
}

export function AccountRow({ account, isSelf, onAction }: AccountRowProps) {
  // The server refuses all three self-actions with a 409. Disabling the buttons here means an admin
  // is told before they click rather than after — it is not what enforces the rule.
  const selfActionTitle = isSelf ? "You cannot do this to your own account." : undefined;

  return (
    <tr className="border-t border-edge hover:bg-panel-hover">
      <th scope="row" className="px-3 py-2 text-left font-medium text-ink">
        {account.username}
        {isSelf ? <span className="ml-2 text-xs font-normal text-ink-faint">(you)</span> : null}
      </th>
      <td className="px-3 py-2 text-ink-muted">{account.email}</td>
      <td className="px-3 py-2">
        {account.role === "ADMIN" ? (
          <span className="rounded bg-accent-soft px-1.5 py-0.5 text-xs font-medium text-accent">
            ADMIN
          </span>
        ) : (
          <span className="text-ink-muted">USER</span>
        )}
      </td>
      {/* Stacked rather than inline, and each part non-wrapping. Inline, a locked account pushed the
          column wide enough to break "Locked out" across two lines mid-badge. */}
      <td className="px-3 py-2">
        <div className="flex flex-col items-start gap-1">
          {/* Each status is a word, not just a colour. Someone who cannot tell green from red still
              reads "Enabled" and "Disabled". */}
          {account.enabled ? (
            <span className="text-success">Enabled</span>
          ) : (
            <span className="text-danger">Disabled</span>
          )}
          {account.locked ? (
            <span className="rounded bg-warning-soft px-1.5 py-0.5 text-xs font-medium whitespace-nowrap text-warning">
              Locked out
            </span>
          ) : null}
        </div>
      </td>
      <td className="px-3 py-2 whitespace-nowrap text-ink-muted">
        {new Date(account.createdAt).toLocaleDateString()}
      </td>
      <td className="px-3 py-2">
        {/* nowrap, not wrap: the wrapper table scrolls horizontally if it has to, which keeps every
            row the same shape instead of letting some rows grow a second line of buttons. */}
        <div className="flex flex-nowrap justify-end gap-2">
          <Button
            variant="secondary"
            disabled={isSelf}
            title={selfActionTitle}
            className="whitespace-nowrap"
            onClick={() => onAction(account.enabled ? "disable" : "enable", account)}
          >
            {account.enabled ? "Disable" : "Enable"}
          </Button>
          <Button
            variant="secondary"
            disabled={isSelf}
            title={selfActionTitle}
            className="whitespace-nowrap"
            onClick={() => onAction(account.role === "ADMIN" ? "demote" : "promote", account)}
          >
            {account.role === "ADMIN" ? "Make user" : "Make admin"}
          </Button>
          <Button
            variant="danger"
            disabled={isSelf}
            title={selfActionTitle}
            onClick={() => onAction("delete", account)}
          >
            Delete
          </Button>
        </div>
      </td>
    </tr>
  );
}
