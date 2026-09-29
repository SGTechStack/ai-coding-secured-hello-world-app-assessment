/**
 * Additional UX-behaviour tests for the improved validation system.
 * All existing tests in LoginPage/RegisterPage/etc. must continue passing.
 * These NEW tests cover blur-on-leave, live-correction, and password toggle UX.
 */
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../contexts/AuthContext';
import { LoginPage } from '../pages/LoginPage';
import { RegisterPage } from '../pages/RegisterPage';
import { mockProblemDetail, mockResponse, setCsrfCookie } from './helpers';

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

function renderRegister() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <RegisterPage />
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

// ── Blur validation ───────────────────────────────────────────────────────────

describe('Login — blur validation', () => {
  it('untouched fields do not show errors on initial render', () => {
    renderLogin();
    // No alert on a fresh page — no fields have been touched
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    // No inline errors either
    expect(screen.queryByText(/required/i)).not.toBeInTheDocument();
  });

  it('shows inline error when username field is blurred empty', async () => {
    renderLogin();
    const usernameInput = screen.getByLabelText(/username/i);
    await userEvent.click(usernameInput);
    await userEvent.tab(); // blur username → focus next field
    expect(await screen.findByText('Username is required.')).toBeInTheDocument();
  });

  it('clears inline error when user types a valid value after blur', async () => {
    renderLogin();
    const usernameInput = screen.getByLabelText(/username/i);
    await userEvent.click(usernameInput);
    await userEvent.tab(); // blur empty → shows error
    expect(await screen.findByText('Username is required.')).toBeInTheDocument();

    // User starts typing — error should clear immediately (touched already)
    await userEvent.type(usernameInput, 'alice');
    await waitFor(() => expect(screen.queryByText('Username is required.')).not.toBeInTheDocument());
  });
});

describe('Register — blur validation', () => {
  it('untouched fields do not show errors on initial render', () => {
    renderRegister();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByText(/required/i)).not.toBeInTheDocument();
  });

  it('shows email format error on blur with invalid email', async () => {
    renderRegister();
    const emailInput = screen.getByLabelText(/email/i);
    await userEvent.type(emailInput, 'not-an-email');
    await userEvent.tab();
    expect(await screen.findByText(/valid email/i)).toBeInTheDocument();
  });

  it('shows password-too-short error on blur', async () => {
    renderRegister();
    const pwInput = screen.getByLabelText(/^password/i);
    await userEvent.type(pwInput, 'short');
    await userEvent.tab();
    // The field-level error uses aria-live="polite" and a form-field-error class.
    // (Requirements list also mentions "12" but is a separate <li> element.)
    const fieldError = await screen.findByText(/at least 12/i, { selector: '.form-field-error' });
    expect(fieldError).toBeInTheDocument();
  });

  it('shows confirm-mismatch error on blur', async () => {
    renderRegister();
    await userEvent.type(screen.getByLabelText(/^password/i), 'valid-password-1');
    await userEvent.type(screen.getByLabelText(/confirm/i), 'different-pass-2');
    await userEvent.tab();
    expect(await screen.findByText(/do not match/i)).toBeInTheDocument();
  });
});

// ── Submit validation (all fields touched at once) ────────────────────────────

describe('Login — submit validation', () => {
  it('submit with all empty fields shows role=alert with first error', async () => {
    renderLogin();
    await userEvent.click(screen.getByRole('button', { name: /login/i }));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/required/i);
  });

  it('submit does not call the API when validation fails', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    renderLogin();
    await userEvent.click(screen.getByRole('button', { name: /login/i }));
    await screen.findByRole('alert');
    // Only csrf + me should have been called; login should NOT be called
    const loginCalls = fetchSpy.mock.calls.filter(
      ([u]) => typeof u === 'string' && u.includes('/api/auth/login'),
    );
    expect(loginCalls).toHaveLength(0);
  });
});

// ── Loading state ─────────────────────────────────────────────────────────────

describe('Login — loading state prevents double submission', () => {
  it('submit button text changes and is disabled while request is pending', async () => {
    let resolveLogin!: (r: Response) => void;
    const loginPromise = new Promise<Response>((res) => { resolveLogin = res; });

    vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
      const path = typeof url === 'string' ? url : url.toString();
      if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
      if (path.includes('/api/auth/me')) return Promise.resolve(mockProblemDetail(401, 'Unauthorized'));
      if (path.includes('/api/auth/login')) return loginPromise;
      return Promise.reject(new Error('unexpected: ' + path));
    });

    renderLogin();
    await userEvent.type(screen.getByLabelText(/username/i), 'alice');
    await userEvent.type(screen.getByLabelText(/password/i), 'pass-12345');
    const btn = screen.getByRole('button', { name: /login/i });
    await userEvent.click(btn);

    await waitFor(() => expect(btn).toBeDisabled());
    expect(btn).toHaveTextContent(/signing in/i);

    resolveLogin(mockResponse({ username: 'alice', role: 'USER' }));
  });
});

// ── Password visibility toggle ────────────────────────────────────────────────

describe('Password visibility toggle', () => {
  it('password field starts as type=password', () => {
    renderLogin();
    expect(screen.getByLabelText(/password/i)).toHaveAttribute('type', 'password');
  });

  it('clicking the toggle reveals the password', async () => {
    renderLogin();
    const toggleBtn = screen.getByRole('button', { name: /show/i });
    await userEvent.click(toggleBtn);
    expect(screen.getByLabelText(/password/i)).toHaveAttribute('type', 'text');
  });

  it('clicking the toggle again hides the password', async () => {
    renderLogin();
    const toggleBtn = screen.getByRole('button', { name: /show/i });
    await userEvent.click(toggleBtn);
    await userEvent.click(toggleBtn);
    expect(screen.getByLabelText(/password/i)).toHaveAttribute('type', 'password');
  });
});

// ── Security: sensitive values not persisted ──────────────────────────────────

describe('Security — no sensitive values in storage', () => {
  it('password is not in localStorage after a failed login', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
      const path = typeof url === 'string' ? url : url.toString();
      if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
      if (path.includes('/api/auth/me')) return Promise.resolve(mockProblemDetail(401, 'Unauthorized'));
      if (path.includes('/api/auth/login')) return Promise.resolve(mockProblemDetail(401, 'Invalid credentials'));
      return Promise.reject(new Error('unexpected: ' + path));
    });

    renderLogin();
    await userEvent.type(screen.getByLabelText(/username/i), 'alice');
    await userEvent.type(screen.getByLabelText(/password/i), 'super-secret-12');
    await userEvent.click(screen.getByRole('button', { name: /login/i }));
    await screen.findByRole('alert');

    const storage = { ...localStorage, ...sessionStorage };
    for (const v of Object.values(storage)) {
      expect(String(v)).not.toContain('super-secret-12');
    }
  });
});
