import { useQuery } from "@tanstack/react-query";
import { LoaderCircle } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import {
  adminUsersQueryOptions,
  deleteUser,
  setUserEnabled,
  setUserRole,
  useAdminUserAction,
  type AdminUser,
} from "../api/admin";
import { meQueryOptions } from "../api/auth";
import { hasCode } from "../api/client";
import { ErrorAlert } from "@/components/ErrorAlert";
import { AlertDialog } from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
} from "@/components/ui/card";

const createdFormat = new Intl.DateTimeFormat(undefined, {
  dateStyle: "medium",
});

/**
 * Every account in a semantic table, for admins only (the route guard sends anyone else away, and
 * the API refuses them anyway). All values are JSX text, so a first name that looks like HTML is
 * shown literally. After a delete, the deleted row's controls are gone, so focus moves to the
 * page heading rather than falling back to the document body.
 */
export function AdminUsersPage() {
  const { data: me } = useQuery(meQueryOptions);
  const users = useQuery(adminUsersQueryOptions);
  // The latest failed row action; cleared when the next one starts.
  const [actionFailure, setActionFailure] = useState<string | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const [deleted, setDeleted] = useState(false);

  // Runs after the confirmation dialog has closed (a child's effect), so the page isn't inert.
  useEffect(() => {
    if (!deleted) return;
    heading.current?.focus();
    setDeleted(false);
  }, [deleted]);

  return (
    <Card className="w-full max-w-5xl">
      <CardHeader>
        <h1
          ref={heading}
          tabIndex={-1}
          className="text-2xl font-semibold tracking-tight outline-none"
        >
          Users
        </h1>
        <CardDescription>Everyone who has an account.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        {users.isError && (
          <ErrorAlert>Unable to load users. Please try again later.</ErrorAlert>
        )}
        {users.isPending && (
          <p className="flex items-center gap-2 text-sm text-muted-foreground">
            <LoaderCircle
              className="size-4 animate-spin motion-reduce:animate-none"
              aria-hidden
            />
            Loading users...
          </p>
        )}
        {actionFailure && <ErrorAlert>{actionFailure}</ErrorAlert>}
        {users.data && (
          <UserTable
            users={users.data}
            currentUsername={me?.username}
            onFailure={setActionFailure}
            onDeleted={() => setDeleted(true)}
          />
        )}
      </CardContent>
    </Card>
  );
}

/** What a row reports back to the page. */
type RowCallbacks = {
  onFailure: (message: string | null) => void;
  onDeleted: () => void;
};

function UserTable({
  users,
  currentUsername,
  ...callbacks
}: {
  users: AdminUser[];
  currentUsername: string | undefined;
} & RowCallbacks) {
  const headerClass = "px-3 py-2 text-left font-medium text-muted-foreground";
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <caption className="sr-only">Users</caption>
        <thead className="border-b">
          <tr>
            <th scope="col" className={headerClass}>
              Username
            </th>
            <th scope="col" className={headerClass}>
              Email
            </th>
            <th scope="col" className={headerClass}>
              First name
            </th>
            <th scope="col" className={headerClass}>
              Role
            </th>
            <th scope="col" className={headerClass}>
              Status
            </th>
            <th scope="col" className={headerClass}>
              Created
            </th>
            <th scope="col" className={headerClass}>
              Actions
            </th>
          </tr>
        </thead>
        <tbody>
          {users.map((user) => (
            <UserRow
              key={user.id}
              user={user}
              isSelf={user.username === currentUsername}
              {...callbacks}
            />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function UserRow({
  user,
  isSelf,
  ...callbacks
}: { user: AdminUser; isSelf: boolean } & RowCallbacks) {
  const cellClass = "px-3 py-2 align-top";
  return (
    <tr className="border-b last:border-b-0">
      <th scope="row" className={`${cellClass} text-left font-medium`}>
        {user.username}
      </th>
      <td className={`${cellClass} break-all`}>{user.email}</td>
      <td className={`${cellClass} break-all`}>{user.firstName}</td>
      <td className={cellClass}>{user.role}</td>
      <td className={cellClass}>{user.enabled ? "Enabled" : "Disabled"}</td>
      <td className={`${cellClass} whitespace-nowrap`}>
        <time dateTime={user.createdAt}>
          {createdFormat.format(new Date(user.createdAt))}
        </time>
      </td>
      <td className={cellClass}>
        <RowActions user={user} isSelf={isSelf} {...callbacks} />
      </td>
    </tr>
  );
}

/** What an admin sees when an action fails. */
function actionFailureMessage(error: Error, username: string): string {
  if (hasCode(error, "USER_NOT_FOUND")) return `${username} no longer exists.`;
  if (hasCode(error, "SELF_ACTION_NOT_ALLOWED")) return SELF_ACTION_NOTE;
  return `Unable to change ${username}. Please try again later.`;
}

const SELF_ACTION_NOTE = "You can't change your own account.";

/**
 * A row's actions: enable/disable, make admin/user, and delete after confirming in an
 * `AlertDialog`. Each success refetches the list. On the admin's own row they are disabled, with
 * the explanation as each button's accessible description: an admin can't disable, demote or
 * delete themselves (the API refuses it too), so at least one admin always remains.
 */
function RowActions({
  user,
  isSelf,
  onFailure,
  onDeleted,
}: { user: AdminUser; isSelf: boolean } & RowCallbacks) {
  const noteId = useId();
  const action = useAdminUserAction();
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  function run(change: () => Promise<unknown>, onSuccess?: () => void) {
    onFailure(null);
    action.mutate(change, {
      onSuccess: () => {
        setConfirmingDelete(false);
        onSuccess?.();
      },
      onError: (error) => {
        setConfirmingDelete(false);
        onFailure(actionFailureMessage(error, user.username));
      },
    });
  }

  const disabled = isSelf || action.isPending;
  const describedBy = isSelf ? noteId : undefined;
  return (
    <div className="flex flex-col gap-1">
      <div className="flex flex-wrap gap-2">
        <Button
          size="sm"
          variant="outline"
          disabled={disabled}
          aria-describedby={describedBy}
          onClick={() => run(() => setUserEnabled(user.id, !user.enabled))}
        >
          {user.enabled ? "Disable" : "Enable"}
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={disabled}
          aria-describedby={describedBy}
          onClick={() =>
            run(() =>
              setUserRole(user.id, user.role === "ADMIN" ? "USER" : "ADMIN"),
            )
          }
        >
          {user.role === "ADMIN" ? "Make user" : "Make admin"}
        </Button>
        <Button
          size="sm"
          variant="destructive"
          disabled={disabled}
          aria-describedby={describedBy}
          onClick={() => setConfirmingDelete(true)}
        >
          Delete
        </Button>
      </div>
      {isSelf && (
        <p id={noteId} className="text-xs text-muted-foreground">
          {SELF_ACTION_NOTE}
        </p>
      )}
      <AlertDialog
        open={confirmingDelete}
        onOpenChange={setConfirmingDelete}
        title={`Delete ${user.username}?`}
        description="This permanently deletes the account and signs it out everywhere. It can't be undone."
      >
        <Button
          variant="outline"
          disabled={action.isPending}
          onClick={() => setConfirmingDelete(false)}
        >
          Cancel
        </Button>
        <Button
          variant="destructive"
          disabled={action.isPending}
          onClick={() => run(() => deleteUser(user.id), onDeleted)}
        >
          {action.isPending ? "Deleting..." : "Delete"}
        </Button>
      </AlertDialog>
    </div>
  );
}
