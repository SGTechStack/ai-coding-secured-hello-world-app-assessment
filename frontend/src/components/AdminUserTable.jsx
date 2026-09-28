import { useEffect, useState } from "react";
import { fetchAdminUsers } from "../api/client.js";

/**
 * Admin user table (Story 8). Loads and renders the user list from
 * `GET /api/admin/users` for an admin. Authorization is enforced server-side by
 * the `/api/admin/**` role guard — this component only renders what the backend
 * returns; a non-admin never receives a list (the request fails with 403).
 *
 * <p>Shows a loading state while fetching and surfaces an error message if the
 * request fails (e.g. a non-admin hitting the endpoint).
 */
export default function AdminUserTable() {
  const [users, setUsers] = useState([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

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
    <table data-testid="admin-users-table" aria-label="user list">
      <caption>Users</caption>
      <thead>
        <tr>
          <th scope="col">Username</th>
          <th scope="col">Email</th>
          <th scope="col">Role</th>
          <th scope="col">Enabled</th>
          <th scope="col">Created</th>
        </tr>
      </thead>
      <tbody>
        {users.map((u) => (
          <tr key={u.username} data-testid="admin-user-row">
            <td>{u.username}</td>
            <td>{u.email}</td>
            <td>{u.role}</td>
            <td>{u.enabled ? "Yes" : "No"}</td>
            <td>{u.createdAt}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
