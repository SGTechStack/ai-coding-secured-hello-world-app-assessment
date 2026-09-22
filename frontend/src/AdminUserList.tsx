import { useEffect, useState } from "react";
import {
  ApiError,
  deleteUser,
  fetchAdminUsers,
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
  const [actionError, setActionError] = useState<string | null>(null);
  const [pendingUserId, setPendingUserId] = useState<string | null>(null);

  function load() {
    setState({ kind: "loading" });
    fetchAdminUsers()
      .then((users) => setState({ kind: "success", users }))
      .catch((error: unknown) => {
        setState({
          kind: "error",
          message: error instanceof Error ? error.message : "Could not load users",
        });
      });
  }

  useEffect(load, []);

  async function runAction(userId: string, action: () => Promise<unknown>) {
    setActionError(null);
    setPendingUserId(userId);
    try {
      await action();
      load();
    } catch (error: unknown) {
      setActionError(
        error instanceof ApiError
          ? error.message
          : error instanceof Error
            ? error.message
            : "Action failed",
      );
    } finally {
      setPendingUserId(null);
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
        <div className="table-wrapper">
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
                const isPending = pendingUserId === user.id;

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
                          disabled={isSelf || isPending}
                          onClick={() => handleToggleEnabled(user)}
                        >
                          {user.enabled ? "Disable" : "Enable"}
                        </button>

                        <select
                          id={`role-select-${user.id}`}
                          name={`role-select-${user.id}`}
                          aria-label={`Change role for ${user.username}`}
                          value={user.role}
                          disabled={isSelf || isPending}
                          onChange={(e) => handleRoleChange(user, e.target.value as UserRole)}
                        >
                          <option value="USER">USER</option>
                          <option value="ADMIN">ADMIN</option>
                        </select>

                        <button
                          type="button"
                          className="btn-danger"
                          disabled={isSelf || isPending}
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
