import { Button } from "@/common/components/ui/button";
import { Input } from "@/common/components/ui/input";
import { Label } from "@/common/components/ui/label";
import { Alert, AlertDescription } from "@/common/components/ui/alert";
import { Badge } from "@/common/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/common/components/ui/table";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/common/components/ui/select";
import { cn } from "@/common/lib/utils";
import type { UserRole } from "@/features/auth/api";
import { useAdminUsers } from "../hooks/useAdminUsers";

interface AdminUserListProps {
  currentUsername: string;
}

export function AdminUserList({ currentUsername }: AdminUserListProps) {
  const {
    state,
    isRefreshing,
    actionError,
    pendingUserIds,
    challenge,
    setChallenge,
    revealedEmails,
    toggleEnabled,
    changeRole,
    unlock,
    submitChallenge,
  } = useAdminUsers();

  return (
    <section>
      <h3 className="mb-4 text-[1.05rem] font-semibold">All users</h3>

      <p className="mb-4 text-[0.78rem] text-muted-foreground">
        Email addresses are masked. Revealing one is recorded in the audit log with the reason you give.
      </p>

      {state.kind === "loading" && (
        <p className="text-[0.9rem] text-muted-foreground" role="status">
          Loading users…
        </p>
      )}

      {state.kind === "error" && (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{state.message}</AlertDescription>
        </Alert>
      )}

      {actionError && (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{actionError}</AlertDescription>
        </Alert>
      )}

      {challenge && (
        <form
          className="mb-4 flex flex-col gap-3 rounded-lg border px-4 py-3"
          onSubmit={(e) => {
            e.preventDefault();
            void submitChallenge();
          }}
        >
          {challenge.kind === "confirm-delete" ? (
            <>
              <p className="text-sm">
                Deleting <strong>{challenge.user.username}</strong> cannot be undone. Enter your own password to
                confirm.
              </p>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="confirm-password">Your password</Label>
                <Input
                  id="confirm-password"
                  name="confirm-password"
                  type="password"
                  autoComplete="current-password"
                  autoFocus
                  value={challenge.value}
                  onChange={(e) => setChallenge({ ...challenge, value: e.target.value, error: null })}
                />
              </div>
            </>
          ) : (
            <>
              <p className="text-sm">
                Why do you need <strong>{challenge.user.username}</strong>&apos;s email address? This is stored in
                the audit log.
              </p>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="reveal-purpose">Reason</Label>
                <Input
                  id="reveal-purpose"
                  name="reveal-purpose"
                  type="text"
                  autoFocus
                  value={challenge.value}
                  onChange={(e) => setChallenge({ ...challenge, value: e.target.value, error: null })}
                />
              </div>
            </>
          )}

          {challenge.error && (
            <Alert variant="destructive">
              <AlertDescription>{challenge.error}</AlertDescription>
            </Alert>
          )}

          <div className="flex items-center gap-2">
            <Button type="submit" variant={challenge.kind === "confirm-delete" ? "destructive" : "default"}>
              {challenge.kind === "confirm-delete" ? "Delete account" : "Reveal address"}
            </Button>
            <Button type="button" variant="secondary" onClick={() => setChallenge(null)}>
              Cancel
            </Button>
          </div>
        </form>
      )}

      {state.kind === "success" && (
        <div
          className={cn("rounded-md border transition-opacity", isRefreshing && "opacity-60")}
          aria-busy={isRefreshing}
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Username</TableHead>
                <TableHead>Email</TableHead>
                <TableHead>Role</TableHead>
                <TableHead>Enabled</TableHead>
                <TableHead>Lockout</TableHead>
                <TableHead>Created</TableHead>
                <TableHead>Logins</TableHead>
                <TableHead>Last login</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {state.users.map((user) => {
                const isSelf = user.username === currentUsername;
                // Locked while this row is mutating, and also while the list is
                // refreshing — acting on rows that are about to be replaced by
                // fresh server data would be acting on stale ids.
                const isLocked = isSelf || pendingUserIds.has(user.id) || isRefreshing;
                const revealed = revealedEmails.get(user.id);

                return (
                  <TableRow key={user.id}>
                    <TableCell>{user.username}</TableCell>
                    <TableCell>
                      {revealed ?? user.maskedEmail}
                      {!revealed && (
                        <Button
                          type="button"
                          variant="link"
                          className="h-auto p-0 pl-1 text-sm"
                          onClick={() => setChallenge({ kind: "state-purpose", user, value: "", error: null })}
                        >
                          Reveal
                        </Button>
                      )}
                    </TableCell>
                    <TableCell>
                      <Badge variant={user.role === "ADMIN" ? "primary" : "default"}>{user.role}</Badge>
                    </TableCell>
                    <TableCell>
                      <span
                        className={cn(
                          "inline-flex items-center gap-1.5 text-[0.82rem] before:size-2 before:rounded-full",
                          user.enabled
                            ? "text-success before:bg-success"
                            : "text-destructive before:bg-destructive",
                        )}
                      >
                        {user.enabled ? "Enabled" : "Disabled"}
                      </span>
                    </TableCell>
                    <TableCell>
                      {user.locked ? (
                        <span className="inline-flex items-center gap-1.5 text-[0.82rem] text-destructive before:size-2 before:rounded-full before:bg-destructive">
                          Locked
                        </span>
                      ) : (
                        <span className="text-[0.82rem] text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    <TableCell>{new Date(user.createdAt).toLocaleString()}</TableCell>
                    <TableCell>{user.loginCount}</TableCell>
                    <TableCell>
                      {user.lastLoginAt ? (
                        new Date(user.lastLoginAt).toLocaleString()
                      ) : (
                        <span className="text-muted-foreground">Never</span>
                      )}
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <Button
                          type="button"
                          variant="secondary"
                          size="sm"
                          disabled={isLocked}
                          onClick={() => toggleEnabled(user)}
                        >
                          {user.enabled ? "Disable" : "Enable"}
                        </Button>

                        {user.locked && (
                          <Button
                            type="button"
                            variant="secondary"
                            size="sm"
                            disabled={isLocked}
                            onClick={() => unlock(user)}
                          >
                            Unlock
                          </Button>
                        )}

                        <Select
                          value={user.role}
                          disabled={isLocked}
                          onValueChange={(value) => changeRole(user, value as UserRole)}
                        >
                          <SelectTrigger
                            size="sm"
                            aria-label={`Change role for ${user.username}`}
                            className="w-[6.5rem]"
                          >
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="USER">USER</SelectItem>
                            <SelectItem value="ADMIN">ADMIN</SelectItem>
                          </SelectContent>
                        </Select>

                        <Button
                          type="button"
                          variant="destructive"
                          size="sm"
                          disabled={isLocked}
                          onClick={() => setChallenge({ kind: "confirm-delete", user, value: "", error: null })}
                        >
                          Delete
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}
    </section>
  );
}
