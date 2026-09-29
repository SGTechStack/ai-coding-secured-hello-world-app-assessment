import { useEffect, useState } from "react";
import { api } from "../api";
import { useSession } from "../session";

export default function AdminPage() {
  const { user } = useSession();
  const [rows, setRows] = useState([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  async function load() {
    const directory = await api("/api/admin/users");
    setRows(directory);
  }

  useEffect(() => {
    load().catch((err) => setError(err.message));
  }, []);

  async function run(action) {
    setError("");
    setNotice("");
    try {
      await action();
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <section className="panel">
      <p className="kicker">Accounts</p>
      <h1>Directory</h1>
      <p className="lede">Enable, disable, change role, or remove another account. Your own row stays locked.</p>
      {error && <p className="banner">{error}</p>}
      {notice && <p className="banner ok">{notice}</p>}
      <table>
        <thead>
          <tr>
            <th>Username</th>
            <th>Email</th>
            <th>Role</th>
            <th>Status</th>
            <th>Created</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const self = row.username === user.username;
            return (
              <tr key={row.id}>
                <td>{row.username}</td>
                <td>{row.email}</td>
                <td>{row.role}</td>
                <td>{row.enabled ? "Enabled" : "Disabled"}</td>
                <td>{new Date(row.createdAt).toLocaleString()}</td>
                <td>
                  <div className="row-actions">
                    <button
                      type="button"
                      disabled={self}
                      onClick={() => run(() => api(`/api/admin/users/${row.id}/enabled`, {
                        method: "PATCH",
                        body: { enabled: !row.enabled },
                      }))}
                    >
                      {row.enabled ? "Disable" : "Enable"}
                    </button>
                    <select
                      aria-label={`Role for ${row.username}`}
                      value={row.role}
                      disabled={self}
                      onChange={(event) => run(() => api(`/api/admin/users/${row.id}/role`, {
                        method: "PATCH",
                        body: { role: event.target.value },
                      }))}
                    >
                      <option value="USER">USER</option>
                      <option value="ADMIN">ADMIN</option>
                    </select>
                    <button
                      type="button"
                      className="danger"
                      disabled={self}
                      onClick={() => {
                        if (window.confirm(`Delete ${row.username}?`)) {
                          run(async () => {
                            await api(`/api/admin/users/${row.id}`, { method: "DELETE" });
                            setNotice(`${row.username} was removed.`);
                          });
                        }
                      }}
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
    </section>
  );
}
