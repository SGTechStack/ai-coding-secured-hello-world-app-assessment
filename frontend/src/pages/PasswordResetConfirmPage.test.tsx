import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { type FetchMock, stubFetchWithCsrf } from '../test/fetchMock';
import PasswordResetConfirmPage from './PasswordResetConfirmPage';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function LoginStub() {
  const { state } = useLocation();
  return <div data-notice={(state as { notice?: string } | null)?.notice ?? ''}>Login page stub</div>;
}

function renderPage(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/reset-password/confirm" element={<PasswordResetConfirmPage />} />
        <Route path="/login" element={<LoginStub />} />
        <Route path="/reset-password" element={<div>Reset request page stub</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('PasswordResetConfirmPage', () => {
  let api: FetchMock;

  beforeEach(() => {
    api = stubFetchWithCsrf();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    window.history.replaceState(null, '', '/');
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
    expect(api).not.toHaveBeenCalled();

    const user = userEvent.setup();
    await user.type(screen.getByLabelText('New password'), 'short');
    await user.click(screen.getByRole('button', { name: /reset password/i }));

    expect(await screen.findByText('Password must be at least 12 characters long')).toBeInTheDocument();
    expect(api).not.toHaveBeenCalled();
  });

  it('confirms the reset and redirects to /login on success', async () => {
    api.mockResolvedValue(jsonResponse(200, {}));
    const user = userEvent.setup();
    renderPage('/reset-password/confirm?token=abc123');

    await user.type(screen.getByLabelText('New password'), 'NewPassword123!');
    await user.click(screen.getByRole('button', { name: /reset password/i }));

    expect(await screen.findByText('Login page stub')).toHaveAttribute('data-notice', 'passwordReset');
    expect(api).toHaveBeenCalledWith(
      '/api/password-reset/confirm',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ token: 'abc123', newPassword: 'NewPassword123!' }),
      }),
    );
  });

  it('shows a fixed message on a 400 INVALID_TOKEN, not the server text', async () => {
    api.mockResolvedValue(jsonResponse(400, { code: 'INVALID_TOKEN', message: 'Token 123 expired' }));
    const user = userEvent.setup();
    renderPage('/reset-password/confirm?token=abc123');

    await user.type(screen.getByLabelText('New password'), 'NewPassword123!');
    await user.click(screen.getByRole('button', { name: /reset password/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This password reset link is invalid or has expired. Please request a new one.',
    );
    expect(screen.queryByText('Token 123 expired')).not.toBeInTheDocument();
  });

  it('shows the server validation message on a 400 VALIDATION_FAILED', async () => {
    api.mockResolvedValue(jsonResponse(400, { code: 'VALIDATION_FAILED', message: 'Password is too short' }));
    const user = userEvent.setup();
    renderPage('/reset-password/confirm?token=abc123');

    await user.type(screen.getByLabelText('New password'), 'NewPassword123!');
    await user.click(screen.getByRole('button', { name: /reset password/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Password is too short');
  });

  it('shows the rate-limit message on a 429', async () => {
    api.mockResolvedValue(jsonResponse(429, {}));
    const user = userEvent.setup();
    renderPage('/reset-password/confirm?token=abc123');

    await user.type(screen.getByLabelText('New password'), 'NewPassword123!');
    await user.click(screen.getByRole('button', { name: /reset password/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts. Please try again later.');
  });

  it('strips the token from the browser URL on load but still submits it', async () => {
    window.history.replaceState(null, '', '/reset-password/confirm?token=abc123');
    api.mockResolvedValue(jsonResponse(200, {}));
    const user = userEvent.setup();
    renderPage('/reset-password/confirm?token=abc123');

    await waitFor(() => expect(window.location.search).toBe(''));
    expect(window.location.pathname).toBe('/reset-password/confirm');
    expect(window.location.href).not.toContain('abc123');

    await user.type(screen.getByLabelText('New password'), 'NewPassword123!');
    await user.click(screen.getByRole('button', { name: /reset password/i }));

    await screen.findByText('Login page stub');
    expect(api).toHaveBeenCalledWith(
      '/api/password-reset/confirm',
      expect.objectContaining({ body: JSON.stringify({ token: 'abc123', newPassword: 'NewPassword123!' }) }),
    );
  });

  it('rejects a password over 72 UTF-8 bytes client-side', async () => {
    renderPage('/reset-password/confirm?token=abc123');

    fireEvent.change(screen.getByLabelText('New password'), { target: { value: `${'a'.repeat(71)}é` } });
    fireEvent.submit(screen.getByRole('button', { name: /reset password/i }).closest('form')!);

    expect(await screen.findByText(/Password is too long/)).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });
});
