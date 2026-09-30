import { createRootRouteWithContext, Outlet, useRouter } from '@tanstack/react-router';
import { TanStackRouterDevtools } from '@tanstack/router-devtools';
import type { QueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Topbar } from '@components/layout/topbar';
import { ErrorPage } from '@components/layout/error-page';
import { NotFoundPage } from '@components/layout/not-found-page';
import { SessionExpiredDialog } from '@components/layout/session-expired-dialog';
import { currentUserQueryOptions } from '@features/user/user.queries';
import { HttpError } from '@lib/http';
import { SESSION_EXPIRED_EVENT, RETURN_TO_KEY } from '@lib/auth';

interface RouterContext {
  queryClient: QueryClient;
}

export const Route = createRootRouteWithContext<RouterContext>()({
  errorComponent: ({ reset }) => <ErrorPage reset={reset} />,
  notFoundComponent: () => <NotFoundPage />,
  loader: async ({ context: { queryClient } }) => {
    try {
      await queryClient.ensureQueryData(currentUserQueryOptions());
    } catch (error) {
      if (error instanceof HttpError && error.status === 401) {
        // http() already triggered the redirect to /login — just suppress the error
        return;
      }
      throw error;
    }
  },
  component: RootLayout,
});

function RootLayout() {
  const [sessionExpired, setSessionExpired] = useState(false);
  const router = useRouter();

  useEffect(() => {
    const returnTo = sessionStorage.getItem(RETURN_TO_KEY);
    sessionStorage.removeItem(RETURN_TO_KEY);
    // Only redirect back if we landed on root — avoids consuming a stale returnTo
    // when the user navigated directly to another route after re-authenticating.
    // router.state is a stable ref read inside the effect, not a reactive dep.
    if (returnTo && router.state.location.pathname === '/') {
      void router.navigate({ to: returnTo });
    }
  }, [router]);

  useEffect(() => {
    const handler = () => setSessionExpired(true);
    window.addEventListener(SESSION_EXPIRED_EVENT, handler);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handler);
  }, []);

  return (
    <div className="flex min-h-svh flex-col">
      <Topbar />
      <main className="flex flex-1 flex-col">
        <Outlet />
      </main>
      <SessionExpiredDialog open={sessionExpired} />
      {import.meta.env.DEV && <TanStackRouterDevtools />}
    </div>
  );
}
