import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../contexts/AuthContext';
import { RegisterPage } from '../pages/RegisterPage';
import { mockProblemDetail, mockResponse, setCsrfCookie } from './helpers';

function renderRegisterPage() {
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

describe('RegisterPage — password policy', () => {
  it('rejects passwords shorter than 12 characters', async () => {
    renderRegisterPage();
    await userEvent.type(screen.getByLabelText(/username/i), 'alice');
    await userEvent.type(screen.getByLabelText(/email/i), 'alice@test.com');
    await userEvent.type(screen.getByLabelText(/^password/i), 'short');
    await userEvent.type(screen.getByLabelText(/confirm/i), 'short');
    await userEvent.click(screen.getByRole('button', { name: /register/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/at least 12/i);
  });

  it('rejects passwords longer than 72 characters', async () => {
    const longPassword = 'a'.repeat(73);
    renderRegisterPage();
    await userEvent.type(screen.getByLabelText(/username/i), 'alice');
    await userEvent.type(screen.getByLabelText(/email/i), 'alice@test.com');
    await userEvent.type(screen.getByLabelText(/^password/i), longPassword);
    await userEvent.type(screen.getByLabelText(/confirm/i), longPassword);
    await userEvent.click(screen.getByRole('button', { name: /register/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/not exceed 72/i);
  });

  it('rejects mismatched passwords', async () => {
    renderRegisterPage();
    await userEvent.type(screen.getByLabelText(/username/i), 'alice');
    await userEvent.type(screen.getByLabelText(/email/i), 'alice@test.com');
    await userEvent.type(screen.getByLabelText(/^password/i), 'password-12345');
    await userEvent.type(screen.getByLabelText(/confirm/i), 'password-different');
    await userEvent.click(screen.getByRole('button', { name: /register/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/do not match/i);
  });

  it('shows conflict error from backend (409)', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
      const path = typeof url === 'string' ? url : url.toString();
      if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
      if (path.includes('/api/auth/me')) return Promise.resolve(mockProblemDetail(401, 'Unauthorized'));
      if (path.includes('/api/auth/register')) return Promise.resolve(mockProblemDetail(409, 'Username is already taken'));
      return Promise.reject(new Error('unexpected: ' + path));
    });

    renderRegisterPage();
    await userEvent.type(screen.getByLabelText(/username/i), 'alice');
    await userEvent.type(screen.getByLabelText(/email/i), 'alice@test.com');
    await userEvent.type(screen.getByLabelText(/^password/i), 'password-12345');
    await userEvent.type(screen.getByLabelText(/confirm/i), 'password-12345');
    await userEvent.click(screen.getByRole('button', { name: /register/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/already taken/i);
  });
});
