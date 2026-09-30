import { useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router';
import { useAuth } from '../../auth/useAuth';
import { Button } from '../ui/Button';

export function AppShell() {
  const { user, isAuthenticated, isAdmin, logout } = useAuth();
  const navigate = useNavigate();
  const [loggingOut, setLoggingOut] = useState(false);

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await logout();
      navigate('/login', { replace: true });
    } finally {
      setLoggingOut(false);
    }
  }

  return (
    <>
      <header className="app-header">
        <Link to="/" className="app-header__brand">
          Secured Hello World
        </Link>
        <nav className="app-header__nav" aria-label="Main">
          {isAuthenticated ? (
            <>
              <NavLink to="/">Home</NavLink>
              {isAdmin ? <NavLink to="/admin/users">Admin</NavLink> : null}
              <span className="app-header__user">
                {user?.username} · {user?.role}
              </span>
              <Button variant="secondary" size="sm" onClick={handleLogout} busy={loggingOut}>
                Log out
              </Button>
            </>
          ) : (
            <>
              <NavLink to="/login">Log in</NavLink>
              <NavLink to="/register">Register</NavLink>
            </>
          )}
        </nav>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </>
  );
}
