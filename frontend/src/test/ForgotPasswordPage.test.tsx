import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../contexts/AuthContext';
import { ForgotPasswordPage } from '../pages/ForgotPasswordPage';
import { mockProblemDetail, mockResponse, setCsrfCookie } from './helpers';

function renderPage() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <ForgotPasswordPage />
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

describe('ForgotPasswordPage — enumeration resistance', () => {
  it('shows generic success for known email', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
      const path = typeof url === 'string' ? url : url.toString();
      if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
      if (path.includes('/api/auth/me')) return Promise.resolve(mockProblemDetail(401, 'Unauthorized'));
      if (path.includes('/password-reset/request')) return Promise.resolve(mockResponse({ message: 'If that email is registered, a reset link has been sent.' }));
      return Promise.reject(new Error('unexpected: ' + path));
    });

    renderPage();
    await userEvent.type(screen.getByLabelText(/email/i), 'alice@example.com');
    await userEvent.click(screen.getByRole('button', { name: /send/i }));

    expect(await screen.findByText(/check your email/i)).toBeInTheDocument();
    expect(screen.getByText(/if that email address is registered/i)).toBeInTheDocument();
  });

  it('shows same generic success even for unknown email (no account existence info)', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
      const path = typeof url === 'string' ? url : url.toString();
      if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
      if (path.includes('/api/auth/me')) return Promise.resolve(mockProblemDetail(401, 'Unauthorized'));
      // Backend always returns 200 regardless (enumeration resistance)
      if (path.includes('/password-reset/request')) return Promise.resolve(mockResponse({ message: 'If that email is registered, a reset link has been sent.' }));
      return Promise.reject(new Error('unexpected: ' + path));
    });

    renderPage();
    await userEvent.type(screen.getByLabelText(/email/i), 'nobody@example.com');
    await userEvent.click(screen.getByRole('button', { name: /send/i }));

    // Same success message — cannot tell if email was registered or not
    expect(await screen.findByText(/check your email/i)).toBeInTheDocument();
    expect(screen.queryByText(/not registered|not found|invalid/i)).not.toBeInTheDocument();
  });

  it('shows rate-limit error for 429', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
      const path = typeof url === 'string' ? url : url.toString();
      if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
      if (path.includes('/api/auth/me')) return Promise.resolve(mockProblemDetail(401, 'Unauthorized'));
      if (path.includes('/password-reset/request')) return Promise.resolve(mockProblemDetail(429, 'Too many requests'));
      return Promise.reject(new Error('unexpected: ' + path));
    });

    renderPage();
    await userEvent.type(screen.getByLabelText(/email/i), 'flood@example.com');
    await userEvent.click(screen.getByRole('button', { name: /send/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/too many/i);
  });
});
