import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Outlet, useNavigate } from "@tanstack/react-router";
import { LockKeyhole } from "lucide-react";
import { logout, meQueryOptions } from "../api/auth";
import { ErrorAlert } from "./ErrorAlert";
import { LogoutButton } from "./LogoutButton";
import { ModeToggle } from "./mode-toggle";

/**
 * Every page: a slim header with the brand on the left and, on the right, Log out (only while a
 * session profile is cached) next to the theme toggle; content centred below. A failed logout
 * leaves the user where they are, with an alert directly below the header until they try again.
 */
export function AppShell() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  // Reads the cached profile without fetching; asking the server is the route guards' job.
  const { data: me } = useQuery({ ...meQueryOptions, enabled: false });
  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: () => {
      // Order matters: forget the old session's data, then leave with no Back entry to `/`.
      queryClient.clear();
      return navigate({ to: "/login", replace: true });
    },
  });

  return (
    <div className="flex min-h-svh flex-col bg-muted/40">
      <header className="flex items-center justify-between px-6 py-4">
        <div className="flex items-center gap-2 text-sm font-semibold">
          <span className="flex size-7 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <LockKeyhole className="size-4" />
          </span>
          Simple Login
        </div>
        <div className="flex items-center gap-2">
          {me && (
            <LogoutButton
              onClick={() => logoutMutation.mutate()}
              pending={logoutMutation.isPending}
            />
          )}
          <ModeToggle />
        </div>
      </header>
      {/* A new attempt resets the mutation, which clears the alert. */}
      {logoutMutation.isError && (
        <div className="mx-auto w-full max-w-md px-6">
          <ErrorAlert>Unable to log out. Please try again.</ErrorAlert>
        </div>
      )}
      <main className="flex flex-1 items-center justify-center p-6 pb-24">
        <Outlet />
      </main>
    </div>
  );
}
