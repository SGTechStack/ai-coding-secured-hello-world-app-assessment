import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import PasswordResetRequestPage from './PasswordResetRequestPage';

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/reset-password']}>
      <PasswordResetRequestPage />
    </MemoryRouter>,
  );
}

describe('PasswordResetRequestPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows an inline error and makes no request when the email is empty', async () => {
    renderPage();

    fireEvent.submit(screen.getByRole('button', { name: /send reset link/i }).closest('form')!);

    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('shows the same generic success message whether or not the email is registered', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 200 }));
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText('Email'), 'johndoe@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(
      await screen.findByText("If an account exists for that email, we've sent a link to reset your password."),
    ).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith(
      '/api/password-reset/request',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ email: 'johndoe@example.com' }),
      }),
    );
  });

  it('shows a server-unavailable banner on a network failure and leaves the form usable', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'));
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText('Email'), 'johndoe@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(
      await screen.findByText('Unable to connect to the server. Please try again later.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /send reset link/i })).not.toBeDisabled();
  });
});
