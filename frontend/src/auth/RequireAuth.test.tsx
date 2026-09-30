import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import RequireAuth from './RequireAuth';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function renderGuarded() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route
          path="/"
          element={<RequireAuth>{(user) => <div>Protected content for {user.username}</div>}</RequireAuth>}
        />
        <Route path="/login" element={<div>Login page stub</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('RequireAuth', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders nothing while the session check is in flight', () => {
    vi.mocked(fetch).mockImplementation(() => new Promise(() => {}));
    renderGuarded();

    expect(screen.queryByText(/protected content/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/login page stub/i)).not.toBeInTheDocument();
  });

  it('renders the guarded content when the session is valid', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(200, { username: 'johndoe', email: 'johndoe@example.com', role: 'USER' }),
    );
    renderGuarded();

    expect(await screen.findByText('Protected content for johndoe')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/auth/me', expect.objectContaining({ credentials: 'include' }));
  });

  it('redirects to /login when there is no valid session', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(401, {}));
    renderGuarded();

    expect(await screen.findByText('Login page stub')).toBeInTheDocument();
  });

  it('redirects to /login when the session check itself fails', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'));
    renderGuarded();

    expect(await screen.findByText('Login page stub')).toBeInTheDocument();
  });
});
