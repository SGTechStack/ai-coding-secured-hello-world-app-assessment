import { useRouter } from '@tanstack/react-router';
import { Button } from '../../common/ui/button';
import { Dialog } from '../../common/ui/dialog';
import { SessionPlaceholder } from './SessionRestoring';

/**
 * The server could not be reached to check the Session after a reload. "Try again" re-runs the route guard. There is
 * nothing behind the dialog to return to, so Esc keeps it open; "Go to login" is the way out.
 */
export function SessionRestoreFailed() {
  const router = useRouter();
  return (
    <>
      <SessionPlaceholder busy={false} />
      <Dialog
        open
        title="Unable to reach the server"
        description="Check your connection, then try again."
        onClose={() => {}}
      >
        <Button onClick={() => void router.invalidate()}>Try again</Button>
        <Button variant="secondary" onClick={() => void router.navigate({ to: '/login' })}>
          Go to login
        </Button>
      </Dialog>
    </>
  );
}
