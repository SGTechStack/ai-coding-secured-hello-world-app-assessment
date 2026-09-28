import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import RedirectIfAuthenticated from './RedirectIfAuthenticated';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function renderGuarded() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route
          path="/login"
          element={
            <RedirectIfAuthenticated>
              <div>Login page stub</div>
            </RedirectIfAuthenticated>
          }
        />
        <Route path="/" element={<div>Landing page stub</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('RedirectIfAuthenticated', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders nothing while the session check is in flight', () => {
    vi.mocked(fetch).mockImplementation(() => new Promise(() => {}));
    renderGuarded();

    expect(screen.queryByText(/login page stub/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/landing page stub/i)).not.toBeInTheDocument();
  });

  it('renders the login page when there is no valid session', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(401, {}));
    renderGuarded();

    expect(await screen.findByText('Login page stub')).toBeInTheDocument();
  });

  it('redirects to / when the session is already valid', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, { username: 'johndoe', firstName: 'John' }));
    renderGuarded();

    expect(await screen.findByText('Landing page stub')).toBeInTheDocument();
  });
});
