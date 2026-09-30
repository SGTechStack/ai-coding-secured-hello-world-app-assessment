import { Link } from '@tanstack/react-router';
import { LogOut, Moon, ShieldCheck, Sun, User } from 'lucide-react';
import { useTheme } from '@lib/theme';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@components/ui/dropdown-menu';
import { Button } from '@components/ui/button';
import { Avatar, AvatarFallback } from '@components/ui/avatar';
import { ADMIN_ROLE } from '@lib/auth';
import { http } from '@lib/http';
import { useCurrentUser, getInitials } from '@features/user/user.queries';

const APP_NAME = 'DEMO';

export function Topbar() {
  const { data: user } = useCurrentUser();
  const { theme, toggle } = useTheme();
  const logoutUrl = import.meta.env.VITE_LOGOUT_URL ?? '/logout';

  function handleLogout() {
    http(logoutUrl, { method: 'POST' }).then(() => {
      window.location.href = '/';
    });
  }

  return (
    <header className="bg-surface-elevated h-topbar sticky top-0 z-40 flex items-center border-b px-6">
      <div className="flex flex-1 items-center gap-2">
        <Link to="/" className="text-sm font-semibold tracking-tight">
          {APP_NAME}
        </Link>
      </div>

      <div className="flex flex-1 items-center justify-end gap-3">
        {user.roles.includes(ADMIN_ROLE) && (
          <Button
            variant="ghost"
            size="sm"
            nativeButton={false}
            role="link"
            render={<Link to="/admin/accounts" activeProps={{ className: 'bg-bg-muted' }} />}
          >
            <ShieldCheck className="size-4" />
            Admin
          </Button>
        )}
        <DropdownMenu>
          <DropdownMenuTrigger className="focus-visible:outline-accent flex items-center gap-2 rounded-full focus-visible:outline-1 focus-visible:outline-offset-0">
            <Avatar>
              <AvatarFallback>{getInitials(user.displayName)}</AvatarFallback>
            </Avatar>
            <span className="hidden text-sm font-medium sm:inline">{user.username}</span>
          </DropdownMenuTrigger>

          <DropdownMenuContent>
            <DropdownMenuLabel>
              <p>{user.username}</p>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem>
              <User />
              Profile
            </DropdownMenuItem>
            <DropdownMenuItem onClick={toggle}>
              {theme === 'dark' ? <Sun /> : <Moon />}
              {theme === 'dark' ? 'Light mode' : 'Dark mode'}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem className="text-danger focus:text-danger" onClick={handleLogout}>
              <LogOut />
              Log out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}
