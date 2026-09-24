import { LogOut } from "lucide-react";
import { Button } from "@/components/ui/button";

type LogoutButtonProps = { onClick: () => void; pending: boolean };

/**
 * Icon plus "Log out", or disabled and "Logging out..." while pending. On narrow screens the label
 * is visually hidden but stays the accessible name.
 */
export function LogoutButton({ onClick, pending }: LogoutButtonProps) {
  return (
    <Button variant="outline" onClick={onClick} disabled={pending}>
      <LogOut aria-hidden />
      <span className="sr-only sm:not-sr-only">
        {pending ? "Logging out..." : "Log out"}
      </span>
    </Button>
  );
}
