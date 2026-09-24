import { Outlet } from "@tanstack/react-router";
import { LockKeyhole } from "lucide-react";
import { ModeToggle } from "./mode-toggle";

/** Every page: a slim header with the brand on the left and the theme toggle on the right; content centred below. */
export function AppShell() {
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
          <ModeToggle />
        </div>
      </header>
      <main className="flex flex-1 items-center justify-center p-6 pb-24">
        <Outlet />
      </main>
    </div>
  );
}
