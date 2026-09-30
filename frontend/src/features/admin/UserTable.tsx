import type { Role, UserSummary } from '../../api/types';
import { Button } from '../../components/ui/Button';

export interface UserTableProps {
  users: UserSummary[];
  currentUsername: string | null;
  busy: boolean;
  onToggleEnabled: (user: UserSummary) => void;
  onChangeRole: (user: UserSummary, role: Role) => void;
  onDelete: (user: UserSummary) => void;
}

const ROLES: Role[] = ['USER', 'ADMIN'];

export function UserTable({
  users,
  currentUsername,
  busy,
  onToggleEnabled,
  onChangeRole,
  onDelete,
}: UserTableProps) {
  if (users.length === 0) {
    return <p className="muted">No users yet.</p>;
  }

  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th scope="col">Username</th>
            <th scope="col">Email</th>
            <th scope="col">Role</th>
            <th scope="col">Status</th>
            <th scope="col">Created</th>
            <th scope="col">Actions</th>
          </tr>
        </thead>
        <tbody>
          {users.map((user) => {
            const isSelf = user.username === currentUsername;
            const disabled = busy || isSelf;
            return (
              <tr key={user.id} data-testid={`user-row-${user.username}`}>
                <td>
                  {user.username}
                  {isSelf ? <span className="muted"> (you)</span> : null}
                </td>
                <td>{user.email}</td>
                <td>
                  <select
                    className="field__select"
                    aria-label={`Role for ${user.username}`}
                    value={user.role}
                    disabled={disabled}
                    onChange={(e) => onChangeRole(user, e.target.value as Role)}
                  >
                    {ROLES.map((role) => (
                      <option key={role} value={role}>
                        {role}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  <span className={`badge ${user.enabled ? 'badge--on' : 'badge--off'}`}>
                    {user.enabled ? 'Enabled' : 'Disabled'}
                  </span>
                </td>
                <td>{formatDate(user.createdAt)}</td>
                <td>
                  <div className="row">
                    <Button
                      variant="secondary"
                      size="sm"
                      disabled={disabled}
                      onClick={() => onToggleEnabled(user)}
                      aria-label={`${user.enabled ? 'Disable' : 'Enable'} ${user.username}`}
                    >
                      {user.enabled ? 'Disable' : 'Enable'}
                    </Button>
                    <Button
                      variant="danger"
                      size="sm"
                      disabled={disabled}
                      onClick={() => onDelete(user)}
                      aria-label={`Delete ${user.username}`}
                    >
                      Delete
                    </Button>
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function formatDate(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString();
}
