import type { Meta } from '@storybook/react-vite';
import { createRouter, createRootRoute, RouterProvider, createMemoryHistory } from '@tanstack/react-router';
import { NotFoundPage } from './not-found-page';

// NotFoundPage calls useRouter()/router.navigate, so it needs a router context.
function makeRouter() {
  const rootRoute = createRootRoute({ component: () => <NotFoundPage /> });
  const router = createRouter({
    routeTree: rootRoute,
    history: createMemoryHistory({ initialEntries: ['/missing'] }),
  });
  return <RouterProvider router={router} />;
}

const meta: Meta = {
  title: 'Layout/NotFoundPage',
  parameters: {
    layout: 'fullscreen',
  },
};

export default meta;

export const Default = {
  render: () => makeRouter(),
};
