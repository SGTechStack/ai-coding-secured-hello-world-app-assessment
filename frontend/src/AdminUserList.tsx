import { useCallback, useEffect, useState } from "react";
import {
  deleteUser,
  fetchAdminUsers,
  fetchUserEmail,
  isAbortError,
  setUserEnabled,
  setUserRole,
  type AdminUserSummary,
  type UserRole,
} from "./api/client";

interface AdminUserListProps {
  currentUsername: string;
}

type ListState =
  | { kind: "loading" }
  | { kind: "success"; users: AdminUserSummary[] }
  | { kind: "error"; message: string };

/**
 * A pending request for something the operator has to supply before an action can
 * proceed: their password for an irreversible delete, or a purpose for revealing
 * an email address.
 *
 * Modelled as state rather than handled with `window.prompt` because a native
 * prompt cannot mask a password field, is blocked by some browsers, and gives no
 * room to explain why the value is being asked for — and an unexplained password
 * prompt is exactly the habit that makes people type passwords into anything.
 */
type Challenge =
  | { kind: "confirm-delete"; user: AdminUserSummary; value: string; error: string | null }
  | { kind: "state-purpose"; user: AdminUserSummary; value: string; error: string | null };

export function AdminUserList({ currentUsername }: AdminUserListProps) {
  const [state, setState] = useState<ListState>({ kind: "loading" });
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [pendingUserIds, setPendingUserIds] = useState<ReadonlySet<string>>(new Set());
  const [challenge, setChallenge] = useState<Challenge | null>(null);
  /**
   * Addresses revealed during this session, keyed by user id.
   *
   * Deliberately not persisted and not prefetched. Each entry cost a
   * purpose-stated, audited request, and keeping them only in component state
   * means closing the panel discards them rather than leaving a local cache of
   * every address an admin has ever looked at.
   */
  const [revealedEmails, setRevealedEmails] = useState<ReadonlyMap<string, string>>(new Map());

  /**
   * Fetches the user list. A *refresh* (after a mutation) keeps the existing
   * rows mounted and only flags `isRefreshing`; a first load clears to the
   * loading state. The distinction matters: dropping back to `{ kind: "loading" }`
   * would unmount the whole table subtree, so React would delete every row's
   * DOM node and rebuild it, throwing away scroll position and keyboard focus.
   */
  const load = useCallback(async (isRefresh: boolean, signal?: AbortSignal) => {
    if (isRefresh) {
      setIsRefreshing(true);
    } else {
      setState({ kind: "loading" });
    }

    try {
      setState({ kind: "success", users: await fetchAdminUsers(signal) });
    } catch (error: unknown) {
      if (isAbortError(error)) return;
      setState({
        kind: "error",
        message: error instanceof Error ? error.message : "Could not load users",
      });
    } finally {
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void load(false, controller.signal);
    return () => controller.abort();
  }, [load]);

  /**
   * Runs a single-user mutation, then awaits the refresh before releasing the
   * row. Awaiting matters: returning early would re-enable the controls while
   * the list was still in flight, and two quick actions would leave two
   * refetches racing with no ordering guarantee — the slower response would win
   * and could repaint pre-mutation data.
   */
  async function runAction(userId: string, action: () => Promise<unknown>) {
    setActionError(null);
    // A Set rather than a single id, so overlapping actions on different rows
    // can't clobber each other's pending flag. Always a fresh Set: mutating the
    // previous one in place would leave the reference unchanged, and React would
    // bail out of the re-render because `Object.is(prev, next)` holds.
    setPendingUserIds((prev) => new Set(prev).add(userId));
    try {
      await action();
      await load(true);
    } catch (error: unknown) {
      setActionError(error instanceof Error ? error.message : "Action failed");
    } finally {
      setPendingUserIds((prev) => {
        const next = new Set(prev);
        next.delete(userId);
        return next;
      });
    }
  }

  function handleToggleEnabled(user: AdminUserSummary) {
    void runAction(user.id, () => setUserEnabled(user.id, !user.enabled));
  }

  function handleRoleChange(user: AdminUserSummary, newRole: UserRole) {
    if (newRole === user.role) return;
    void runAction(user.id, () => setUserRole(user.id, newRole));
  }

  /**
   * Submits whichever challenge is open.
   *
   * Errors are shown inside the challenge rather than in the panel-level banner,
   * so a wrong password leaves the form open with the message attached to it.
   * Clearing the form and reporting the failure elsewhere would make a mistyped
   * password look like a refusal to act.
   */
  async function submitChallenge() {
    if (!challenge) return;

    if (!challenge.value.trim()) {
      setChallenge({ ...challenge, error: "This is required" });
      return;
    }

    const { kind, user, value } = challenge;

    try {
      if (kind === "confirm-delete") {
        await deleteUser(user.id, value);
        setChallenge(null);
        // Clear any revealed address for the deleted account rather than leaving
        // it on screen attached to a row that no longer exists.
        setRevealedEmails((prev) => {
          const next = new Map(prev);
          next.delete(user.id);
          return next;
        });
        await load(true);
      } else {
        const revealed = await fetchUserEmail(user.id, value);
        setRevealedEmails((prev) => new Map(prev).set(user.id, revealed.email));
        setChallenge(null);
      }
    } catch (error: unknown) {
      setChallenge({
        ...challenge,
        error: error instanceof Error ? error.message : "Request failed",
      });
    }
  }

  return (
    <section>
      <h3>All users</h3>

      <p className="field-hint">
        Email addresses are masked. Revealing one is recorded in the audit log with the reason you give.
      </p>

      {state.kind === "loading" && <p className="skeleton-text" role="status">Loading users…</p>}

      {state.kind === "error" && (
        <p className="alert alert-error" role="alert">
          {state.message}
        </p>
      )}

      {actionError && (
        <p className="alert alert-error" role="alert">
          {actionError}
        </p>
      )}

      {challenge && (
        <form
          className="alert"
          onSubmit={(e) => {
            e.preventDefault();
            void submitChallenge();
          }}
        >
          {challenge.kind === "confirm-delete" ? (
            <>
              <p>
                Deleting <strong>{challenge.user.username}</strong> cannot be undone. Enter your own password to
                confirm.
              </p>
              <div className="field">
                <label htmlFor="confirm-password">Your password</label>
                <input
                  id="confirm-password"
                  name="confirm-password"
                  type="password"
                  autoComplete="current-password"
                  autoFocus
                  value={challenge.value}
                  onChange={(e) => setChallenge({ ...challenge, value: e.target.value, error: null })}
                />
              </div>
            </>
          ) : (
            <>
              <p>
                Why do you need <strong>{challenge.user.username}</strong>&apos;s email address? This is stored in
                the audit log.
              </p>
              <div className="field">
                <label htmlFor="reveal-purpose">Reason</label>
                <input
                  id="reveal-purpose"
                  name="reveal-purpose"
                  type="text"
                  autoFocus
                  value={challenge.value}
                  onChange={(e) => setChallenge({ ...challenge, value: e.target.value, error: null })}
                />
              </div>
            </>
          )}

          {challenge.error && (
            <p className="alert alert-error" role="alert">
              {challenge.error}
            </p>
          )}

          <div className="row-actions">
            <button type="submit" className={challenge.kind === "confirm-delete" ? "btn-danger" : ""}>
              {challenge.kind === "confirm-delete" ? "Delete account" : "Reveal address"}
            </button>
            <button type="button" className="btn-secondary" onClick={() => setChallenge(null)}>
              Cancel
            </button>
          </div>
        </form>
      )}

      {state.kind === "success" && (
        <div className={`table-wrapper${isRefreshing ? " is-refreshing" : ""}`} aria-busy={isRefreshing}>
          <table>
            <thead>
              <tr>
                <th>Username</th>
                <th>Email</th>
                <th>Role</th>
                <th>Enabled</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {state.users.map((user) => {
                const isSelf = user.username === currentUsername;
                // Locked while this row is mutating, and also while the list is
                // refreshing — acting on rows that are about to be replaced by
                // fresh server data would be acting on stale ids.
                const isLocked = isSelf || pendingUserIds.has(user.id) || isRefreshing;
                const revealed = revealedEmails.get(user.id);

                return (
                  <tr key={user.id}>
                    <td>{user.username}</td>
                    <td>
                      {revealed ?? user.maskedEmail}
                      {!revealed && (
                        <>
                          {" "}
                          <button
                            type="button"
                            className="btn-link"
                            onClick={() =>
                              setChallenge({ kind: "state-purpose", user, value: "", error: null })
                            }
                          >
                            Reveal
                          </button>
                        </>
                      )}
                    </td>
                    <td>
                      <span className={`role-badge${user.role === "ADMIN" ? " is-admin" : ""}`}>
                        {user.role}
                      </span>
                    </td>
                    <td>
                      <span className={`badge-enabled ${user.enabled ? "is-enabled" : "is-disabled"}`}>
                        {user.enabled ? "Enabled" : "Disabled"}
                      </span>
                    </td>
                    <td>{new Date(user.createdAt).toLocaleString()}</td>
                    <td>
                      <div className="row-actions">
                        <button
                          type="button"
                          className="btn-secondary"
                          disabled={isLocked}
                          onClick={() => handleToggleEnabled(user)}
                        >
                          {user.enabled ? "Disable" : "Enable"}
                        </button>

                        <select
                          id={`role-select-${user.id}`}
                          name={`role-select-${user.id}`}
                          aria-label={`Change role for ${user.username}`}
                          value={user.role}
                          disabled={isLocked}
                          onChange={(e) => handleRoleChange(user, e.target.value as UserRole)}
                        >
                          <option value="USER">USER</option>
                          <option value="ADMIN">ADMIN</option>
                        </select>

                        <button
                          type="button"
                          className="btn-danger"
                          disabled={isLocked}
                          onClick={() =>
                            setChallenge({ kind: "confirm-delete", user, value: "", error: null })
                          }
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
