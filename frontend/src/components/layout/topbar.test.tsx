import { describe, it, expect, vi, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http as mswHttp, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { Suspense } from 'react';
import { RouterProvider, createRouter, createMemoryHistory, createRootRoute } from '@tanstack/react-router';
import { Topbar } from './topbar';

// useTheme uses localStorage + matchMedia which are unreliable in the test environment;
// theme behaviour has its own test in theme.test.ts
vi.mock('@lib/theme', () => ({
  useTheme: () => ({ theme: 'light', toggle: vi.fn() }),
}));

const server = setupServer(
  mswHttp.get('/api/v1/me', () =>
    HttpResponse.json({ username: 'jsmith', displayName: 'John Smith', roles: ['user'] }),
  ),
  mswHttp.post('/logout', () => new HttpResponse(null, { status: 200 })),
);

const originalLocation = window.location;
beforeAll(() => server.listen());
afterEach(() => {
  server.resetHandlers();
  Object.defineProperty(window, 'location', { value: originalLocation, writable: true, configurable: true });
});
afterAll(() => server.close());

function renderTopbar() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const rootRoute = createRootRoute({ component: () => <Topbar /> });
  const router = createRouter({
    routeTree: rootRoute,
    history: createMemoryHistory({ initialEntries: ['/'] }),
  });
  render(
    <QueryClientProvider client={client}>
      <Suspense fallback={null}>
        <RouterProvider router={router} />
      </Suspense>
    </QueryClientProvider>,
  );
}

describe('Topbar', () => {
  it('renders the logged-in username', async () => {
    renderTopbar();
    await waitFor(() => expect(screen.getByText('jsmith')).toBeInTheDocument());
  });

  it('renders initials in the avatar', async () => {
    renderTopbar();
    await waitFor(() => expect(screen.getByText('JS')).toBeInTheDocument());
  });

  it('posts to logout URL then redirects to /', async () => {
    const user = userEvent.setup();

    renderTopbar();
    await waitFor(() => expect(screen.getByText('jsmith')).toBeInTheDocument());

    // Replace location after user data loads; keep a valid href so the fetch interceptor
    // can resolve the relative /logout URL
    const locationMock = { href: 'http://localhost/' };
    Object.defineProperty(window, 'location', { value: locationMock, writable: true, configurable: true });

    await user.click(screen.getByText('jsmith'));
    await user.click(await screen.findByText('Log out'));

    await waitFor(() => expect(locationMock.href).toBe('/'));
  });

  it('hides the Admin link for non-admin users', async () => {
    renderTopbar();
    await waitFor(() => expect(screen.getByText('jsmith')).toBeInTheDocument());
    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument();
  });

  it('shows the Admin link in the topbar for admins', async () => {
    server.use(
      mswHttp.get('/api/v1/me', () =>
        HttpResponse.json({ username: 'root', displayName: 'Root Admin', roles: ['ADMIN'] }),
      ),
    );
    renderTopbar();
    const link = await screen.findByRole('link', { name: 'Admin' });
    expect(link).toHaveAttribute('href', '/admin/accounts');
  });
});
