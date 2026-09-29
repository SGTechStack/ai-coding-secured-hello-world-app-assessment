import { useState } from 'react';
import type { ReactNode } from 'react';
import { Link, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { AuthPage } from './auth/AuthPage';
import { Notice } from './components/Notice';
import { HomePage } from './pages/HomePage';
import { AdminPage } from './pages/AdminPage';

function Protected({ children, admin = false }: { children: ReactNode; admin?: boolean }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (admin && user.role !== 'ADMIN') return <Navigate to="/app" replace />;
  return children;
}

function Shell() {
  const { user, loading, startupError, signOut } = useAuth();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function logout() {
    setBusy(true);
    setError('');
    try {
      await signOut();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to sign out. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="site-shell">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="site-header">
        <Link className="brand" to={user ? '/app' : '/login'} aria-label="Hello home">
          <span className="brand-icon" aria-hidden="true">
            h.
          </span>
          <span>
            hello<span className="brand-period">.</span>
          </span>
        </Link>
        <nav aria-label="Main navigation">
          {user ? (
            <>
              <Link to="/app">Overview</Link>
              {user.role === 'ADMIN' && <Link to="/admin">Manage users</Link>}
              <button className="button subtle" disabled={busy} onClick={() => void logout()}>
                {busy ? 'Signing out…' : 'Sign out'}
              </button>
            </>
          ) : (
            <span className="header-note">A simple start. A safer space.</span>
          )}
        </nav>
      </header>
      <main id="main">
        <Notice message={error} error />
        {loading ? (
          <p className="loading" role="status">
            Getting things ready…
          </p>
        ) : startupError ? (
          <div className="startup-error">
            <Notice message={startupError} error />
            <button className="button primary" onClick={() => window.location.reload()}>
              Try again
            </button>
          </div>
        ) : (
          <Routes>
            <Route path="/login" element={<AuthPage key="login" mode="login" />} />
            <Route path="/register" element={<AuthPage key="register" mode="register" />} />
            <Route path="/forgot-password" element={<AuthPage key="forgot" mode="forgot" />} />
            <Route path="/reset-password" element={<AuthPage key="reset" mode="reset" />} />
            <Route
              path="/app"
              element={
                <Protected>
                  <HomePage />
                </Protected>
              }
            />
            <Route
              path="/admin"
              element={
                <Protected admin>
                  <AdminPage />
                </Protected>
              }
            />
            <Route path="*" element={<Navigate to={user ? '/app' : '/login'} replace />} />
          </Routes>
        )}
      </main>
      <footer className="site-footer">
        <span>A little hello goes a long way.</span>
        <span>Made for a more thoughtful web.</span>
      </footer>
    </div>
  );
}

export function App() {
  return (
    <AuthProvider>
      <Shell />
    </AuthProvider>
  );
}
