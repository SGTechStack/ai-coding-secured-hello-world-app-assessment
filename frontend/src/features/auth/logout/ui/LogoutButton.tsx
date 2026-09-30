import { LogOut } from 'lucide-react';
import { Alert } from '../../../../common/ui/alert';
import { Button } from '../../../../common/ui/button';
import { useLogout } from '../hooks/useLogout';

/** Fixed copy: nothing from the failed response is ever rendered (IM8 as-3). */
const FAILURE = "Couldn't log out. Check your connection and try again.";

/**
 * Ends the User's Session. Disabled while Logout is pending, so repeated clicks never race. If Logout could not reach
 * the server, the User stays signed in and can retry; the alert clears as soon as the retry starts.
 */
export function LogoutButton() {
  const logout = useLogout();
  return (
    <div className="flex flex-col items-end gap-2">
      <Button disabled={logout.isPending} onClick={() => logout.mutate()} variant="secondary">
        <LogOut aria-hidden="true" className="size-4" />
        {logout.isPending ? 'Logging out…' : 'Log out'}
      </Button>
      {logout.isError && (
        <Alert className="max-w-xs" tone="danger">
          {FAILURE}
        </Alert>
      )}
    </div>
  );
}
