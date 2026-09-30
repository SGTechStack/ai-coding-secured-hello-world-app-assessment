import { useRouterState } from '@tanstack/react-router';
import { RETURN_TO_KEY } from '@lib/auth';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@components/ui/dialog';
import { Button } from '@components/ui/button';

interface SessionExpiredDialogProps {
  open: boolean;
}

export function SessionExpiredDialog({ open }: SessionExpiredDialogProps) {
  const location = useRouterState({ select: (s) => s.location });

  const handleSignIn = () => {
    sessionStorage.setItem(RETURN_TO_KEY, location.pathname + location.searchStr + location.hash);
    window.location.href = import.meta.env.VITE_LOGIN_URL ?? '/login';
  };

  return (
    <Dialog open={open} onOpenChange={() => {}}>
      <DialogContent showCloseButton={false} className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Session expired</DialogTitle>
          <DialogDescription>Your session has timed out. Please sign in again to continue.</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button onClick={handleSignIn}>Sign in</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
