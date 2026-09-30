import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import type * as PasswordStrength from '../../features/account/password/model/password-strength';
import type * as RegistrationApi from '../../features/account/registration/api/registration-api';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { axiosFailure } from '../../test-support';

import type { MessageResponse } from '../../features/account/core';
import type { RegistrationRequest } from '../../features/account/registration/model/registration';

const registerAccount = vi.fn<(input: RegistrationRequest) => Promise<MessageResponse>>();
const navigate = vi.fn();
const estimatePasswordStrength = vi.fn<(password: string, inputs: string[]) => Promise<number>>();
vi.mock('../../features/account/registration/api/registration-api', async (importOriginal) => ({
  ...(await importOriginal<typeof RegistrationApi>()),
  registerAccount: (input: RegistrationRequest) => registerAccount(input),
}));
vi.mock('../../features/account/password/model/password-strength', async (importOriginal) => ({
  ...(await importOriginal<typeof PasswordStrength>()),
  estimatePasswordStrength: (password: string, inputs: string[]) => estimatePasswordStrength(password, inputs),
}));
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigate,
  Link: ({ to, children, ...props }: { to: string; children: ReactNode }) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
}));

import { RegisterPage } from './RegisterPage';

const VALID = { username: 'testuser123', email: 'testuser123@test.example.com', password: 'Str0ng!Passw0rd' };

beforeEach(() => {
  registerAccount.mockReset().mockResolvedValue({ message: 'Account created. You can now log in.' });
  navigate.mockReset().mockResolvedValue(undefined);
  estimatePasswordStrength.mockReset().mockResolvedValue(3);
  localStorage.clear();
  sessionStorage.clear();
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

function renderReadyPage() {
  render(<RegisterPage />);
  return screen.getByRole('button', { name: 'Create account' });
}

function fill(values: Partial<typeof VALID & { confirmPassword: string }>) {
  const labels = { username: 'Username', email: 'Email', password: 'Password', confirmPassword: 'Confirm password' };
  for (const [key, value] of Object.entries(values)) {
    fireEvent.change(screen.getByLabelText(labels[key as keyof typeof labels]), { target: { value } });
  }
}

const fillValid = () => fill({ ...VALID, confirmPassword: VALID.password });

/** Submits under fake timers and lets the 400 ms minimum pending state elapse. */
async function submitAndSettle() {
  fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
  await act(async () => {
    await vi.advanceTimersByTimeAsync(400);
  });
}

async function renderWithFakeTimers() {
  vi.useFakeTimers();
  render(<RegisterPage />);
  await act(async () => {
    await Promise.resolve();
  });
}

describe('RegisterPage', () => {
  it('renders labelled, touch-sized controls with registration autocomplete hints and a login link', () => {
    renderReadyPage();
    expect(screen.getByRole('heading', { name: 'Create account' })).toBeVisible();
    expect(screen.getByLabelText('Username')).toHaveAttribute('autocomplete', 'username');
    expect(screen.getByLabelText('Email')).toHaveAttribute('autocomplete', 'email');
    expect(screen.getByLabelText('Password')).toHaveAttribute('autocomplete', 'new-password');
    expect(screen.getByLabelText('Confirm password')).toHaveAttribute('autocomplete', 'new-password');
    expect(screen.getByRole('button', { name: 'Create account' })).toHaveClass('min-h-11');
    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login');
  });

  it('toggles password visibility for both password fields', () => {
    renderReadyPage();
    const toggle = screen.getByRole('button', { name: 'Show password' });
    fireEvent.click(toggle);
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'text');
    expect(screen.getByRole('button', { name: 'Hide password' })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(screen.getByRole('button', { name: 'Show password confirmation' }));
    expect(screen.getByLabelText('Confirm password')).toHaveAttribute('type', 'text');
  });

  it('defers validation until submit, then reports every field without sending a request', async () => {
    const button = renderReadyPage();
    fill({ username: 'ab' });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    fireEvent.click(button);
    expect(await screen.findByText('Username must be at least 5 characters')).toBeVisible();
    expect(screen.getByText('Email is required')).toBeVisible();
    expect(screen.getByText('Password is required')).toBeVisible();
    expect(screen.getByText('Confirm your password')).toBeVisible();
    expect(registerAccount).not.toHaveBeenCalled();
  });

  it.each([
    [{ password: 'short' }, 'Password must be at least 12 characters'],
    [{ password: 'lowercase12345!' }, 'Password must include an uppercase letter'],
    [{ password: 'UPPERCASE12345!' }, 'Password must include a lowercase letter'],
    [{ password: 'NoDigitsHere!!' }, 'Password must include a digit'],
    [{ password: 'NoSpecials1234' }, 'Password must include a special character'],
    [{ password: 'P\u00e4ssword1234!' }, 'Password may use only printable ASCII characters'],
    [{ password: `${'Aa1!'.repeat(18)}x` }, 'Password must be at most 72 characters'],
    [{ password: 'Xx!TESTUSER123' }, 'Password must not contain your username or the name part of your email'],
    [{ email: 'not-an-email' }, 'Enter a valid email address'],
    [
      { username: 'test user' },
      'Username may use only letters, digits, ".", "_" and "-", and must start with a letter or digit',
    ],
  ])('blocks %j locally with its rule message', async (override, message) => {
    const button = renderReadyPage();
    const values = { ...VALID, ...override };
    fill({ ...values, confirmPassword: values.password });
    fireEvent.click(button);
    expect(await screen.findByText(message, { exact: false })).toBeVisible();
    expect(registerAccount).not.toHaveBeenCalled();
  });

  it('checks the confirmation locally and never sends it', async () => {
    const button = renderReadyPage();
    fill({ ...VALID, confirmPassword: 'Str0ng!Passw0rX' });
    fireEvent.click(button);
    expect(await screen.findByText('Passwords do not match')).toBeVisible();
    expect(registerAccount).not.toHaveBeenCalled();

    fill({ confirmPassword: VALID.password });
    fireEvent.click(button);
    await waitFor(() => expect(registerAccount).toHaveBeenCalledWith(VALID));
    expect(registerAccount.mock.calls[0]?.[0]).not.toHaveProperty('confirmPassword');
  });

  it('updates the checklist and strength bar live, with text as well as colour', async () => {
    estimatePasswordStrength.mockResolvedValue(1);
    renderReadyPage();
    act(() => screen.getByLabelText('Password').focus());
    const guidance = screen.getByText(/Password must have/).closest('[aria-live="polite"]') as HTMLElement;
    expect(within(guidance).getByText('Strength: Not rated yet')).toBeVisible();

    fill({ username: VALID.username, email: VALID.email, password: 'abc' });
    expect(within(guidance).getByText('Password must have (3 of 7 met):')).toBeVisible();
    expect(within(guidance).getByText('A lowercase letter').textContent).toContain('Met: ');
    expect(within(guidance).getByText('A digit').textContent).toContain('Not met: ');
    expect(await within(guidance).findByText('Strength: Weak')).toBeVisible();
    expect(estimatePasswordStrength).toHaveBeenLastCalledWith('abc', [VALID.username, VALID.email]);

    fill({ password: VALID.password });
    expect(within(guidance).getByText('Password must have (7 of 7 met):')).toBeVisible();
  });

  it('shows a short hint until the password field is focused, filled, or flagged on submit', async () => {
    const button = renderReadyPage();
    const password = screen.getByLabelText('Password');
    const hint = /^12–72 characters, with upper and lower case letters/;
    expect(screen.getByText(hint)).toBeVisible();
    expect(password).toHaveAccessibleDescription(hint);
    expect(screen.queryByText(/Password must have/)).not.toBeInTheDocument();

    act(() => password.focus());
    expect(screen.getByText(/Password must have/)).toBeVisible();
    expect(screen.queryByText(hint)).not.toBeInTheDocument();
    act(() => screen.getByRole('button', { name: 'Show password' }).focus());
    expect(screen.getByText(/Password must have/)).toBeVisible();

    act(() => screen.getByLabelText('Username').focus());
    expect(screen.queryByText(/Password must have/)).not.toBeInTheDocument();

    fill({ password: 'abc' });
    expect(screen.getByText(/Password must have/)).toBeVisible();
    fill({ password: '' });
    expect(screen.queryByText(/Password must have/)).not.toBeInTheDocument();

    fireEvent.click(button);
    expect(await screen.findByText('Password is required')).toBeVisible();
    expect(screen.getByText(/Password must have/)).toBeVisible();
  });

  it('lets a password rated "Very weak" submit when it meets every hard rule', async () => {
    estimatePasswordStrength.mockResolvedValue(0);
    const button = renderReadyPage();
    fillValid();
    expect(await screen.findByText('Strength: Very weak')).toBeVisible();
    fireEvent.click(button);
    await waitFor(() => expect(registerAccount).toHaveBeenCalledWith(VALID));
  });

  it('keeps every control disabled for at least 400ms, then acknowledges success and routes to login', async () => {
    let resolveRegistration!: () => void;
    registerAccount.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveRegistration = () => resolve({ message: 'Account created. You can now log in.' });
        }),
    );
    await renderWithFakeTimers();
    fillValid();
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
    await act(async () => {
      await Promise.resolve();
    });

    for (const label of ['Username', 'Email', 'Password', 'Confirm password'])
      expect(screen.getByLabelText(label)).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Creating account.' })).toBeDisabled();
    await act(async () => {
      resolveRegistration();
      await vi.advanceTimersByTimeAsync(399);
    });
    expect(screen.queryByRole('status')).not.toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(screen.getByRole('status')).toHaveTextContent('Account created. You can now log in.');
    expect(navigate).not.toHaveBeenCalled();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1500);
    });
    expect(navigate).toHaveBeenCalledWith({ to: '/login' });
  });

  it('shows each server field code on its field and retains the drafts in the form', async () => {
    registerAccount.mockRejectedValueOnce(
      axiosFailure(400, {
        errors: [
          { field: 'username', code: 'USERNAME_INVALID_CHARACTER' },
          { field: 'password', code: 'PASSWORD_TOO_COMMON' },
          { field: 'password', code: 'PASSWORD_MISSING_DIGIT' },
        ],
      }),
    );
    await renderWithFakeTimers();
    fillValid();
    await submitAndSettle();

    const passwordError = screen.getByText('This password is too common. Choose a different one', { exact: false });
    expect(passwordError).toHaveTextContent('Password must include a digit');
    expect(screen.getByText(/^Username may use only letters/)).toBeVisible();
    expect(screen.queryByText('Unable to create account with these details.')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toHaveValue(VALID.password);
    expect(screen.getByLabelText('Confirm password')).toHaveValue(VALID.password);
  });

  it.each([
    [
      'duplicate account',
      axiosFailure(400, { errors: [{ code: 'USER_EXISTS' }] }),
      'Username or email is already in use.',
    ],
    [
      'unknown code',
      axiosFailure(400, { errors: [{ field: 'username', code: 'SOMETHING_NEW' }] }),
      'Unable to create account with these details.',
    ],
    ['malformed 400 body', axiosFailure(400, '<html>'), 'Unable to create account with these details.'],
    [
      'lockout',
      axiosFailure(429, { detail: 'ignored' }),
      'Registration is temporarily unavailable. Please try again later.',
    ],
    ['server error', axiosFailure(500), 'Unable to connect to the server. Please try again later.'],
    ['network error', axiosFailure(), 'Unable to connect to the server. Please try again later.'],
  ])(
    'shows fixed banner feedback for a %s, keeps drafts, and clears the banner on edit',
    async (_name, failure, message) => {
      registerAccount.mockRejectedValueOnce(failure);
      await renderWithFakeTimers();
      fillValid();
      await submitAndSettle();

      expect(screen.getByRole('alert')).toHaveTextContent(message);
      expect(screen.getByLabelText('Username')).toBeEnabled();
      expect(screen.getByLabelText('Username')).toHaveValue(VALID.username);
      expect(screen.getByLabelText('Password')).toHaveValue(VALID.password);
      expect(navigate).not.toHaveBeenCalled();

      fill({ username: 'testuser124' });
      expect(screen.queryByText(message)).not.toBeInTheDocument();
    },
  );

  it('never writes drafts or credentials to browser storage', async () => {
    registerAccount.mockRejectedValueOnce(axiosFailure(500));
    await renderWithFakeTimers();
    fillValid();
    await submitAndSettle();

    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
