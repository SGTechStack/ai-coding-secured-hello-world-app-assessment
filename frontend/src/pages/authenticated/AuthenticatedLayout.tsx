import { useQueryClient } from '@tanstack/react-query';
import { Outlet } from '@tanstack/react-router';
import { BrandMark } from '../../common/ui/brand-mark';
import { LogoutButton } from '../../features/auth/logout';
import { readSession } from '../../features/auth/session';
import { MainNav } from './MainNav';

/** Frame for every protected page: the logo and Log out control, the navigation row, then the page's own content. */
export function AuthenticatedLayout() {
  const role = readSession(useQueryClient())?.role;
  return (
    <div className="flex min-h-screen flex-col bg-canvas text-ink">
      <header className="flex items-center justify-between gap-3 border-b border-line bg-surface px-4 py-2 sm:px-6">
        <BrandMark />
        <LogoutButton />
      </header>
      <MainNav role={role} />
      <Outlet />
    </div>
  );
}
