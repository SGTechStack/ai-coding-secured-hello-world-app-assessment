import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import type * as PasswordStrength from '../../features/account/password/model/password-strength';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { axiosFailure } from '../../test-support';
import type * as PasswordResetApi from '../../features/account/password-reset/api/password-reset-api';

import type { PasswordResetConfirmRequest } from '../../features/account/password-reset/model/password-reset';

const confirmPasswordReset = vi.fn<(input: PasswordResetConfirmRequest) => Promise<void>>();
const navigate = vi.fn();
vi.mock('../../features/account/password-reset/api/password-reset-api', async (importOriginal) => ({
  ...(await importOriginal<typeof PasswordResetApi>()),
  confirmPasswordReset: (input: PasswordResetConfirmRequest) => confirmPasswordReset(input),
}));
vi.mock('../../features/account/password/model/password-strength', async (importOriginal) => ({
  ...(await importOriginal<typeof PasswordStrength>()),
  estimatePasswordStrength: () => Promise.resolve(3),
}));
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigate,
  Link: ({ to, children, ...props }: { to: string; children: ReactNode }) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
}));

import { ResetPasswordPage } from './ResetPasswordPage';

const TOKEN = 'A'.repeat(40) + '_-9';
const PASSWORD = 'N3w!Different#Pw';
const INVALID = 'This reset link is invalid or has expired.';

function openLink(hash: string) {
  window.history.replaceState(null, '', `/reset-password${hash}`);
  render(<ResetPasswordPage />);
}

function fill(password: string, confirm = password) {
  fireEvent.change(screen.getByLabelText('New password'), { target: { value: password } });
  fireEvent.change(screen.getByLabelText('Confirm new password'), { target: { value: confirm } });
}

async function submit() {
  fireEvent.click(screen.getByRole('button', { name: 'Reset password' }));
  await act(async () => {
    await vi.advanceTimersByTimeAsync(400);
  });
}

beforeEach(() => {
  vi.useFakeTimers();
  confirmPasswordReset.mockReset().mockResolvedValue(undefined);
  navigate.mockReset().mockResolvedValue(undefined);
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
  window.history.replaceState(null, '', '/');
});

describe('ResetPasswordPage', () => {
  it('reads the token from the fragment, then removes it from the address bar', () => {
    openLink(`#token=${TOKEN}`);

    expect(window.location.hash).toBe('');
    expect(window.location.pathname).toBe('/reset-password');
    expect(screen.getByLabelText('New password')).toHaveAttribute('autocomplete', 'new-password');
    expect(screen.getByRole('button', { name: 'Reset password' })).toHaveClass('min-h-11');
  });

  it('shows the invalid-link state without calling the server when there is no token', () => {
    openLink('');

    expect(screen.getByRole('alert')).toHaveTextContent(INVALID);
    expect(screen.getByRole('link', { name: 'Request a new reset link' })).toHaveAttribute('href', '/forgot-password');
    expect(screen.queryByLabelText('New password')).not.toBeInTheDocument();
    expect(confirmPasswordReset).not.toHaveBeenCalled();
  });

  it('submits the token with the new password, never the confirmation, then goes to login', async () => {
    openLink(`#token=${TOKEN}`);
    fill(PASSWORD);
    await submit();

    expect(confirmPasswordReset).toHaveBeenCalledWith({ token: TOKEN, newPassword: PASSWORD });
    expect(navigate).toHaveBeenCalledWith({ to: '/login', search: { reset: 'done' } });
  });

  it('checks the password rules and the confirmation locally', async () => {
    openLink(`#token=${TOKEN}`);
    fill('short', 'other');
    await submit();

    expect(screen.getByText('Password must be at least 12 characters', { exact: false })).toBeVisible();
    expect(screen.getByText('Passwords do not match')).toBeVisible();
    expect(confirmPasswordReset).not.toHaveBeenCalled();
  });

  it('shows the live checklist without the identity rule, which only the server can check', () => {
    openLink(`#token=${TOKEN}`);
    fill('abc');

    expect(screen.getByText('Password must have (2 of 6 met):')).toBeVisible();
    expect(screen.queryByText('Does not contain your username or email name')).not.toBeInTheDocument();
  });

  it('shows a server password violation on the field and keeps the form usable', async () => {
    confirmPasswordReset.mockRejectedValueOnce(
      axiosFailure(400, { errors: [{ field: 'newPassword', code: 'PASSWORD_TOO_COMMON' }] }),
    );
    openLink(`#token=${TOKEN}`);
    fill(PASSWORD);
    await submit();

    expect(screen.getByText('This password is too common. Choose a different one')).toBeVisible();
    expect(screen.getByLabelText('New password')).toBeEnabled();
    fill('An0ther!Password#');
    await submit();
    expect(confirmPasswordReset).toHaveBeenLastCalledWith({ token: TOKEN, newPassword: 'An0ther!Password#' });
  });

  it('shows a Password history rejection on the field, and the same link can be used again', async () => {
    confirmPasswordReset.mockRejectedValueOnce(
      axiosFailure(400, { errors: [{ field: 'newPassword', code: 'PASSWORD_REUSED' }] }),
    );
    openLink(`#token=${TOKEN}`);
    fill(PASSWORD);
    await submit();

    expect(screen.getByText('You used this password recently. Choose a different one')).toBeVisible();
    expect(screen.queryByRole('link', { name: 'Request a new reset link' })).not.toBeInTheDocument();
    fill('An0ther!Password#');
    await submit();
    expect(navigate).toHaveBeenCalledWith({ to: '/login', search: { reset: 'done' } });
  });

  it('switches to the invalid-link state when the server rejects the link, never showing server text', async () => {
    confirmPasswordReset.mockRejectedValueOnce(
      axiosFailure(400, {
        code: 'PASSWORD_RESET_TOKEN_INVALID',
        detail: 'server detail is never shown',
      }),
    );
    openLink(`#token=${TOKEN}`);
    fill(PASSWORD);
    await submit();

    expect(screen.getByRole('alert')).toHaveTextContent(INVALID);
    expect(screen.queryByText('server detail is never shown')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Request a new reset link' })).toBeVisible();
    expect(navigate).not.toHaveBeenCalled();
  });

  it.each([
    ['rate limit', axiosFailure(429), 'Too many requests. Please try again later.'],
    ['server error', axiosFailure(500), 'Unable to connect to the server. Please try again later.'],
    ['network error', axiosFailure(), 'Unable to connect to the server. Please try again later.'],
  ])('shows fixed feedback for a %s and keeps the drafts', async (_name, failure, message) => {
    confirmPasswordReset.mockRejectedValueOnce(failure);
    openLink(`#token=${TOKEN}`);
    fill(PASSWORD);
    await submit();

    expect(screen.getByRole('alert')).toHaveTextContent(message);
    expect(screen.getByLabelText('New password')).toHaveValue(PASSWORD);
  });
});
