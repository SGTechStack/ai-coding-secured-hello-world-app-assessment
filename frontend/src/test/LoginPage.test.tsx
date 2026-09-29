import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../contexts/AuthContext';
import { LoginPage } from '../pages/LoginPage';
import { mockProblemDetail, mockResponse, mockUser, setCsrfCookie } from './helpers';

function renderLoginPage() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  setCsrfCookie();
  // Default: csrf + me=401 (unauthenticated start)
  vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
    const path = typeof url === 'string' ? url : url.toString();
    if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
    if (path.includes('/api/auth/me')) return Promise.resolve(mockProblemDetail(401, 'Unauthorized'));
    return Promise.reject(new Error('unexpected: ' + path));
  });
});

describe('LoginPage', () => {
  it('renders username and password fields', () => {
    renderLoginPage();
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
  });

  it('shows error when username is empty', async () => {
    renderLoginPage();
    await userEvent.click(screen.getByRole('button', { name: /login/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/required/i);
  });

  it('shows generic error for 401 response — does not distinguish cause', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
      const path = typeof url === 'string' ? url : url.toString();
      if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
      if (path.includes('/api/auth/me')) return Promise.resolve(mockProblemDetail(401, 'Unauthorized'));
      if (path.includes('/api/auth/login')) return Promise.resolve(mockProblemDetail(401, 'Invalid credentials'));
      return Promise.reject(new Error('unexpected: ' + path));
    });

    renderLoginPage();
    await userEvent.type(screen.getByLabelText(/username/i), 'alice');
    await userEvent.type(screen.getByLabelText(/password/i), 'wrongpassword12');
    await userEvent.click(screen.getByRole('button', { name: /login/i }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/invalid credentials/i);
    // Must not expose details like "wrong password" or "unknown user"
    expect(alert).not.toHaveTextContent(/unknown|wrong password|locked/i);
  });

  it('shows rate-limit message for 429', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
      const path = typeof url === 'string' ? url : url.toString();
      if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
      if (path.includes('/api/auth/me')) return Promise.resolve(mockProblemDetail(401, 'Unauthorized'));
      if (path.includes('/api/auth/login')) return Promise.resolve(mockProblemDetail(429, 'Too many requests'));
      return Promise.reject(new Error('unexpected: ' + path));
    });

    renderLoginPage();
    await userEvent.type(screen.getByLabelText(/username/i), 'alice');
    await userEvent.type(screen.getByLabelText(/password/i), 'password-12345');
    await userEvent.click(screen.getByRole('button', { name: /login/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/too many/i);
  });

  it('submit button is disabled during loading', async () => {
    let resolveLogin: (v: Response) => void;
    const loginPromise = new Promise<Response>((res) => { resolveLogin = res; });
    vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
      const path = typeof url === 'string' ? url : url.toString();
      if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
      if (path.includes('/api/auth/me')) return Promise.resolve(mockProblemDetail(401, 'Unauthorized'));
      if (path.includes('/api/auth/login')) return loginPromise;
      if (path.includes('/api/auth/me')) return Promise.resolve(mockResponse(mockUser()));
      return Promise.reject(new Error('unexpected: ' + path));
    });

    renderLoginPage();
    await userEvent.type(screen.getByLabelText(/username/i), 'alice');
    await userEvent.type(screen.getByLabelText(/password/i), 'password-12345');

    const btn = screen.getByRole('button', { name: /login/i });
    await userEvent.click(btn);

    await waitFor(() => expect(btn).toBeDisabled());
    resolveLogin!(mockResponse({ username: 'alice', role: 'USER' }));
  });
});
