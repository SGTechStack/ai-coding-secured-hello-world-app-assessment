import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('greets the user with the literal text of GET /api/hello', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response('Hello, johndoe', { status: 200 }));
    renderLandingPage();

    expect(await screen.findByRole('heading', { name: 'Hello, johndoe' })).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/hello', expect.objectContaining({ credentials: 'include' }));
  });

  it('falls back to a client-built greeting if /api/hello itself fails', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'));
    renderLandingPage();

    expect(await screen.findByRole('heading', { name: 'Hello, johndoe' })).toBeInTheDocument();
  });

  it('logs out and returns to the login page when the logout control is used', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response('Hello, johndoe', { status: 200 }));
    const user = userEvent.setup();
    renderLandingPage();
    await screen.findByRole('heading', { name: 'Hello, johndoe' });

    await user.click(screen.getByRole('button', { name: /log out/i }));

    expect(fetch).toHaveBeenCalledWith(
      '/api/auth/logout',
      expect.objectContaining({ method: 'POST', credentials: 'include' }),
    );
    expect(await screen.findByText('Login page stub')).toBeInTheDocument();
  });

  it('still returns to the login page if the logout request itself fails', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'));
    const user = userEvent.setup();
    renderLandingPage();
    await screen.findByRole('heading', { name: 'Hello, johndoe' });

    await user.click(screen.getByRole('button', { name: /log out/i }));

    expect(await screen.findByText('Login page stub')).toBeInTheDocument();
  });
});
