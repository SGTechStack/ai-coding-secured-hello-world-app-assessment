import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider, useAuth } from '../contexts/AuthContext';
import { mockProblemDetail, mockResponse, mockUser, setCsrfCookie } from './helpers';

function AuthStatus() {
  const { user, loading } = useAuth();
  if (loading) return <div>Loading</div>;
  return <div>{user ? `Logged in: ${user.username}` : 'Not logged in'}</div>;
}

function renderWithAuth(ui: React.ReactElement = <AuthStatus />) {
  return render(
    <MemoryRouter>
      <AuthProvider>{ui}</AuthProvider>
    </MemoryRouter>,
  );
}

describe('AuthProvider — session rehydration', () => {
  it('sets user when /me returns 200', async () => {
    setCsrfCookie();
    vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
      const path = typeof url === 'string' ? url : url.toString();
      if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
      if (path.includes('/api/auth/me')) return Promise.resolve(mockResponse(mockUser()));
      return Promise.reject(new Error('unexpected: ' + path));
    });

    renderWithAuth();

    await waitFor(() => expect(screen.getByText('Logged in: alice')).toBeInTheDocument());
  });

  it('leaves user null when /me returns 401', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
      const path = typeof url === 'string' ? url : url.toString();
      if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
      if (path.includes('/api/auth/me')) return Promise.resolve(mockProblemDetail(401, 'Unauthorized'));
      return Promise.reject(new Error('unexpected: ' + path));
    });

    renderWithAuth();

    await waitFor(() => expect(screen.getByText('Not logged in')).toBeInTheDocument());
  });
});

describe('AuthProvider — login / logout', () => {
  function LoginLogout() {
    const { user, login, logout } = useAuth();
    return (
      <>
        <span>{user ? `user:${user.username}` : 'none'}</span>
        <button onClick={() => { void login('alice', 'password12'); }}>Login</button>
        <button onClick={() => { void logout(); }}>Logout</button>
      </>
    );
  }

  it('calls login API and updates user', async () => {
    setCsrfCookie();
    vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
      const path = typeof url === 'string' ? url : url.toString();
      if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
      if (path.includes('/api/auth/me')) return Promise.resolve(mockResponse(mockUser()));
      if (path.includes('/api/auth/login')) return Promise.resolve(mockResponse({ username: 'alice', role: 'USER' }));
      return Promise.reject(new Error('unexpected: ' + path));
    });

    renderWithAuth(<LoginLogout />);
    await waitFor(() => expect(screen.getByText('user:alice')).toBeInTheDocument());

    // Now test a fresh logout flow
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
      const path = typeof url === 'string' ? url : url.toString();
      if (path.includes('/api/auth/logout')) return Promise.resolve(new Response(null, { status: 204 }));
      return Promise.reject(new Error('unexpected logout'));
    });

    await userEvent.click(screen.getByText('Logout'));

    expect(fetchSpy).toHaveBeenCalledWith(expect.stringContaining('/api/auth/logout'), expect.anything());
    await waitFor(() => expect(screen.getByText('none')).toBeInTheDocument());
  });
});
