import { useEffect, useState } from "react";
import { fetchAdminUsers, type AdminUserSummary } from "./api/client";

type ListState =
  | { kind: "loading" }
  | { kind: "success"; users: AdminUserSummary[] }
  | { kind: "error"; message: string };

export function AdminUserList() {
  const [state, setState] = useState<ListState>({ kind: "loading" });

  useEffect(() => {
    let isMounted = true;

    fetchAdminUsers()
      .then((users) => {
        if (isMounted) setState({ kind: "success", users });
      })
      .catch((error: unknown) => {
        if (isMounted) {
          setState({
            kind: "error",
            message: error instanceof Error ? error.message : "Could not load users",
          });
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <section>
      <h3>All users</h3>

      {state.kind === "loading" && <p role="status">Loading users…</p>}

      {state.kind === "error" && (
        <p role="alert" style={{ color: "crimson" }}>
          {state.message}
        </p>
      )}

      {state.kind === "success" && (
        <table>
          <thead>
            <tr>
              <th>Username</th>
              <th>Email</th>
              <th>Role</th>
              <th>Enabled</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {state.users.map((user) => (
              <tr key={user.id}>
                <td>{user.username}</td>
                <td>{user.email}</td>
                <td>{user.role}</td>
                <td>{user.enabled ? "Yes" : "No"}</td>
                <td>{new Date(user.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
