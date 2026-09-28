import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import PasswordResetConfirmPage from './PasswordResetConfirmPage';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function renderPage(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/reset-password/confirm" element={<PasswordResetConfirmPage />} />
        <Route path="/login" element={<div>Login page stub</div>} />
        <Route path="/reset-password" element={<div>Reset request page stub</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('PasswordResetConfirmPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows an invalid-link message and no form when the token query param is missing', () => {
    renderPage('/reset-password/confirm');

    expect(screen.getByText('Invalid reset link')).toBeInTheDocument();
    expect(screen.queryByLabelText('New password')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /request a new link/i })).toBeInTheDocument();
  });

  it('shows inline errors for an empty or too-short password and makes no request', async () => {
    renderPage('/reset-password/confirm?token=abc123');

    fireEvent.submit(screen.getByRole('button', { name: /reset password/i }).closest('form')!);
    expect(await screen.findByText('Password is required')).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();

    const user = userEvent.setup();
    await user.type(screen.getByLabelText('New password'), 'short');
    await user.click(screen.getByRole('button', { name: /reset password/i }));

    expect(await screen.findByText('Password must be at least 12 characters long')).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('confirms the reset and redirects to /login on success', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, {}));
    const user = userEvent.setup();
    renderPage('/reset-password/confirm?token=abc123');

    await user.type(screen.getByLabelText('New password'), 'NewPassword123!');
    await user.click(screen.getByRole('button', { name: /reset password/i }));

    expect(await screen.findByText('Login page stub')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith(
      '/api/password-reset/confirm',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ token: 'abc123', newPassword: 'NewPassword123!' }),
      }),
    );
  });

  it('shows the server-provided message on an invalid/expired token (400)', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(400, { message: 'Invalid or expired reset token' }));
    const user = userEvent.setup();
    renderPage('/reset-password/confirm?token=abc123');

    await user.type(screen.getByLabelText('New password'), 'NewPassword123!');
    await user.click(screen.getByRole('button', { name: /reset password/i }));

    expect(await screen.findByText('Invalid or expired reset token')).toBeInTheDocument();
  });
});
