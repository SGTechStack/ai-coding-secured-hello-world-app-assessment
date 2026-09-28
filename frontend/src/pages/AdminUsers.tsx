import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, AdminUser } from "../api";
import { useAuth } from "../auth";

export function AdminUsers() {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [error, setError] = useState("");
  const { user, setUser } = useAuth();
  const navigate = useNavigate();

  async function refresh() {
    try {
      setUsers(await api.adminListUsers());
    } catch (err) {
      setError((err as Error).message);
    }
  }

  useEffect(() => {
    refresh();
  }, []);

  async function act(fn: () => Promise<unknown>) {
    setError("");
    try {
      await api.primeCsrf();
      await fn();
      await refresh();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  async function onLogout() {
    try {
      await api.logout();
    } finally {
      setUser(null);
      navigate("/login");
    }
  }

  return (
    <div className="container wide">
      <div className="topbar">
        <h1>User management</h1>
        <button className="secondary" onClick={onLogout}>Log out</button>
      </div>
      {error && <p className="error">{error}</p>}
      <table>
        <thead>
          <tr>
            <th>Username</th><th>Email</th><th>Role</th><th>Enabled</th><th>Created</th><th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {users.map((u) => {
            const isSelf = u.username === user?.username;
            return (
              <tr key={u.id}>
                <td>{u.username}{isSelf ? " (you)" : ""}</td>
                <td>{u.email}</td>
                <td>{u.role}</td>
                <td>{u.enabled ? "yes" : "no"}</td>
                <td>{new Date(u.createdAt).toLocaleDateString()}</td>
                <td className="row-actions">
                  <button className="secondary" disabled={isSelf}
                    onClick={() => act(() => api.adminSetStatus(u.id, !u.enabled))}>
                    {u.enabled ? "Disable" : "Enable"}
                  </button>
                  <button className="secondary" disabled={isSelf}
                    onClick={() => act(() => api.adminChangeRole(u.id, u.role === "ADMIN" ? "USER" : "ADMIN"))}>
                    {u.role === "ADMIN" ? "Make USER" : "Make ADMIN"}
                  </button>
                  <button className="danger" disabled={isSelf}
                    onClick={() => act(() => api.adminDelete(u.id))}>
                    Delete
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
