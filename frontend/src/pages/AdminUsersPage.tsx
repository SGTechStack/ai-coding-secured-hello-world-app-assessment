import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../api/admin';
import type { Role, UserSummary } from '../api/types';
import { useAuth } from '../auth/useAuth';
import { Alert } from '../components/ui/Alert';
import { Card } from '../components/ui/Card';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { UserTable } from '../features/admin/UserTable';
import { describeError } from '../lib/errors';

const USERS_QUERY_KEY = ['admin', 'users'] as const;

/** Stories 8-11. */
export function AdminUsersPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const users = useQuery({ queryKey: USERS_QUERY_KEY, queryFn: adminApi.listUsers });
  const [actionError, setActionError] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<UserSummary | null>(null);

  const refresh = () => queryClient.invalidateQueries({ queryKey: USERS_QUERY_KEY });
  const onError = (error: unknown) => setActionError(describeError(error));

  const setEnabled = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) => adminApi.setEnabled(id, enabled),
    onMutate: () => setActionError(null),
    onSuccess: refresh,
    onError,
  });

  const changeRole = useMutation({
    mutationFn: ({ id, role }: { id: string; role: Role }) => adminApi.changeRole(id, role),
    onMutate: () => setActionError(null),
    onSuccess: refresh,
    onError,
  });

  const deleteUser = useMutation({
    mutationFn: (id: string) => adminApi.deleteUser(id),
    onMutate: () => setActionError(null),
    onSuccess: () => {
      setPendingDelete(null);
      return refresh();
    },
    onError,
  });

  const busy = setEnabled.isPending || changeRole.isPending || deleteUser.isPending;

  return (
    <div className="stack">
      <Card>
        <h1>User accounts</h1>
        <p className="muted">
          Actions on your own account are disabled here and rejected by the server.
        </p>
        {actionError ? <Alert tone="error">{actionError}</Alert> : null}
        {users.isPending ? <p className="muted">Loading users…</p> : null}
        {users.isError ? <Alert tone="error">{describeError(users.error)}</Alert> : null}
        {users.data ? (
          <UserTable
            users={users.data}
            currentUsername={user?.username ?? null}
            busy={busy}
            onToggleEnabled={(target) =>
              setEnabled.mutate({ id: target.id, enabled: !target.enabled })
            }
            onChangeRole={(target, role) => changeRole.mutate({ id: target.id, role })}
            onDelete={(target) => setPendingDelete(target)}
          />
        ) : null}
      </Card>

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete account?"
        confirmLabel="Delete"
        busy={deleteUser.isPending}
        onCancel={() => setPendingDelete(null)}
        onConfirm={() => pendingDelete && deleteUser.mutate(pendingDelete.id)}
      >
        <p>
          This permanently removes <strong>{pendingDelete?.username}</strong> and ends any of
          their active sessions. This cannot be undone.
        </p>
      </ConfirmDialog>
    </div>
  );
}
