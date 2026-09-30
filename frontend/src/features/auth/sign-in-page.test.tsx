import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RETURN_TO_KEY } from '@lib/auth';
import { SignInPage } from './sign-in-page';

const mockNavigate = vi.fn();
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mockNavigate,
}));

const fetchMock = vi.fn();

function jsonResponse({ status, body }: { status: number; body: unknown }) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <SignInPage />
    </QueryClientProvider>,
  );
}

async function fillAndSubmit({ username, password }: { username: string; password: string }) {
  const user = userEvent.setup();
  if (username) await user.type(screen.getByLabelText('Username'), username);
  if (password) await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Sign in' }));
}

describe('SignInPage', () => {
  beforeEach(() => {
    fetchMock.mockReset();
    mockNavigate.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    // jsdom sits at "/"; pretend that is the sign-in page so a 401 does not trigger a redirect.
    vi.stubEnv('VITE_LOGIN_URL', window.location.pathname);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  it('posts the credentials as JSON and goes to the app on success', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ status: 200, body: { username: 'ada' } }));
    renderPage();

    await fillAndSubmit({ username: 'ada', password: 'correct horse battery' });

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith({ to: '/' }));
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/login');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toEqual({ username: 'ada', password: 'correct horse battery' });
  });

  it('returns to the page the visitor was sent away from, once', async () => {
    sessionStorage.setItem(RETURN_TO_KEY, '/forbidden');
    fetchMock.mockResolvedValue(jsonResponse({ status: 200, body: { username: 'ada' } }));
    renderPage();

    await fillAndSubmit({ username: 'ada', password: 'correct horse battery' });

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith({ to: '/forbidden' }));
    expect(sessionStorage.getItem(RETURN_TO_KEY)).toBeNull();
  });

  it('shows one generic message when sign-in is refused', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ status: 401, body: { title: 'Sign-in failed', detail: 'Invalid username or password' } }),
    );
    renderPage();

    await fillAndSubmit({ username: 'ada', password: 'wrong password!!' });

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password');
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('asks for both fields without calling the API when they are empty', async () => {
    renderPage();

    await fillAndSubmit({ username: '', password: '' });

    expect(await screen.findByText('Enter your username')).toBeInTheDocument();
    expect(screen.getByText('Enter your password')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
