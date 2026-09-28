import { useEffect, useState } from "react";
import { fetchAdminUsers, setUserEnabled, changeUserRole } from "../api/client.js";

/**
 * Admin user table (Story 8/9). Loads and renders the user list from
 * `GET /api/admin/users` for an admin, and (Story 9) provides a per-row control
 * to enable/disable a target account via `PATCH /api/admin/users/{id}/enabled`.
 * Authorization is enforced server-side by the `/api/admin/**` role guard — this
 * component only renders what the backend returns; a non-admin never receives a
 * list (the request fails with 403).
 *
 * <p>The self-action guard is enforced by the backend (an admin toggling their
 * own account gets 400); this component surfaces that error message and leaves
 * the row's state unchanged on any failure.
 */
export default function AdminUserTable() {
  const [users, setUsers] = useState([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [pendingId, setPendingId] = useState(null);
  const [actionError, setActionError] = useState("");

  useEffect(() => {
    let active = true;
    fetchAdminUsers()
      .then((data) => {
        if (active) {
          setUsers(data);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (active) {
          setError(err.message);
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  async function handleToggle(user) {
    setActionError("");
    setPendingId(user.username);
    try {
      const updated = await setUserEnabled(user.id, !user.enabled);
      // Reflect the new state the backend confirmed (fall back to the flag we sent).
      const nextEnabled =
        typeof updated?.enabled === "boolean" ? updated.enabled : !user.enabled;
      setUsers((prev) =>
        prev.map((u) =>
          u.username === user.username ? { ...u, enabled: nextEnabled } : u,
        ),
      );
    } catch (err) {
      setActionError(err.message);
    } finally {
      setPendingId(null);
    }
  }

  async function handleRoleChange(user, nextRole) {
    if (nextRole === user.role) {
      return;
    }
    setActionError("");
    setPendingId(user.username);
    try {
      const updated = await changeUserRole(user.id, nextRole);
      // Reflect the role the backend confirmed (fall back to the one we sent).
      const confirmedRole =
        typeof updated?.role === "string" ? updated.role : nextRole;
      setUsers((prev) =>
        prev.map((u) =>
          u.username === user.username ? { ...u, role: confirmedRole } : u,
        ),
      );
    } catch (err) {
      // Leave the row's role unchanged on failure (e.g. self-action guard 400).
      setActionError(err.message);
    } finally {
      setPendingId(null);
    }
  }

  if (loading) {
    return <p data-testid="admin-users-loading">Loading users…</p>;
  }

  if (error) {
    return (
      <p role="alert" data-testid="admin-users-error">
        {error}
      </p>
    );
  }

  return (
    <>
      {actionError && (
        <p role="alert" data-testid="admin-users-action-error">
          {actionError}
        </p>
      )}
      <table data-testid="admin-users-table" aria-label="user list">
        <caption>Users</caption>
        <thead>
          <tr>
            <th scope="col">Username</th>
            <th scope="col">Email</th>
            <th scope="col">Role</th>
            <th scope="col">Enabled</th>
            <th scope="col">Created</th>
            <th scope="col">Actions</th>
          </tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.username} data-testid="admin-user-row">
              <td>{u.username}</td>
              <td>{u.email}</td>
              <td>
                <select
                  data-testid="admin-user-role"
                  value={u.role}
                  onChange={(e) => handleRoleChange(u, e.target.value)}
                  disabled={pendingId === u.username}
                  aria-label={`Role for ${u.username}`}
                >
                  <option value="USER">USER</option>
                  <option value="ADMIN">ADMIN</option>
                </select>
              </td>
              <td>{u.enabled ? "Yes" : "No"}</td>
              <td>{u.createdAt}</td>
              <td>
                <button
                  type="button"
                  data-testid="admin-user-toggle"
                  onClick={() => handleToggle(u)}
                  disabled={pendingId === u.username}
                  aria-label={`${u.enabled ? "Disable" : "Enable"} ${u.username}`}
                >
                  {u.enabled ? "Disable" : "Enable"}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
