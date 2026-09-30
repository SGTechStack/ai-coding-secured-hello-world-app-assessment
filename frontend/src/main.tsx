import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider, createRouter } from '@tanstack/react-router';
import { QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { queryClient } from '@/lib/query-client';
import { routeTree } from '@/routeTree.gen';
import '@/index.css';

const router = createRouter({
  routeTree,
  context: { queryClient },
  defaultPreload: 'intent',
  basepath: import.meta.env.VITE_BASE_URL.replace(/\/$/, ''), // Remove trailing slash if present (Vite's BASE_URL always has a trailing slash)
});

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      {import.meta.env.DEV && <ReactQueryDevtools />}
    </QueryClientProvider>
  </StrictMode>,
);
