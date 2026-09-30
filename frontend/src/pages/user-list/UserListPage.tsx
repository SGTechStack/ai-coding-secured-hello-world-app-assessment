import { useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { useEffect, useRef, useState } from 'react';
import { isAuthenticationRequired } from '../../common/http/api-client';
import { cn } from '../../common/lib/cn';
import { Alert } from '../../common/ui/alert';
import { Button } from '../../common/ui/button';
import { buttonClasses } from '../../common/ui/styles';
import {
  isAccessDenied,
  UserList,
  UserListSkeleton,
  userListQueryOptions,
  useUserList,
  type ListedAccount,
  type UserListPage,
} from '../../features/account/administration';
import { readSession } from '../../features/auth/session';
import { AccessDeniedPage } from '../access-denied/AccessDeniedPage';

/**
 * `/admin/users`, with the role guard of ADR 0007: anyone but an Admin sees Access denied at this address and the User
 * list is never requested. The role only decides what the client shows; the server still refuses the API.
 */
export function UserListPage() {
  const session = readSession(useQueryClient());
  if (session?.role !== 'ADMIN') return <AccessDeniedPage />;
  return <AdminUserList callerId={session.id} />;
}

/**
 * The page in the address is 1-based and sent to the API 0-based. A 403 (the role changed since login) shows Access
 * denied, and a 401 is left to the global Session-expired reaction, so the placeholder stays up while it navigates to
 * /login. Any other failure shows an alert with "Try again"; a failed page change keeps the previous rows below it.
 */
function AdminUserList({ callerId }: Readonly<{ callerId: string }>) {
  const { page = 1 } = useSearch({ from: '/_authenticated/admin/users' });
  const navigate = useNavigate({ from: '/admin/users' });
  const queryClient = useQueryClient();
  const { query, shown, loadingPage } = useUserList(callerId, page - 1);
  const list = useRef<HTMLElement>(null);
  // A toggled Account replaces its row in the shown page's cache, so the change appears without a reload.
  const onToggled = (account: ListedAccount) =>
    queryClient.setQueryData(
      userListQueryOptions(callerId, page - 1).queryKey,
      (current?: UserListPage) =>
        current && { ...current, content: current.content.map((row) => (row.id === account.id ? account : row)) },
    );
  // Set by "Try again": its button disappears once the retry succeeds, so focus would fall to the body.
  const [retried, setRetried] = useState(false);
  useEffect(() => {
    if (retried && query.isSuccess) list.current?.focus();
  }, [retried, query.isSuccess]);

  if (query.isError && isAccessDenied(query.error)) return <AccessDeniedPage />;
  const failed = query.isError && !isAuthenticationRequired(query.error);
  const retry = () => {
    setRetried(true);
    void query.refetch();
  };
  const goTo = (next: number) => void navigate({ search: { page: next > 1 ? next : undefined } });

  return (
    <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6">
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Users</h1>
      <p className="mt-1.5 text-sm text-ink-muted">Every account, newest first. Times are Singapore time (SGT).</p>
      <div className="mt-6 flex flex-col gap-3">
        {failed && (
          <div className="flex flex-col items-start gap-3">
            <Alert tone="danger" className="w-full">
              Unable to load users. Please try again.
            </Alert>
            <Button variant="secondary" onClick={retry}>
              Try again
            </Button>
          </div>
        )}
        {shown && shown.content.length === 0 && (
          <section
            tabIndex={-1}
            ref={list}
            aria-label="Users"
            className="rounded-card border border-line bg-surface px-4 py-10 text-center shadow-sm outline-none"
          >
            <p className="text-sm text-ink-muted">There are no users on this page.</p>
            <Link to="/admin/users" search={{}} className={cn(buttonClasses('secondary'), 'mt-4')}>
              Go to the first page
            </Link>
          </section>
        )}
        {shown && shown.content.length > 0 && (
          <UserList
            ref={list}
            shown={shown}
            callerId={callerId}
            busy={loadingPage}
            onPageChange={goTo}
            onToggled={onToggled}
          />
        )}
        {!shown && !failed && <UserListSkeleton />}
      </div>
    </main>
  );
}
