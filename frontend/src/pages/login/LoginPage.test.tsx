import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { axiosFailure } from '../../test-support';

const mutateAsync = vi.fn();
const navigate = vi.fn();
let search: { reset?: 'done' } = {};
vi.mock('../../features/auth/login/hooks/useLogin', () => ({ useLogin: () => ({ mutateAsync }) }));
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigate,
  useSearch: () => search,
  Link: ({ to, children, ...props }: { to: string; children: ReactNode }) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
}));

import { LoginPage } from './LoginPage';

beforeEach(() => {
  mutateAsync.mockReset().mockResolvedValue(undefined);
  navigate.mockReset().mockResolvedValue(undefined);
  search = {};
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

function renderReadyPage() {
  render(<LoginPage />);
  return screen.getByRole('button', { name: 'Log in' });
}

function enterValidCredentials() {
  fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'johndoe' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'Password123!' } });
}

describe('LoginPage', () => {
  it('renders the accessible responsive login shell', () => {
    render(<LoginPage />);
    expect(screen.getByRole('heading', { name: 'Log in' })).toBeVisible();
    expect(screen.getByLabelText('Username')).toHaveAttribute('autocomplete', 'username');
    expect(screen.getByLabelText('Password')).toHaveAttribute('autocomplete', 'current-password');
    expect(screen.getByRole('button', { name: 'Log in' })).toHaveClass('min-h-11');
  });

  it('links to registration and to password reset', () => {
    render(<LoginPage />);
    expect(screen.getByRole('link', { name: 'Create an account' })).toHaveAttribute('href', '/register');
    expect(screen.getByRole('link', { name: 'Forgot password?' })).toHaveAttribute('href', '/forgot-password');
  });

  it('confirms a completed Password reset, and only then', () => {
    render(<LoginPage />);
    expect(screen.queryByText('Password updated. Log in with your new password.')).not.toBeInTheDocument();
    cleanup();

    search = { reset: 'done' };
    render(<LoginPage />);
    expect(screen.getByRole('status')).toHaveTextContent('Password updated. Log in with your new password.');
  });

  it('drops the Password reset confirmation from the URL on the first login attempt, for good', async () => {
    search = { reset: 'done' };
    navigate.mockImplementation((to: { to: string; search?: typeof search }) => {
      if (to.to === '/login') search = to.search ?? {};
      return Promise.resolve();
    });
    mutateAsync.mockRejectedValueOnce(axiosFailure(401));
    const button = renderReadyPage();
    enterValidCredentials();
    fireEvent.click(button);

    expect(navigate).toHaveBeenCalledWith({ to: '/login', search: {}, replace: true });
    expect(await screen.findByText('Invalid username or password')).toBeVisible();
    expect(screen.queryByText('Password updated. Log in with your new password.')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('Password')).toBeEnabled(), { timeout: 600 });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'Password123!x' } });
    expect(screen.queryByText('Invalid username or password')).not.toBeInTheDocument();
    expect(screen.queryByText('Password updated. Log in with your new password.')).not.toBeInTheDocument();
  });

  it('does not show validation feedback before a submission', () => {
    renderReadyPage();
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'bad/name' } });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows all client validation errors and does not submit malformed credentials', async () => {
    const button = renderReadyPage();
    fireEvent.click(button);
    expect(await screen.findByText('Username is required')).toBeVisible();
    expect(screen.getByText('Password is required')).toBeVisible();
    expect(mutateAsync).not.toHaveBeenCalled();
  });

  it('rejects short, slash-containing, and backslash-containing usernames after submission', async () => {
    const button = renderReadyPage();
    const username = screen.getByLabelText('Username');
    fireEvent.change(username, { target: { value: 'john' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'valid password' } });
    fireEvent.click(button);
    expect(await screen.findByText('Username must be at least 5 characters')).toBeVisible();
    fireEvent.change(username, { target: { value: 'bad/name' } });
    fireEvent.click(button);
    expect(await screen.findByText('Username must not contain / or \\')).toBeVisible();
    fireEvent.change(username, { target: { value: 'bad\\name' } });
    fireEvent.click(button);
    expect(await screen.findByText('Username must not contain / or \\')).toBeVisible();
    expect(mutateAsync).not.toHaveBeenCalled();
  });

  it('clears only the edited field validation feedback immediately', async () => {
    const button = renderReadyPage();
    fireEvent.click(button);
    await screen.findByText('Username is required');
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'jo' } });
    expect(screen.queryByText('Username is required')).not.toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeVisible();
  });

  it('keeps every control disabled for 400ms and cycles the loading label before successful navigation', async () => {
    vi.useFakeTimers();
    let resolveLogin!: () => void;
    mutateAsync.mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          resolveLogin = resolve;
        }),
    );
    render(<LoginPage />);
    await act(async () => {
      await Promise.resolve();
    });
    enterValidCredentials();
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }));
    await act(async () => {
      await Promise.resolve();
    });

    expect(screen.getByLabelText('Username')).toBeDisabled();
    expect(screen.getByLabelText('Password')).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Logging in.' })).toBeDisabled();
    expect(navigate).not.toHaveBeenCalled();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(400);
    });
    expect(screen.getByRole('button', { name: 'Logging in..' })).toBeDisabled();
    await act(async () => {
      resolveLogin();
      await Promise.resolve();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(navigate).toHaveBeenCalledWith({ to: '/home' });
  });

  it.each([
    ['401 response', axiosFailure(401), 'Invalid username or password'],
    ['429 response', axiosFailure(429), 'Too many sign-in attempts. Please try again later.'],
    ['server error', axiosFailure(500), 'Unable to connect to the server. Please try again later.'],
    ['network error', axiosFailure(), 'Unable to connect to the server. Please try again later.'],
  ])('shows safe feedback and retains credentials for a %s', async (_outcome, failure, message) => {
    vi.useFakeTimers();
    mutateAsync.mockRejectedValueOnce(failure);
    render(<LoginPage />);
    await act(async () => {
      await Promise.resolve();
    });
    enterValidCredentials();
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }));

    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByText(message)).toBeVisible();
    expect(screen.getByLabelText('Username')).toBeDisabled();
    expect(screen.getByLabelText('Password')).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Logging in.' })).toBeDisabled();
    expect(screen.queryByText('Username is required')).not.toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(399);
    });
    expect(screen.getByLabelText('Username')).toBeDisabled();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(screen.getByLabelText('Username')).toBeEnabled();
    expect(screen.getByLabelText('Password')).toBeEnabled();
    expect(screen.getByLabelText('Username')).toHaveValue('johndoe');
    expect(screen.getByLabelText('Password')).toHaveValue('Password123!');

    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'Password123!x' } });
    expect(screen.queryByText(message)).not.toBeInTheDocument();
  });

  it('submits valid credentials and enters the protected route after authentication', async () => {
    const button = renderReadyPage();
    enterValidCredentials();
    fireEvent.click(button);
    await waitFor(() => expect(mutateAsync).toHaveBeenCalledWith({ username: 'johndoe', password: 'Password123!' }));
    await waitFor(() => expect(navigate).toHaveBeenCalledWith({ to: '/home' }), { timeout: 600 });
  });
});
