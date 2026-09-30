import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider, createRouter, createMemoryHistory, createRootRoute } from '@tanstack/react-router';
import { RETURN_TO_KEY } from '@lib/auth';
import { SessionExpiredDialog } from './session-expired-dialog';

function renderDialog({ open }: { open: boolean }) {
  const rootRoute = createRootRoute({ component: () => <SessionExpiredDialog open={open} /> });
  const router = createRouter({
    routeTree: rootRoute,
    history: createMemoryHistory({ initialEntries: ['/lists/123?foo=bar'] }),
  });
  render(<RouterProvider router={router} />);
}

describe('SessionExpiredDialog', () => {
  let locationMock: { href: string };

  beforeEach(() => {
    sessionStorage.clear();
    locationMock = { href: 'http://localhost/' };
    Object.defineProperty(window, 'location', { value: locationMock, writable: true, configurable: true });
  });

  afterEach(() => {
    sessionStorage.clear();
    vi.unstubAllEnvs();
  });

  it('renders the dialog when open', async () => {
    renderDialog({ open: true });
    await waitFor(() => expect(screen.getByText('Session expired')).toBeInTheDocument());
  });

  it('does not render dialog content when closed', async () => {
    renderDialog({ open: false });
    // Allow render to settle, then assert the dialog title is absent
    await new Promise((r) => setTimeout(r, 50));
    expect(screen.queryByText('Session expired')).not.toBeInTheDocument();
  });

  it('stores the current path in sessionStorage on sign in', async () => {
    const user = userEvent.setup();
    renderDialog({ open: true });
    await waitFor(() => screen.getByRole('button', { name: 'Sign in' }));
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(sessionStorage.getItem(RETURN_TO_KEY)).toBe('/lists/123?foo=bar');
  });

  it('redirects to VITE_LOGIN_URL on sign in', async () => {
    vi.stubEnv('VITE_LOGIN_URL', '/oauth2/authorization/aas');
    const user = userEvent.setup();
    renderDialog({ open: true });
    await waitFor(() => screen.getByRole('button', { name: 'Sign in' }));
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(locationMock.href).toBe('/oauth2/authorization/aas');
  });
});
