import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { ANONYMOUS, type SessionUser } from '../api/types';
import { AuthContext, type AuthContextValue } from './AuthContext';
import { RequireAdmin } from './RequireAdmin';
import { RequireAuth } from './RequireAuth';

function contextFor(user: SessionUser | null): AuthContextValue {
  return {
    user,
    isLoading: user === null,
    isAuthenticated: user?.authenticated === true,
    isAdmin: user?.authenticated === true && user.role === 'ADMIN',
    login: vi.fn(),
    logout: vi.fn(),
  };
}

function renderGuarded(user: SessionUser | null, initialPath: string) {
  return render(
    <AuthContext.Provider value={contextFor(user)}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/login" element={<p>login page</p>} />
          <Route path="/" element={<p>home page</p>} />
          <Route
            path="/secret"
            element={
              <RequireAuth>
                <p>secret content</p>
              </RequireAuth>
            }
          />
          <Route
            path="/admin"
            element={
              <RequireAdmin>
                <p>admin content</p>
              </RequireAdmin>
            }
          />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

describe('RequireAuth', () => {
  it('shows a loading state while the session is unknown', () => {
    renderGuarded(null, '/secret');
    expect(screen.getByText(/checking your session/i)).toBeInTheDocument();
  });

  it('redirects anonymous visitors to /login', () => {
    renderGuarded(ANONYMOUS, '/secret');
    expect(screen.getByText('login page')).toBeInTheDocument();
    expect(screen.queryByText('secret content')).not.toBeInTheDocument();
  });

  it('renders children for an authenticated user', () => {
    renderGuarded({ authenticated: true, username: 'alice', role: 'USER' }, '/secret');
    expect(screen.getByText('secret content')).toBeInTheDocument();
  });
});

describe('RequireAdmin', () => {
  it('sends a plain USER home', () => {
    renderGuarded({ authenticated: true, username: 'alice', role: 'USER' }, '/admin');
    expect(screen.getByText('home page')).toBeInTheDocument();
  });

  it('sends anonymous visitors to /login', () => {
    renderGuarded(ANONYMOUS, '/admin');
    expect(screen.getByText('login page')).toBeInTheDocument();
  });

  it('renders children for an ADMIN', () => {
    renderGuarded({ authenticated: true, username: 'root', role: 'ADMIN' }, '/admin');
    expect(screen.getByText('admin content')).toBeInTheDocument();
  });
});
