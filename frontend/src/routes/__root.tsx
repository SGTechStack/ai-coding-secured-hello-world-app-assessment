import { Outlet, createRootRouteWithContext } from '@tanstack/react-router';
import type { QueryClient } from '@tanstack/react-query';
import { PageNotFound } from '../pages/not-found/PageNotFound';

// The router is the only page list (ADR 0007): the server loads the app for every frontend path.
export const Route = createRootRouteWithContext<{ queryClient: QueryClient }>()({
  component: Outlet,
  notFoundComponent: PageNotFound,
});
