import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
import { App } from './App';
import { api, ApiError } from './lib/api';

vi.mock('./lib/api', async (original) => {
  const actual = await original<typeof import('./lib/api')>();
  return { ...actual, api: { get: vi.fn(), post: vi.fn(), patch: vi.fn(), delete: vi.fn() } };
});

const account = {
  id: 'account-id',
  username: 'jingshun',
  email: 'jingshun@example.com',
  role: 'USER' as const,
  enabled: true,
  createdAt: '2026-09-29T00:00:00Z',
};

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.get).mockRejectedValue(new ApiError(401, 'Authentication required.'));
});

it('signs in and shows the protected greeting, then signs out', async () => {
  const user = userEvent.setup();
  vi.mocked(api.post).mockResolvedValue(account);
  render(
    <MemoryRouter initialEntries={['/login']}>
      <App />
    </MemoryRouter>,
  );
  await user.type(await screen.findByLabelText('Username'), 'jingshun');
  await user.type(screen.getByLabelText('Password'), 'A-strong-password-2026');
  vi.mocked(api.get).mockResolvedValue('Hello, jingshun');
  await user.click(screen.getByRole('button', { name: 'Sign in' }));
  expect(await screen.findByRole('heading', { name: 'Hello, jingshun' })).toBeInTheDocument();
  expect(screen.queryByRole('link', { name: 'Manage users' })).not.toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: 'Sign out' }));
  expect(await screen.findByRole('heading', { name: 'Welcome back.' })).toBeInTheDocument();
});

it('shows generic login failures and allows another attempt', async () => {
  const user = userEvent.setup();
  vi.mocked(api.post).mockRejectedValue(new ApiError(401, 'Invalid username or password.'));
  render(
    <MemoryRouter initialEntries={['/login']}>
      <App />
    </MemoryRouter>,
  );
  await user.type(await screen.findByLabelText('Username'), 'someone');
  await user.type(screen.getByLabelText('Password'), 'wrong');
  await user.click(screen.getByRole('button', { name: 'Sign in' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password.');
  expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled();
});

it('requests a reset and displays the generic confirmation', async () => {
  const user = userEvent.setup();
  vi.mocked(api.post).mockResolvedValue({
    message: 'If an account matches that email, a password reset link has been sent.',
  });
  render(
    <MemoryRouter initialEntries={['/forgot-password']}>
      <App />
    </MemoryRouter>,
  );
  await user.type(await screen.findByLabelText('Email address'), 'someone@example.com');
  await user.click(screen.getByRole('button', { name: 'Send reset link' }));
  expect(await screen.findByRole('status')).toHaveTextContent('If an account matches that email');
});

it('rejects mismatched registration passwords before creating an account', async () => {
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={['/register']}>
      <App />
    </MemoryRouter>,
  );
  await user.type(await screen.findByLabelText('Username'), 'jingshun');
  await user.type(screen.getByLabelText('Email address'), 'jingshun@example.com');
  await user.type(screen.getByLabelText('Password'), 'A-strong-password-2026');
  await user.type(screen.getByLabelText('Confirm password'), 'Different-password-2026');
  await user.click(screen.getByRole('button', { name: 'Create account' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Passwords do not match.');
  expect(api.post).not.toHaveBeenCalled();
});

it('completes a reset from its fragment token and returns to sign in', async () => {
  const user = userEvent.setup();
  vi.mocked(api.post).mockResolvedValue({
    message: 'Your password has been updated. Please sign in again.',
  });
  render(
    <MemoryRouter initialEntries={['/reset-password#token=single-use-secret']}>
      <App />
    </MemoryRouter>,
  );
  await user.type(await screen.findByLabelText('Password'), 'Replacement-password-2026');
  await user.type(screen.getByLabelText('Confirm password'), 'Replacement-password-2026');
  await user.click(screen.getByRole('button', { name: 'Update password' }));
  expect(api.post).toHaveBeenCalledWith('/auth/password-reset/confirm', {
    token: 'single-use-secret',
    password: 'Replacement-password-2026',
  });
  expect(await screen.findByRole('heading', { name: 'Welcome back.' })).toBeInTheDocument();
  expect(screen.getByRole('status')).toHaveTextContent('Your password has been updated.');
});

it('makes incomplete reset links actionable', async () => {
  render(
    <MemoryRouter initialEntries={['/reset-password']}>
      <App />
    </MemoryRouter>,
  );
  expect(await screen.findByRole('alert')).toHaveTextContent('This reset link is incomplete.');
  expect(screen.getByRole('button', { name: 'Update password' })).toBeDisabled();
  expect(screen.getByRole('link', { name: 'Request a new reset link' })).toHaveAttribute(
    'href',
    '/forgot-password',
  );
});

it('returns an already signed-in user to login after resetting their password', async () => {
  const user = userEvent.setup();
  vi.mocked(api.get).mockImplementation(async (path) =>
    path === '/auth/me' ? account : 'Hello, jingshun',
  );
  vi.mocked(api.post).mockResolvedValue({
    message: 'Your password has been updated. Please sign in again.',
  });
  render(
    <MemoryRouter initialEntries={['/reset-password#token=single-use-secret']}>
      <App />
    </MemoryRouter>,
  );
  await user.type(await screen.findByLabelText('Password'), 'Replacement-password-2026');
  await user.type(screen.getByLabelText('Confirm password'), 'Replacement-password-2026');
  await user.click(screen.getByRole('button', { name: 'Update password' }));
  expect(await screen.findByRole('heading', { name: 'Welcome back.' })).toBeInTheDocument();
});

it('shows admin controls for other accounts and updates status', async () => {
  const user = userEvent.setup();
  const administrator = {
    ...account,
    id: 'admin-id',
    username: 'operator',
    role: 'ADMIN' as const,
  };
  vi.mocked(api.get).mockImplementation(async (path) =>
    path === '/auth/me' ? administrator : [administrator, account],
  );
  vi.mocked(api.patch).mockResolvedValue({ ...account, enabled: false });
  render(
    <MemoryRouter initialEntries={['/admin']}>
      <App />
    </MemoryRouter>,
  );
  expect(await screen.findByRole('heading', { name: 'The people here.' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Disable operator' })).not.toBeInTheDocument();
  await user.click(await screen.findByRole('button', { name: 'Disable jingshun' }));
  expect(await screen.findByRole('button', { name: 'Enable jingshun' })).toBeInTheDocument();
  expect(screen.getByRole('status')).toHaveTextContent('Updated jingshun.');
});
