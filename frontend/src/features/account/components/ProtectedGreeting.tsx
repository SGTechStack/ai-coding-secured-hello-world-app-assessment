import { useState } from "react";

import { Card, CardContent } from "@/common/components/ui/card";
import { Button } from "@/common/components/ui/button";
import { Badge } from "@/common/components/ui/badge";
import { Alert, AlertDescription } from "@/common/components/ui/alert";
import { logout, type UserRole } from "@/features/auth/api";
import { AdminUserList } from "@/features/admin/components/AdminUserList";
import { useGreeting } from "../hooks/useGreeting";
import { AccountDataPanel } from "./AccountDataPanel";

interface ProtectedGreetingProps {
  username: string;
  role: UserRole;
  onLoggedOut: () => void;
}

export function ProtectedGreeting({ username, role, onLoggedOut }: ProtectedGreetingProps) {
  const greeting = useGreeting();
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  async function handleLogout() {
    setIsLoggingOut(true);
    try {
      await logout();
    } catch {
      // Server-side invalidation failed (network down, stale CSRF token). Clear
      // client state anyway so the user isn't trapped in a session they've asked
      // to leave. Swallowing this is deliberate: without the catch, `finally`
      // would let the rejection escape an async handler whose promise nobody
      // holds, surfacing as an unhandled rejection.
    } finally {
      setIsLoggingOut(false);
      onLoggedOut();
    }
  }

  return (
    <>
      <Card>
        <CardContent className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex flex-col gap-1">
            <Badge variant={role === "ADMIN" ? "primary" : "default"} className="w-fit uppercase tracking-wide">
              {role}
            </Badge>

            {greeting.kind === "loading" && (
              <p className="text-[0.9rem] text-muted-foreground" role="status">
                Loading greeting…
              </p>
            )}
            {greeting.kind === "success" && (
              <p className="text-[1.05rem]" role="status">
                {greeting.greeting}
              </p>
            )}
            {greeting.kind === "error" && (
              <Alert variant="destructive">
                <AlertDescription>{greeting.message}</AlertDescription>
              </Alert>
            )}
          </div>

          <Button type="button" variant="secondary" onClick={handleLogout} disabled={isLoggingOut}>
            {isLoggingOut ? "Logging out…" : "Log out"}
          </Button>
        </CardContent>
      </Card>

      {role === "ADMIN" && (
        <Card>
          <CardContent>
            <AdminUserList currentUsername={username} />
          </CardContent>
        </Card>
      )}

      {/*
        Shown to every role, admins included: an admin is a data subject with the
        same rights over their own account as anyone else, which is why
        ACCOUNT_SELF_MANAGE is mapped to USER and reaches ADMIN through the role
        hierarchy rather than being granted separately.

        Erasure ends the session, so this reuses the same callback as logout —
        the account is gone and there is nothing left to render.
      */}
      <Card>
        <CardContent>
          <AccountDataPanel onErased={onLoggedOut} />
        </CardContent>
      </Card>
    </>
  );
}
