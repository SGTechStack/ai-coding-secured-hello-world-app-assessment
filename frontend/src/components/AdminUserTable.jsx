import { useEffect, useState } from "react";
import { fetchAdminUsers, setUserEnabled, changeUserRole, deleteUser } from "../api/client.js";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

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

  async function handleDelete(user) {
    setActionError("");
    setPendingId(user.username);
    try {
      await deleteUser(user.id);
      // Remove the deleted account from the list on success.
      setUsers((prev) => prev.filter((u) => u.username !== user.username));
    } catch (err) {
      // Leave the list unchanged on failure (e.g. self-action guard 400).
      setActionError(err.message);
    } finally {
      setPendingId(null);
    }
  }

  if (loading) {
    return (
      <p data-testid="admin-users-loading" className="text-sm text-muted-foreground">
        Loading users…
      </p>
    );
  }

  if (error) {
    return (
      <Alert variant="destructive" data-testid="admin-users-error">
        <AlertDescription>{error}</AlertDescription>
      </Alert>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      {actionError && (
        <Alert variant="destructive" data-testid="admin-users-action-error">
          <AlertDescription>{actionError}</AlertDescription>
        </Alert>
      )}
      <div className="overflow-hidden rounded-lg border">
        <Table data-testid="admin-users-table" aria-label="user list">
          <caption className="sr-only">Users</caption>
          <TableHeader className="bg-muted/50">
            <TableRow>
              <TableHead scope="col">Username</TableHead>
              <TableHead scope="col">Email</TableHead>
              <TableHead scope="col">Role</TableHead>
              <TableHead scope="col">Enabled</TableHead>
              <TableHead scope="col">Created</TableHead>
              <TableHead scope="col" className="text-right">
                Actions
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {users.map((u) => (
              <TableRow key={u.username} data-testid="admin-user-row">
                <TableCell className="font-medium">{u.username}</TableCell>
                <TableCell className="text-muted-foreground">{u.email}</TableCell>
                <TableCell>
                  <select
                    data-testid="admin-user-role"
                    value={u.role}
                    onChange={(e) => handleRoleChange(u, e.target.value)}
                    disabled={pendingId === u.username}
                    aria-label={`Role for ${u.username}`}
                    className="h-8 rounded-lg border border-input bg-transparent px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <option value="USER">USER</option>
                    <option value="ADMIN">ADMIN</option>
                  </select>
                </TableCell>
                <TableCell>
                  <span
                    className={
                      u.enabled
                        ? "inline-flex items-center rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary"
                        : "inline-flex items-center rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground"
                    }
                  >
                    {u.enabled ? "Yes" : "No"}
                  </span>
                </TableCell>
                <TableCell className="text-muted-foreground">{u.createdAt}</TableCell>
                <TableCell>
                  <div className="flex justify-end gap-2">
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      data-testid="admin-user-toggle"
                      onClick={() => handleToggle(u)}
                      disabled={pendingId === u.username}
                      aria-label={`${u.enabled ? "Disable" : "Enable"} ${u.username}`}
                    >
                      {u.enabled ? "Disable" : "Enable"}
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="destructive"
                      data-testid="admin-user-delete"
                      onClick={() => handleDelete(u)}
                      disabled={pendingId === u.username}
                      aria-label={`Delete ${u.username}`}
                    >
                      Delete
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
