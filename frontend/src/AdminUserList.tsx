import { useCallback, useEffect, useState } from "react";
import {
  deleteUser,
  fetchAdminUsers,
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

export function AdminUserList({ currentUsername }: AdminUserListProps) {
  const [state, setState] = useState<ListState>({ kind: "loading" });
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [pendingUserIds, setPendingUserIds] = useState<ReadonlySet<string>>(new Set());

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

  function handleDelete(user: AdminUserSummary) {
    void runAction(user.id, () => deleteUser(user.id));
  }

  return (
    <section>
      <h3>All users</h3>

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

                return (
                  <tr key={user.id}>
                    <td>{user.username}</td>
                    <td>{user.email}</td>
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
                          onClick={() => handleDelete(user)}
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
