import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mutate = vi.fn();
const logout: { mutate: typeof mutate; isPending: boolean; isError: boolean; error: unknown } = {
  mutate,
  isPending: false,
  isError: false,
  error: null,
};
vi.mock('../hooks/useLogout', () => ({ useLogout: () => logout }));

import { LogoutButton } from './LogoutButton';

const FAILURE = "Couldn't log out. Check your connection and try again.";

beforeEach(() => {
  mutate.mockReset();
  Object.assign(logout, { isPending: false, isError: false, error: null });
});
afterEach(cleanup);

describe('LogoutButton', () => {
  it('is an accessible, touch-sized button that starts Logout', () => {
    render(<LogoutButton />);
    const button = screen.getByRole('button', { name: 'Log out' });

    expect(button).toHaveClass('min-h-11');
    fireEvent.click(button);
    expect(mutate).toHaveBeenCalledOnce();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('is disabled and says so while Logout is pending', () => {
    logout.isPending = true;
    render(<LogoutButton />);

    expect(screen.getByRole('button', { name: 'Logging out…' })).toBeDisabled();
  });

  it('announces a fixed failure message, never the server detail, and lets the User retry', () => {
    Object.assign(logout, {
      isError: true,
      error: Object.assign(new Error('Request failed with status code 503'), {
        isAxiosError: true,
        response: { status: 503, data: { detail: 'Database connection refused on db-01' } },
      }),
    });
    render(<LogoutButton />);

    expect(screen.getByRole('alert')).toHaveTextContent(FAILURE);
    expect(screen.queryByText(/db-01|503/)).not.toBeInTheDocument();
    const retry = screen.getByRole('button', { name: 'Log out' });
    expect(retry).toBeEnabled();
    fireEvent.click(retry);
    expect(mutate).toHaveBeenCalledOnce();
  });
});
