import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { type FetchMock, stubFetchWithCsrf } from '../test/fetchMock';
import LandingPage from './LandingPage';

function renderLandingPage() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route
          path="/"
          element={<LandingPage user={{ username: 'johndoe', email: 'johndoe@example.com', role: 'USER' }} />}
        />
        <Route path="/login" element={<div>Login page stub</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('LandingPage', () => {
  let api: FetchMock;

  beforeEach(() => {
    api = stubFetchWithCsrf();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('greets the user with the literal text of GET /api/hello', async () => {
    api.mockResolvedValue(new Response('Hello, johndoe', { status: 200 }));
    renderLandingPage();

    expect(await screen.findByRole('heading', { name: 'Hello, johndoe' })).toBeInTheDocument();
    expect(api).toHaveBeenCalledWith('/api/hello', expect.objectContaining({ credentials: 'include' }));
  });

  it('falls back to a client-built greeting if /api/hello itself fails', async () => {
    api.mockRejectedValue(new TypeError('Failed to fetch'));
    renderLandingPage();

    expect(await screen.findByRole('heading', { name: 'Hello, johndoe' })).toBeInTheDocument();
  });

  it('logs out and returns to the login page when the logout control is used', async () => {
    api.mockResolvedValue(new Response('Hello, johndoe', { status: 200 }));
    const user = userEvent.setup();
    renderLandingPage();
    await screen.findByRole('heading', { name: 'Hello, johndoe' });

    await user.click(screen.getByRole('button', { name: /log out/i }));

    expect(api).toHaveBeenCalledWith(
      '/api/auth/logout',
      expect.objectContaining({ method: 'POST', credentials: 'include' }),
    );
    expect(await screen.findByText('Login page stub')).toBeInTheDocument();
  });

  it('shows an error and stays on the page when the logout request fails (network)', async () => {
    api.mockImplementation((input) =>
      String(input).endsWith('/api/hello')
        ? Promise.resolve(new Response('Hello, johndoe', { status: 200 }))
        : Promise.reject(new TypeError('Failed to fetch')),
    );
    const user = userEvent.setup();
    renderLandingPage();
    await screen.findByRole('heading', { name: 'Hello, johndoe' });

    await user.click(screen.getByRole('button', { name: /log out/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to log out. Please try again.');
    expect(screen.queryByText('Login page stub')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /log out/i })).not.toBeDisabled();
  });

  it('shows an error and stays on the page when the logout request returns a 5xx', async () => {
    api.mockImplementation((input) =>
      Promise.resolve(
        String(input).endsWith('/api/hello')
          ? new Response('Hello, johndoe', { status: 200 })
          : new Response(null, { status: 500 }),
      ),
    );
    const user = userEvent.setup();
    renderLandingPage();
    await screen.findByRole('heading', { name: 'Hello, johndoe' });

    await user.click(screen.getByRole('button', { name: /log out/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to log out. Please try again.');
    expect(screen.queryByText('Login page stub')).not.toBeInTheDocument();
  });

  it('sends the CSRF token header on logout', async () => {
    api.mockImplementation(() => Promise.resolve(new Response('Hello, johndoe', { status: 200 })));
    const user = userEvent.setup();
    renderLandingPage();
    await screen.findByRole('heading', { name: 'Hello, johndoe' });

    await user.click(screen.getByRole('button', { name: /log out/i }));

    await screen.findByText('Login page stub');
    const [, init] = api.mock.calls.find(([url]) => url === '/api/auth/logout')!;
    expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('test-csrf-token');
  });
});
