import { createRootRouteWithContext, Outlet } from '@tanstack/react-router';
import { TanStackRouterDevtools } from '@tanstack/router-devtools';
import type { QueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { ErrorPage } from '@components/layout/error-page';
import { NotFoundPage } from '@components/layout/not-found-page';
import { SessionExpiredDialog } from '@components/layout/session-expired-dialog';
import { SESSION_EXPIRED_EVENT } from '@lib/auth';

interface RouterContext {
  queryClient: QueryClient;
}

export const Route = createRootRouteWithContext<RouterContext>()({
  errorComponent: ({ reset }) => <ErrorPage reset={reset} />,
  notFoundComponent: () => <NotFoundPage />,
  component: RootLayout,
});

function RootLayout() {
  const [sessionExpired, setSessionExpired] = useState(false);

  useEffect(() => {
    const handler = () => setSessionExpired(true);
    window.addEventListener(SESSION_EXPIRED_EVENT, handler);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handler);
  }, []);

  return (
    <div className="flex min-h-svh flex-col">
      <main className="flex flex-1 flex-col">
        <Outlet />
      </main>
      <SessionExpiredDialog open={sessionExpired} />
      {import.meta.env.DEV && <TanStackRouterDevtools />}
    </div>
  );
}
