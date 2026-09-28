import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';

/**
 * Top-level route-wiring tests: session-restore-on-load and both auth-state
 * route guards (issue 04). `fetch` is mocked at the boundary per Seam 2 to
 * stand in for `GET /api/auth/me` (and, once landed on `/`, `GET
 * /api/hello`). Login-flow behavior in depth lives in `pages/LoginPage.test.tsx`;
 * the greeting/logout behavior in depth lives in `pages/LandingPage.test.tsx`.
 */
describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    window.history.pushState({}, '', '/');
  });

  function jsonResponse(status: number, body: unknown): Response {
    return new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  // A route transition can mount a second guard (e.g. /login -> / bounces
  // through both RedirectIfAuthenticated and RequireAuth), each issuing its
  // own GET /me; landing on / additionally fires GET /hello for the
  // greeting. Build a fresh Response per call and dispatch on the request
  // URL rather than `mockResolvedValue`, whose single shared instance can
  // only have its body read once and can't distinguish the two endpoints.
  function mockMeAlways(status: number, body: unknown) {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.includes('/api/hello')) {
        return Promise.resolve(new Response('Hello, johndoe', { status: 200 }));
      }
      return Promise.resolve(jsonResponse(status, body));
    });
  }

  it('restores an authenticated session on load and greets the user at /', async () => {
    mockMeAlways(200, { username: 'johndoe', email: 'johndoe@example.com', role: 'USER' });

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Hello, johndoe' })).toBeInTheDocument();
  });

  it('redirects an anonymous visitor from / to /login', async () => {
    mockMeAlways(401, {});

    render(<App />);

    expect(await screen.findByRole('heading', { name: /welcome back/i })).toBeInTheDocument();
  });

  it('redirects an already-authenticated visitor from /login to /', async () => {
    mockMeAlways(200, { username: 'johndoe', email: 'johndoe@example.com', role: 'USER' });
    window.history.pushState({}, '', '/login');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Hello, johndoe' })).toBeInTheDocument();
  });

  it('lets an anonymous visitor see the login page at /login', async () => {
    mockMeAlways(401, {});
    window.history.pushState({}, '', '/login');

    render(<App />);

    expect(await screen.findByRole('heading', { name: /welcome back/i })).toBeInTheDocument();
  });

  it('redirects unknown routes back to / (and on to /login when anonymous)', async () => {
    mockMeAlways(401, {});
    window.history.pushState({}, '', '/some-unknown-route');

    render(<App />);

    expect(await screen.findByRole('heading', { name: /welcome back/i })).toBeInTheDocument();
  });
});
