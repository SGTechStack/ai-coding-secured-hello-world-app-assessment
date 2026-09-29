import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../contexts/AuthContext';
import { ResetPasswordPage } from '../pages/ResetPasswordPage';
import { mockProblemDetail, mockResponse, setCsrfCookie } from './helpers';

function renderWithToken(token = 'valid-token-abc') {
  return render(
    <MemoryRouter initialEntries={[`/reset-password?token=${token}`]}>
      <AuthProvider>
        <ResetPasswordPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  setCsrfCookie();
  vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
    const path = typeof url === 'string' ? url : url.toString();
    if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
    if (path.includes('/api/auth/me')) return Promise.resolve(mockProblemDetail(401, 'Unauthorized'));
    return Promise.reject(new Error('unexpected: ' + path));
  });
});

describe('ResetPasswordPage', () => {
  it('shows error for missing token (no query param)', () => {
    render(
      <MemoryRouter initialEntries={['/reset-password']}>
        <AuthProvider>
          <ResetPasswordPage />
        </AuthProvider>
      </MemoryRouter>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent(/invalid reset link/i);
  });

  it('validates password length client-side', async () => {
    renderWithToken();
    await userEvent.type(screen.getByLabelText(/^new password/i), 'short');
    await userEvent.type(screen.getByLabelText(/confirm/i), 'short');
    await userEvent.click(screen.getByRole('button', { name: /reset/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/at least 12/i);
  });

  it('shows generic error for 400 (invalid/expired/used token)', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
      const path = typeof url === 'string' ? url : url.toString();
      if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
      if (path.includes('/api/auth/me')) return Promise.resolve(mockProblemDetail(401, 'Unauthorized'));
      if (path.includes('/password-reset/confirm')) return Promise.resolve(mockProblemDetail(400, 'Invalid or expired reset token'));
      return Promise.reject(new Error('unexpected: ' + path));
    });

    renderWithToken('expired-token');
    await userEvent.type(screen.getByLabelText(/^new password/i), 'new-password-1234');
    await userEvent.type(screen.getByLabelText(/confirm/i), 'new-password-1234');
    await userEvent.click(screen.getByRole('button', { name: /reset/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid|expired/i);
  });

  it('does not display the plaintext token in the UI', () => {
    renderWithToken('my-super-secret-token-xyz');
    // Token must not be visible in the DOM (e.g. in a debug label or span)
    expect(screen.queryByText(/my-super-secret-token-xyz/i)).not.toBeInTheDocument();
  });
});
