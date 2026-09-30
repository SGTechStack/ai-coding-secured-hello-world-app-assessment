import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { axiosFailure } from '../../test-support';
import type * as PasswordResetApi from '../../features/account/password-reset/api/password-reset-api';

import type { PasswordResetRequest } from '../../features/account/password-reset/model/password-reset';
import type { MessageResponse } from '../../features/account/core';

const requestPasswordReset = vi.fn<(input: PasswordResetRequest) => Promise<MessageResponse>>();
vi.mock('../../features/account/password-reset/api/password-reset-api', async (importOriginal) => ({
  ...(await importOriginal<typeof PasswordResetApi>()),
  requestPasswordReset: (input: PasswordResetRequest) => requestPasswordReset(input),
}));
vi.mock('@tanstack/react-router', () => ({
  Link: ({ to, children, ...props }: { to: string; children: ReactNode }) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
}));

import { ForgotPasswordPage } from './ForgotPasswordPage';

const REQUESTED =
  'If an account is registered with that email, we have sent a link to reset its password. ' +
  'The link expires in 30 minutes.';

async function submit(email: string) {
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } });
  fireEvent.click(screen.getByRole('button', { name: 'Send reset link' }));
  await act(async () => {
    await vi.advanceTimersByTimeAsync(400);
  });
}

beforeEach(() => {
  vi.useFakeTimers();
  requestPasswordReset.mockReset().mockResolvedValue({ message: 'server copy is never shown' });
  render(<ForgotPasswordPage />);
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe('ForgotPasswordPage', () => {
  it('renders a labelled, touch-sized email form with a way back to login', () => {
    expect(screen.getByRole('heading', { name: 'Reset your password' })).toBeVisible();
    expect(screen.getByLabelText('Email')).toHaveAttribute('autocomplete', 'email');
    expect(screen.getByRole('button', { name: 'Send reset link' })).toHaveClass('min-h-11');
    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login');
  });

  it('shows the same fixed confirmation for every accepted request', async () => {
    await submit('  someone@test.example.com ');

    // An email input already strips surrounding spaces; the server normalizes too.
    expect(requestPasswordReset).toHaveBeenCalledWith({ email: 'someone@test.example.com' });
    expect(screen.getByRole('status')).toHaveTextContent(REQUESTED);
    expect(screen.queryByText('server copy is never shown')).not.toBeInTheDocument();
  });

  it('validates the email locally before any request', async () => {
    await submit('not-an-email');
    expect(screen.getByText('Enter a valid email address')).toBeVisible();

    await submit('');
    expect(screen.getByText('Email is required')).toBeVisible();
    expect(requestPasswordReset).not.toHaveBeenCalled();
  });

  it('shows a server email violation on the field', async () => {
    requestPasswordReset.mockRejectedValueOnce(
      axiosFailure(400, { errors: [{ field: 'email', code: 'EMAIL_TOO_LONG' }] }),
    );

    await submit('someone@test.example.com');

    expect(screen.getByText('Email must be at most 254 characters')).toBeVisible();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it.each([
    ['rate limit', axiosFailure(429, { detail: 'ignored' }), 'Too many requests. Please try again later.'],
    [
      'unknown code',
      axiosFailure(400, { errors: [{ field: 'email', code: 'SOMETHING_NEW' }] }),
      'Unable to request a reset with these details.',
    ],
    ['server error', axiosFailure(500), 'Unable to connect to the server. Please try again later.'],
    ['network error', axiosFailure(), 'Unable to connect to the server. Please try again later.'],
  ])('shows fixed feedback for a %s, and clears it on edit', async (_name, failure, message) => {
    requestPasswordReset.mockRejectedValueOnce(failure);

    await submit('someone@test.example.com');
    expect(screen.getByRole('alert')).toHaveTextContent(message);
    expect(screen.getByLabelText('Email')).toBeEnabled();

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'someone@test.example.co' } });
    expect(screen.queryByText(message)).not.toBeInTheDocument();
  });
});
