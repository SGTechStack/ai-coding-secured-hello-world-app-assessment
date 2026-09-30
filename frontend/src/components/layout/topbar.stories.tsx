import type { Meta } from '@storybook/react-vite';
import { http, HttpResponse } from 'msw';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createRouter, createRootRoute, RouterProvider, createMemoryHistory } from '@tanstack/react-router';
import { Suspense } from 'react';
import { Topbar } from './topbar';

function makeRouter() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const rootRoute = createRootRoute({ component: () => <Topbar /> });
  const router = createRouter({
    routeTree: rootRoute,
    history: createMemoryHistory({ initialEntries: ['/'] }),
  });
  return (
    <QueryClientProvider client={client}>
      <Suspense fallback={null}>
        <RouterProvider router={router} />
      </Suspense>
    </QueryClientProvider>
  );
}

const meta: Meta = {
  title: 'Layout/Topbar',
  parameters: {
    layout: 'fullscreen',
  },
};

export default meta;

const meHandler = ({ displayName, username }: { displayName: string; username: string }) =>
  http.get('/api/v1/me', () => HttpResponse.json({ username, displayName, roles: ['user'] }));

export const LoggedIn = {
  parameters: {
    msw: { handlers: [meHandler({ displayName: 'John Smith', username: 'jsmith' })] },
  },
  render: () => makeRouter(),
};

export const LongName = {
  parameters: {
    msw: { handlers: [meHandler({ displayName: 'Alexandra Van Der Berg', username: 'a.vanderberg' })] },
  },
  render: () => makeRouter(),
};
