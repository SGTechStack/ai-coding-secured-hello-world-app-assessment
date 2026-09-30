import type { Meta } from '@storybook/react-vite';
import { createRouter, createRootRoute, RouterProvider, createMemoryHistory } from '@tanstack/react-router';
import { SessionExpiredDialog } from './session-expired-dialog';

// SessionExpiredDialog reads location via useRouterState, so it needs a router context.
function withRouter(open: boolean) {
  const rootRoute = createRootRoute({ component: () => <SessionExpiredDialog open={open} /> });
  const router = createRouter({
    routeTree: rootRoute,
    history: createMemoryHistory({ initialEntries: ['/'] }),
  });
  return <RouterProvider router={router} />;
}

const meta: Meta = {
  title: 'Layout/SessionExpiredDialog',
  parameters: {
    layout: 'fullscreen',
  },
};

export default meta;

export const Open = {
  render: () => withRouter(true),
};

export const Closed = {
  render: () => withRouter(false),
};
