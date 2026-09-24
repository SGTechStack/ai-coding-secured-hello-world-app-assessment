import { useQuery } from "@tanstack/react-query";
import { LoaderCircle } from "lucide-react";
import { useId } from "react";
import { adminUsersQueryOptions, type AdminUser } from "../api/admin";
import { meQueryOptions } from "../api/auth";
import { ErrorAlert } from "@/components/ErrorAlert";
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
 * shown literally.
 */
export function AdminUsersPage() {
  const { data: me } = useQuery(meQueryOptions);
  const users = useQuery(adminUsersQueryOptions);

  return (
    <Card className="w-full max-w-5xl">
      <CardHeader>
        <h1 className="text-2xl font-semibold tracking-tight">Users</h1>
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
        {users.data && (
          <UserTable users={users.data} currentUsername={me?.username} />
        )}
      </CardContent>
    </Card>
  );
}

function UserTable({
  users,
  currentUsername,
}: {
  users: AdminUser[];
  currentUsername: string | undefined;
}) {
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
            />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function UserRow({ user, isSelf }: { user: AdminUser; isSelf: boolean }) {
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
        {isSelf && <OwnAccountActions user={user} />}
      </td>
    </tr>
  );
}

/**
 * The row actions, disabled on the admin's own account: an admin can't disable, demote or delete
 * themselves (the API refuses it too), which also means at least one admin always remains. The
 * explanation is each button's accessible description.
 */
function OwnAccountActions({ user }: { user: AdminUser }) {
  const noteId = useId();
  return (
    <div className="flex flex-col gap-1">
      <div className="flex flex-wrap gap-2">
        <Button size="sm" variant="outline" disabled aria-describedby={noteId}>
          {user.enabled ? "Disable" : "Enable"}
        </Button>
        <Button size="sm" variant="outline" disabled aria-describedby={noteId}>
          {user.role === "ADMIN" ? "Make user" : "Make admin"}
        </Button>
        <Button
          size="sm"
          variant="destructive"
          disabled
          aria-describedby={noteId}
        >
          Delete
        </Button>
      </div>
      <p id={noteId} className="text-xs text-muted-foreground">
        You can't change your own account.
      </p>
    </div>
  );
}
