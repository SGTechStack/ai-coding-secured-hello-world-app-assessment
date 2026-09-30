import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { type FetchMock, stubFetchWithCsrf } from '../test/fetchMock';
import PasswordResetRequestPage from './PasswordResetRequestPage';

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/reset-password']}>
      <PasswordResetRequestPage />
    </MemoryRouter>,
  );
}

describe('PasswordResetRequestPage', () => {
  let api: FetchMock;

  beforeEach(() => {
    api = stubFetchWithCsrf();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows an inline error and makes no request when the email is empty', async () => {
    renderPage();

    fireEvent.submit(screen.getByRole('button', { name: /send reset link/i }).closest('form')!);

    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(api).not.toHaveBeenCalled();
  });

  it('shows the same generic success message whether or not the email is registered', async () => {
    api.mockResolvedValue(new Response(null, { status: 200 }));
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText('Email'), 'johndoe@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(
      await screen.findByText("If an account exists for that email, we've sent a link to reset your password."),
    ).toBeInTheDocument();
    expect(api).toHaveBeenCalledWith(
      '/api/password-reset/request',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ email: 'johndoe@example.com' }),
      }),
    );
  });

  it('shows a server-unavailable banner on a network failure and leaves the form usable', async () => {
    api.mockRejectedValue(new TypeError('Failed to fetch'));
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText('Email'), 'johndoe@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(
      await screen.findByText('Unable to connect to the server. Please try again later.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /send reset link/i })).not.toBeDisabled();
  });

  it('shows the rate-limit message on a 429', async () => {
    api.mockResolvedValue(new Response(JSON.stringify({ code: 'RATE_LIMITED' }), { status: 429 }));
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText('Email'), 'johndoe@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts. Please try again later.');
  });

  it('bootstraps the CSRF token before submitting', async () => {
    api.mockResolvedValue(new Response(null, { status: 200 }));
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText('Email'), 'johndoe@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    await screen.findByRole('status');
    const [, init] = api.mock.calls[0];
    expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('test-csrf-token');
  });
});
