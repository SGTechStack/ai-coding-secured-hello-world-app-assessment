import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CHANGE_PASSWORD_FAILED_MESSAGE } from './auth.queries';
import { ChangePasswordPage } from './change-password-page';

const mockNavigate = vi.fn();
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mockNavigate,
}));

const fetchMock = vi.fn();

const NEW_PASSWORD = 'a-much-longer-secret';

function jsonResponse({ status, body }: { status: number; body: unknown }) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <ChangePasswordPage />
    </QueryClientProvider>,
  );
}

async function fillAndSubmit({ newPassword, confirmPassword }: { newPassword: string; confirmPassword: string }) {
  const user = userEvent.setup();
  if (newPassword) await user.type(screen.getByLabelText('New password'), newPassword);
  if (confirmPassword) await user.type(screen.getByLabelText('Confirm new password'), confirmPassword);
  await user.click(screen.getByRole('button', { name: 'Change password' }));
}

describe('ChangePasswordPage', () => {
  beforeEach(() => {
    fetchMock.mockReset();
    mockNavigate.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('posts both entries and goes to the app on success', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    renderPage();

    await fillAndSubmit({ newPassword: NEW_PASSWORD, confirmPassword: NEW_PASSWORD });

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith({ to: '/' }));
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/me/password');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toEqual({ newPassword: NEW_PASSWORD, confirmPassword: NEW_PASSWORD });
  });

  it('flags a mismatch without calling the API', async () => {
    renderPage();

    await fillAndSubmit({ newPassword: NEW_PASSWORD, confirmPassword: `${NEW_PASSWORD}-typo` });

    expect(await screen.findByText('The two passwords do not match')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('flags a password under 12 characters without calling the API', async () => {
    renderPage();

    await fillAndSubmit({ newPassword: 'too-short', confirmPassword: 'too-short' });

    expect(await screen.findByText('Use at least 12 characters')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('shows the server reason when it refuses the change', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ status: 400, body: { title: 'Bad Request', detail: 'Password confirmation does not match' } }),
    );
    renderPage();

    await fillAndSubmit({ newPassword: NEW_PASSWORD, confirmPassword: NEW_PASSWORD });

    expect(await screen.findByRole('alert')).toHaveTextContent('Password confirmation does not match');
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('falls back to a generic message when the server gives no reason', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ status: 500, body: {} }));
    renderPage();

    await fillAndSubmit({ newPassword: NEW_PASSWORD, confirmPassword: NEW_PASSWORD });

    expect(await screen.findByRole('alert')).toHaveTextContent(CHANGE_PASSWORD_FAILED_MESSAGE);
  });
});
